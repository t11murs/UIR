package com.example.uir_android.data.repository.impl

import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.data.local.CompletedTestReviewStore
import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.data.local.LocalAccountSession
import com.example.uir_android.data.remote.ServerCompletedTestReview
import com.example.uir_android.data.remote.ServerEmulatorAnswer
import com.example.uir_android.data.remote.ServerEmulatorResultLookup
import com.example.uir_android.data.remote.ServerEmulatorSubmitResult
import com.example.uir_android.data.remote.ServerEmulatorControlsService
import com.example.uir_android.data.remote.ServerTestAnswer
import com.example.uir_android.data.remote.ServerTestDetail
import com.example.uir_android.data.remote.ServerTestResult
import com.example.uir_android.data.remote.ServerTestsService
import com.example.uir_android.domain.model.TestAnswer
import com.example.uir_android.domain.model.EmulatorAnswer
import com.example.uir_android.domain.model.TuringTaskData
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class TestRepositoryImplTest {
    @Test
    fun `network failure is reconciled with completed result for same run`() = runTest {
        val service = mock(ServerTestsService::class.java)
        val store = mock(CompletedTestReviewStore::class.java)
        val repository = createRepository(service, store)
        val answers = listOf(TestAnswer(questionId = 7, values = listOf("A")))
        val remoteAnswers = listOf(ServerTestAnswer(questionId = 7, values = listOf("A")))
        `when`(service.submitTest(TEST_ID, RUN_ID, false, remoteAnswers)).thenReturn(networkError())
        `when`(service.loadTestResult(TEST_ID, RUN_ID)).thenReturn(
            AppResult.Success(completedReview(RUN_ID))
        )

        val result = repository.submitTest(TEST_ID, RUN_ID, false, answers)

        assertTrue(result is AppResult.Success)
        assertEquals(RUN_ID, (result as AppResult.Success).data.runId)
    }

    @Test
    fun `server failure is reconciled with completed result for same run`() = runTest {
        val service = mock(ServerTestsService::class.java)
        val store = mock(CompletedTestReviewStore::class.java)
        val repository = createRepository(service, store)
        val answers = listOf(TestAnswer(questionId = 7, values = listOf("A")))
        val remoteAnswers = listOf(ServerTestAnswer(questionId = 7, values = listOf("A")))
        `when`(service.submitTest(TEST_ID, RUN_ID, false, remoteAnswers)).thenReturn(
            AppResult.Error("Ошибка прокси", type = AppErrorType.SERVER)
        )
        `when`(service.loadTestResult(TEST_ID, RUN_ID)).thenReturn(
            AppResult.Success(completedReview(RUN_ID))
        )

        val result = repository.submitTest(TEST_ID, RUN_ID, false, answers)

        assertTrue(result is AppResult.Success)
        assertEquals(RUN_ID, (result as AppResult.Success).data.runId)
    }

    @Test
    fun `result from another run does not hide submit network failure`() = runTest {
        val service = mock(ServerTestsService::class.java)
        val store = mock(CompletedTestReviewStore::class.java)
        val repository = createRepository(service, store)
        val answers = listOf(TestAnswer(questionId = 7, values = listOf("A")))
        val remoteAnswers = listOf(ServerTestAnswer(questionId = 7, values = listOf("A")))
        val submitError = networkError()
        `when`(service.submitTest(TEST_ID, RUN_ID, false, remoteAnswers)).thenReturn(submitError)
        `when`(service.loadTestResult(TEST_ID, RUN_ID)).thenReturn(
            AppResult.Success(completedReview(RUN_ID - 1))
        )

        val result = repository.submitTest(TEST_ID, RUN_ID, false, answers)

        assertSame(submitError, result)
    }

    @Test
    fun `emulator submit network failure is reconciled by exact completed run`() = runTest {
        val service = mock(ServerEmulatorControlsService::class.java)
        val repository = EmulatorControlRepositoryImpl(service)
        val task = TuringTaskData(alphabet = emptyList(), automaton = emptyList())
        val answers = listOf(EmulatorAnswer(questionId = 7, task = task))
        val remoteAnswers = listOf(
            ServerEmulatorAnswer(
                questionId = 7,
                task = com.example.uir_android.data.remote.ServerTuringTask(
                    alphabet = emptyList(),
                    automaton = emptyList()
                )
            )
        )
        `when`(service.submit(TEST_ID, RUN_ID, false, remoteAnswers)).thenReturn(networkError())
        `when`(service.loadResult(TEST_ID, RUN_ID)).thenReturn(
            AppResult.Success(
                ServerEmulatorResultLookup(
                    completed = true,
                    result = ServerEmulatorSubmitResult(runId = RUN_ID, score = 4.0)
                )
            )
        )

        val result = repository.submit(TEST_ID, RUN_ID, false, answers)

        assertTrue(result is AppResult.Success)
        assertEquals(RUN_ID, (result as AppResult.Success).data.runId)
    }

    @Test
    fun `emulator submit malformed success response is reconciled by exact completed run`() = runTest {
        val service = mock(ServerEmulatorControlsService::class.java)
        val repository = EmulatorControlRepositoryImpl(service)
        val task = TuringTaskData(alphabet = emptyList(), automaton = emptyList())
        val answers = listOf(EmulatorAnswer(questionId = 7, task = task))
        val remoteAnswers = listOf(
            ServerEmulatorAnswer(
                questionId = 7,
                task = com.example.uir_android.data.remote.ServerTuringTask(
                    alphabet = emptyList(),
                    automaton = emptyList()
                )
            )
        )
        val ambiguousError = AppResult.Error(
            "Некорректный JSON от сервера: 200",
            type = AppErrorType.SERVER
        )
        `when`(service.submit(TEST_ID, RUN_ID, false, remoteAnswers)).thenReturn(ambiguousError)
        `when`(service.loadResult(TEST_ID, RUN_ID)).thenReturn(
            AppResult.Success(
                ServerEmulatorResultLookup(
                    completed = true,
                    result = ServerEmulatorSubmitResult(runId = RUN_ID, score = 4.0)
                )
            )
        )

        val result = repository.submit(TEST_ID, RUN_ID, false, answers)

        assertTrue(result is AppResult.Success)
        assertEquals(RUN_ID, (result as AppResult.Success).data.runId)
    }

    @Test
    fun `emulator reconciliation rejects result from another run`() = runTest {
        val service = mock(ServerEmulatorControlsService::class.java)
        val repository = EmulatorControlRepositoryImpl(service)
        val task = TuringTaskData(alphabet = emptyList(), automaton = emptyList())
        val answers = listOf(EmulatorAnswer(questionId = 7, task = task))
        val remoteAnswers = listOf(
            ServerEmulatorAnswer(
                questionId = 7,
                task = com.example.uir_android.data.remote.ServerTuringTask(
                    alphabet = emptyList(),
                    automaton = emptyList()
                )
            )
        )
        val submitError = AppResult.Error("Ошибка прокси", type = AppErrorType.SERVER)
        `when`(service.submit(TEST_ID, RUN_ID, false, remoteAnswers)).thenReturn(submitError)
        `when`(service.loadResult(TEST_ID, RUN_ID)).thenReturn(
            AppResult.Success(
                ServerEmulatorResultLookup(
                    completed = true,
                    result = ServerEmulatorSubmitResult(runId = RUN_ID - 1, score = 4.0)
                )
            )
        )

        val result = repository.submit(TEST_ID, RUN_ID, false, answers)

        assertSame(submitError, result)
    }

    private fun completedReview(runId: Int) = ServerCompletedTestReview(
        test = ServerTestDetail(id = TEST_ID, runId = runId),
        result = ServerTestResult(runId = runId, score = 4.0, total = 5.0)
    )

    private fun networkError() = AppResult.Error(
        message = "Нет соединения",
        type = AppErrorType.NETWORK
    )

    private suspend fun TestScope.createRepository(
        service: ServerTestsService,
        store: CompletedTestReviewStore
    ): TestRepositoryImpl {
        val settingsStore = mock(AppSettingsStore::class.java)
        val session = LocalAccountSession(
            email = OWNER_EMAIL,
            hasAccount = true,
            isLoggedIn = true,
            cookieHeader = "laravel_session=test"
        )
        `when`(settingsStore.authSessionFlow).thenReturn(flowOf(session))
        `when`(settingsStore.currentAuthSession()).thenReturn(session)
        return TestRepositoryImpl(service, store, settingsStore, backgroundScope)
    }

    private companion object {
        const val TEST_ID = 5
        const val RUN_ID = 101
        const val OWNER_EMAIL = "student@test.ru"
    }
}
