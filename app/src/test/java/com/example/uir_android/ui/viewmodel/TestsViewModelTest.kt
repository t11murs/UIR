package com.example.uir_android.ui.viewmodel

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AccountSession
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.EmulatorControlDraftRepository
import com.example.uir_android.domain.repository.EmulatorControlRepository
import com.example.uir_android.domain.repository.TestDraftRepository
import com.example.uir_android.domain.repository.TestRepository
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TestsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `assessment guard waits for local drafts and both server sources`() = runTest(dispatcher) {
        val testsResponse = CompletableDeferred<AppResult<List<TestSummary>>>()
        val controlsResponse = CompletableDeferred<AppResult<List<TestSummary>>>()
        val testRepository = object : TestRepository by mock(TestRepository::class.java) {
            override suspend fun loadTests(): AppResult<List<TestSummary>> = testsResponse.await()
        }
        val controlRepository = object : EmulatorControlRepository by mock(EmulatorControlRepository::class.java) {
            override suspend fun loadControls(): AppResult<List<TestSummary>> = controlsResponse.await()
        }
        val authRepository = mock(AuthRepository::class.java)
        val testDraftRepository = mock(TestDraftRepository::class.java)
        val controlDraftRepository = mock(EmulatorControlDraftRepository::class.java)
        val owner = "student@test.ru"

        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = owner))
        `when`(testDraftRepository.getActiveTestIds(owner)).thenReturn(setOf(31))
        `when`(controlDraftRepository.getActiveControlIds(owner)).thenReturn(emptySet())

        val viewModel = TestsViewModel(
            testRepository = testRepository,
            emulatorControlRepository = controlRepository,
            authRepository = authRepository,
            testDraftRepository = testDraftRepository,
            emulatorControlDraftRepository = controlDraftRepository
        )
        viewModel.onSessionChanged(isLoggedIn = true, email = owner)
        runCurrent()

        assertEquals(setOf(31), viewModel.state.value.localDraftTestIds)
        assertFalse(viewModel.state.value.assessmentGuardReady)

        testsResponse.complete(AppResult.Success(emptyList()))
        runCurrent()
        assertFalse(viewModel.state.value.assessmentGuardReady)

        controlsResponse.complete(AppResult.Success(emptyList()))
        advanceUntilIdle()
        assertTrue(viewModel.state.value.assessmentGuardReady)
    }

    @Test
    fun `emulator controls are excluded from regular tests`() {
        val regular = TestSummary(id = 1, name = "Обычный тест")
        val control = TestSummary(id = 2, name = "Контрольная с эмулятором")

        val result = filterNativeTests(
            tests = listOf(regular, control),
            controls = listOf(control)
        )

        assertEquals(listOf(regular), result)
    }

    @Test
    fun `turing control is excluded when control request failed`() {
        val regular = TestSummary(id = 1, name = "Обычный тест")
        val turing = TestSummary(id = 2, name = "Тьюринг", isTuringControl = true)

        val result = filterNativeTests(
            tests = listOf(regular, turing),
            controls = emptyList()
        )

        assertEquals(listOf(regular), result)
    }

    @Test
    fun `adaptive tests are excluded from native list`() {
        val regular = TestSummary(id = 1, name = "Обычный тест")
        val adaptive = TestSummary(id = 2, name = "Адаптивный тест", isAdaptive = true)

        val result = filterNativeTests(
            tests = listOf(regular, adaptive),
            controls = emptyList()
        )

        assertEquals(listOf(regular), result)
    }

    @Test
    fun `completed test has attempts and no current run`() {
        assertTrue(TestSummary(attempts = 1, hasCurrentRun = false).isCompleted())
        assertFalse(TestSummary(attempts = 1, hasCurrentRun = true).isCompleted())
        assertFalse(TestSummary(attempts = 0, hasCurrentRun = false).isCompleted())
    }

    @Test
    fun `available completed test always opens its result`() {
        val test = TestSummary(attempts = 1, available = true, hasCurrentRun = false)

        assertTrue(test.shouldReviewResult(reviewRequested = false))
        assertTrue(test.shouldReviewResult(reviewRequested = true))
    }

    @Test
    fun `unavailable completed test opens result`() {
        val test = TestSummary(attempts = 1, available = false, hasCurrentRun = false)

        assertTrue(test.shouldReviewResult(reviewRequested = false))
    }

    @Test
    fun `latest result marks assessment completed even when attempts counter is stale`() {
        val test = TestSummary(
            attempts = 0,
            hasCurrentRun = false,
            latestResult = TestResult(runId = 44, score = 5.0)
        )

        assertTrue(test.isCompleted())
    }

    @Test
    fun `completed results are read from matching test summaries`() {
        val firstResult = TestResult(score = 8.0, markRu = "4")
        val secondResult = TestResult(score = 6.0, markRu = "3")
        val tests = listOf(
            TestSummary(id = 1, name = "Одинаковое имя", latestResult = firstResult),
            TestSummary(id = 2, name = "Одинаковое имя", latestResult = secondResult)
        )

        assertEquals(
            mapOf(1 to firstResult, 2 to secondResult),
            completedResultsFromSummaries(tests)
        )
    }

    @Test
    fun `test without summary result does not affect another completed test`() {
        val result = TestResult(score = 9.0, markRu = "5")
        val tests = listOf(
            TestSummary(id = 7, latestResult = result),
            TestSummary(id = 8, latestResult = null)
        )

        assertEquals(mapOf(7 to result), completedResultsFromSummaries(tests))
    }

    @Test
    fun `draft storage failure does not fail test list loading`() = runTest {
        val result = loadDraftIdsSafely("student@test.ru") {
            throw IllegalStateException("Room unavailable")
        }

        assertTrue(result.ids.isEmpty())
        assertEquals("Не удалось проверить сохранённые ответы", result.errorMessage)
    }

    @Test
    fun `draft loading preserves coroutine cancellation`() = runTest {
        val job = launch {
            loadDraftIdsSafely("student@test.ru") {
                throw CancellationException("cancelled")
            }
        }

        job.join()

        assertTrue(job.isCancelled)
    }
}
