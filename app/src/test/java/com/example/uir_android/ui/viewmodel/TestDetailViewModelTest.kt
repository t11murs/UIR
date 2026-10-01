package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.MonotonicClock
import com.example.uir_android.domain.model.AccountSession
import com.example.uir_android.domain.model.ActiveTestDraft
import com.example.uir_android.domain.model.CompletedTestReview
import com.example.uir_android.domain.model.TestAnswer
import com.example.uir_android.domain.model.TestDetail
import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionResult
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.TestRepository
import com.example.uir_android.domain.repository.TestDraftRepository
import com.example.uir_android.ui.state.TestSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class TestDetailViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var repository: TestRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var applicationScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        repository = mock(TestRepository::class.java)
        authRepository = mock(AuthRepository::class.java)
    }

    @After
    fun tearDown() {
        applicationScope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `expired session produces error without requesting test detail`() = runTest(dispatcher) {
        `when`(authRepository.currentSession()).thenReturn(AccountSession())
        `when`(repository.loadTests()).thenReturn(AppResult.Error("Сессия истекла"))
        val draftRepository = FakeTestDraftRepository()

        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()

        assertEquals(TestSessionStatus.ERROR, viewModel.state.value.status)
        assertEquals("Сессия истекла", viewModel.state.value.errorMessage)
        verify(repository, never()).loadTest(TEST_ID)
    }

    @Test
    fun `session read failure is retryable and does not leave test loading`() = runTest(dispatcher) {
        `when`(authRepository.currentSession())
            .thenThrow(IllegalStateException("DataStore unavailable"))
            .thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(activeTestDetail()))
        val viewModel = createViewModel(FakeTestDraftRepository())

        advanceUntilIdle()

        assertEquals(TestSessionStatus.ERROR, viewModel.state.value.status)
        assertEquals(
            "Не удалось прочитать данные сессии. Повторите попытку",
            viewModel.state.value.errorMessage
        )
        verify(repository, never()).loadTest(TEST_ID)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(TestSessionStatus.ACTIVE, viewModel.state.value.status)
        assertNull(viewModel.state.value.errorMessage)
        verify(repository, times(1)).loadTest(TEST_ID)
    }

    @Test
    fun `adaptive test is rejected before creating server run`() = runTest(dispatcher) {
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(
            TestSummary(id = TEST_ID, name = "Адаптивный тест", isAdaptive = true)
        )

        val viewModel = createViewModel(FakeTestDraftRepository())
        advanceUntilIdle()

        assertEquals(TestSessionStatus.ERROR, viewModel.state.value.status)
        assertEquals(
            "Адаптивные тесты пока недоступны в Android-приложении",
            viewModel.state.value.errorMessage
        )
        verify(repository, never()).loadTest(TEST_ID)
    }

    @Test
    fun `active test with duplicate question ids is blocked`() = runTest(dispatcher) {
        val invalidDetail = activeTestDetail().copy(
            questions = listOf(
                TestQuestion(id = 11, supported = true),
                TestQuestion(id = 11, supported = true)
            )
        )
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(invalidDetail))

        val viewModel = createViewModel(FakeTestDraftRepository())
        advanceUntilIdle()
        viewModel.submit()

        assertEquals(TestSessionStatus.ERROR, viewModel.state.value.status)
        assertNull(viewModel.state.value.test)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("повторяются ID: 11"))
        verify(repository, never()).submitTest(
            eq(TEST_ID),
            eq(invalidDetail.runId),
            eq(false),
            anyList<TestAnswer>()
        )
    }

    @Test
    fun `completed result loading error is shown`() = runTest(dispatcher) {
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(
            TestSummary(id = TEST_ID, name = "Тест", attempts = 1, hasCurrentRun = false)
        )
        `when`(repository.loadTestResult(TEST_ID)).thenReturn(AppResult.Error("Детализация недоступна"))

        val viewModel = createViewModel(FakeTestDraftRepository())
        advanceUntilIdle()

        assertEquals(TestSessionStatus.ERROR, viewModel.state.value.status)
        assertEquals("Детализация недоступна", viewModel.state.value.errorMessage)
    }

    @Test
    fun `completed result with non-positive question id is blocked`() = runTest(dispatcher) {
        val invalidDetail = activeTestDetail().copy(
            questions = listOf(TestQuestion(id = 0, supported = true))
        )
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(
            TestSummary(id = TEST_ID, name = "Тест", attempts = 1, hasCurrentRun = false)
        )
        `when`(repository.loadTestResult(TEST_ID)).thenReturn(
            AppResult.Success(CompletedTestReview(invalidDetail, TestResult()))
        )

        val viewModel = createViewModel(FakeTestDraftRepository())
        advanceUntilIdle()

        assertEquals(TestSessionStatus.ERROR, viewModel.state.value.status)
        assertNull(viewModel.state.value.test)
        assertTrue(viewModel.state.value.errorMessage.orEmpty().contains("вопросов: 1"))
    }

    @Test
    fun `completed test restores questions and submitted answers for review`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        val result = TestResult(
            score = 0.0,
            total = 1.0,
            details = listOf(
                TestQuestionResult(
                    questionId = 11,
                    score = 0.0,
                    points = 1.0,
                    rightPercent = 0,
                    answers = listOf("Нет")
                )
            )
        )
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(
            TestSummary(id = TEST_ID, name = "Тест", attempts = 1, hasCurrentRun = false)
        )
        `when`(repository.loadTestResult(TEST_ID)).thenReturn(
            AppResult.Success(CompletedTestReview(detail, result))
        )

        val viewModel = createViewModel(FakeTestDraftRepository())
        advanceUntilIdle()

        assertEquals(TestSessionStatus.FINISHED, viewModel.state.value.status)
        assertEquals(listOf("Нет"), viewModel.state.value.answers[11])
        assertEquals(1, viewModel.state.value.test?.questions?.size)
    }

    @Test
    fun `second submit is ignored while first submit is running`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        `when`(
            repository.submitTest(
                eq(TEST_ID),
                eq(detail.runId),
                eq(false),
                anyList<TestAnswer>()
            )
        ).thenReturn(
            AppResult.Success(TestResult(score = 1.0, total = 1.0))
        )
        val viewModel = createViewModel(FakeTestDraftRepository())
        advanceUntilIdle()
        viewModel.setSingleAnswer(questionId = 11, value = "Да")

        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        verify(repository, times(1)).submitTest(
            eq(TEST_ID),
            eq(detail.runId),
            eq(false),
            anyList<TestAnswer>()
        )
        assertEquals(TestSessionStatus.FINISHED, viewModel.state.value.status)
    }

    @Test
    fun `draft save failure does not prevent submission`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        `when`(
            repository.submitTest(
                eq(TEST_ID),
                eq(detail.runId),
                eq(false),
                anyList<TestAnswer>()
            )
        ).thenReturn(AppResult.Success(TestResult(score = 1.0, total = 1.0)))
        val draftRepository = FakeTestDraftRepository()
        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()
        draftRepository.failNextSave(IllegalStateException("Room unavailable"))
        viewModel.setSingleAnswer(questionId = 11, value = "Да")

        viewModel.submit()
        advanceUntilIdle()

        verify(repository, times(1)).submitTest(
            eq(TEST_ID),
            eq(detail.runId),
            eq(false),
            anyList<TestAnswer>()
        )
        assertEquals(TestSessionStatus.FINISHED, viewModel.state.value.status)
    }

    @Test
    fun `draft is restored after view model recreation`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        val draft = ActiveTestDraft(
            ownerKey = "student@test.ru",
            testId = TEST_ID,
            runId = detail.runId,
            endsAt = detail.endsAt,
            currentQuestionIndex = 0,
            answers = mapOf(11 to listOf("Да")),
            markedQuestionIds = setOf(11),
            updatedAt = 1L
        )
        val draftRepository = FakeTestDraftRepository(draft)
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = draft.ownerKey))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))

        val recreated = createViewModel(draftRepository)
        advanceUntilIdle()

        assertEquals(listOf("Да"), recreated.state.value.answers[11])
        assertTrue(11 in recreated.state.value.markedQuestionIds)
    }

    @Test
    fun `answer is written through without debounce delay`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        val draftRepository = FakeTestDraftRepository()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()
        val timeBeforeAnswer = testScheduler.currentTime

        viewModel.setSingleAnswer(questionId = 11, value = "Да")
        runCurrent()

        assertEquals(timeBeforeAnswer, testScheduler.currentTime)
        assertEquals(listOf("Да"), draftRepository.current()?.answers?.get(11))
    }

    @Test
    fun `draft read failure does not prevent opening active test`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        val draftRepository = FakeTestDraftRepository().apply {
            failNextGet(IllegalStateException("Room unavailable"))
        }
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))

        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()

        assertEquals(TestSessionStatus.ACTIVE, viewModel.state.value.status)
        assertEquals(detail.id, viewModel.state.value.test?.id)
    }

    @Test
    fun `first unanswered question position is persisted`() = runTest(dispatcher) {
        val detail = activeTestDetail().copy(
            questions = listOf(
                TestQuestion(id = 11, count = 1, variants = listOf("Да", "Нет"), supported = true),
                TestQuestion(id = 12, count = 2, variants = listOf("Да", "Нет"), supported = true)
            )
        )
        val draftRepository = FakeTestDraftRepository()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))

        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()
        viewModel.setSingleAnswer(questionId = 11, value = "Да")
        viewModel.goToFirstUnanswered()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.currentQuestionIndex)
        assertEquals(1, draftRepository.current()?.currentQuestionIndex)
    }

    @Test
    fun `saving before exit persists cleared answer and runs navigation once`() = runTest(dispatcher) {
        val detail = activeTestDetail().copy(
            questions = listOf(TestQuestion(id = 11, count = 1, typeCode = 8, supported = true))
        )
        val draftRepository = FakeTestDraftRepository()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()
        viewModel.setTextAnswer(questionId = 11, value = "Старый ответ")
        advanceUntilIdle()
        viewModel.setTextAnswer(questionId = 11, value = "")
        var navigationCalls = 0

        viewModel.saveDraftAndThen { navigationCalls += 1 }
        viewModel.saveDraftAndThen { navigationCalls += 1 }
        advanceUntilIdle()

        assertEquals(listOf(""), draftRepository.current()?.answers?.get(11))
        assertEquals(1, navigationCalls)
    }

    @Test
    fun `rapid answer changes persist only latest draft before flush`() = runTest(dispatcher) {
        val detail = activeTestDetail().copy(
            questions = listOf(TestQuestion(id = 11, count = 1, typeCode = 8, supported = true))
        )
        val draftRepository = FakeTestDraftRepository()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()
        draftRepository.clearSaveHistory()

        repeat(100) { index ->
            viewModel.setTextAnswer(questionId = 11, value = "Ответ $index")
        }
        var navigationCalls = 0
        viewModel.saveDraftAndThen { navigationCalls += 1 }
        advanceUntilIdle()

        assertEquals(1, draftRepository.saveCount)
        assertEquals(listOf("Ответ 99"), draftRepository.current()?.answers?.get(11))
        assertEquals(1, navigationCalls)
    }

    @Test
    fun `draft save failure does not block exit`() = runTest(dispatcher) {
        val detail = activeTestDetail()
        val draftRepository = FakeTestDraftRepository()
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()
        draftRepository.failNextSave(IllegalStateException("Room unavailable"))
        var navigationCalls = 0

        viewModel.saveDraftAndThen { navigationCalls += 1 }
        advanceUntilIdle()

        assertEquals(1, navigationCalls)
        assertEquals(TestSessionStatus.ACTIVE, viewModel.state.value.status)
    }

    @Test
    fun `expired test submits restored draft and deletes it only after success`() = runTest(dispatcher) {
        val detail = activeTestDetail(endsAt = "2000-01-01 00:00:00")
        val draftRepository = FakeTestDraftRepository(
            ActiveTestDraft(
                ownerKey = "student@test.ru",
                testId = TEST_ID,
                runId = detail.runId,
                endsAt = detail.endsAt,
                currentQuestionIndex = 0,
                answers = mapOf(11 to listOf("Да")),
                markedQuestionIds = emptySet(),
                updatedAt = 1L
            )
        )
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        `when`(
            repository.submitTest(
                eq(TEST_ID),
                eq(detail.runId),
                eq(true),
                anyList<TestAnswer>()
            )
        ).thenReturn(
            AppResult.Success(TestResult(score = 1.0, total = 1.0))
        )

        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()

        assertEquals(TestSessionStatus.FINISHED, viewModel.state.value.status)
        assertTrue(draftRepository.isEmpty())
        verify(repository, times(1)).submitTest(
            eq(TEST_ID),
            eq(detail.runId),
            eq(true),
            anyList<TestAnswer>()
        )
    }

    @Test
    fun `expired test keeps restored draft when server does not confirm submission`() = runTest(dispatcher) {
        val detail = activeTestDetail(endsAt = "2000-01-01 00:00:00")
        val draftRepository = FakeTestDraftRepository(
            ActiveTestDraft(
                ownerKey = "student@test.ru",
                testId = TEST_ID,
                runId = detail.runId,
                endsAt = detail.endsAt,
                currentQuestionIndex = 0,
                answers = mapOf(11 to listOf("Да")),
                markedQuestionIds = emptySet(),
                updatedAt = 1L
            )
        )
        `when`(authRepository.currentSession()).thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.cachedTest(TEST_ID)).thenReturn(TestSummary(id = TEST_ID))
        `when`(repository.loadTest(TEST_ID)).thenReturn(AppResult.Success(detail))
        `when`(
            repository.submitTest(
                eq(TEST_ID),
                eq(detail.runId),
                eq(true),
                anyList<TestAnswer>()
            )
        ).thenReturn(
            AppResult.Error("Сервер временно недоступен")
        )

        val viewModel = createViewModel(draftRepository)
        advanceUntilIdle()

        assertEquals(TestSessionStatus.EXPIRED, viewModel.state.value.status)
        assertTrue(draftRepository.isNotEmpty())
        verify(repository, times(1)).submitTest(
            eq(TEST_ID),
            eq(detail.runId),
            eq(true),
            anyList<TestAnswer>()
        )
    }

    private fun createViewModel(draftRepository: TestDraftRepository) = TestDetailViewModel(
        testRepository = repository,
        authRepository = authRepository,
        testDraftRepository = draftRepository,
        monotonicClock = MonotonicClock { 10_000L },
        applicationScope = applicationScope,
        savedStateHandle = SavedStateHandle(mapOf("testId" to TEST_ID))
    )

    private fun activeTestDetail(endsAt: String = "") = TestDetail(
        id = TEST_ID,
        name = "Тест",
        runId = 101,
        endsAt = endsAt,
        questions = listOf(
            TestQuestion(
                id = 11,
                count = 1,
                variants = listOf("Да", "Нет"),
                supported = true
            )
        )
    )

    private companion object {
        const val TEST_ID = 5
    }
}

private class FakeTestDraftRepository(
    initialDraft: ActiveTestDraft? = null
) : TestDraftRepository {
    private var draft = initialDraft
    private var nextGetError: Exception? = null
    private var nextSaveError: Exception? = null
    var saveCount: Int = 0
        private set

    override suspend fun get(ownerKey: String, testId: Int): ActiveTestDraft? {
        nextGetError?.let { error ->
            nextGetError = null
            throw error
        }
        return draft
    }

    override suspend fun getActiveTestIds(ownerKey: String): Set<Int> =
        draft?.let { setOf(it.testId) }.orEmpty()

    override suspend fun save(draft: ActiveTestDraft) {
        nextSaveError?.let { error ->
            nextSaveError = null
            throw error
        }
        saveCount += 1
        this.draft = draft
    }

    override suspend fun delete(ownerKey: String, testId: Int) {
        draft = null
    }

    override suspend fun deleteAllForOwner(ownerKey: String) {
        draft = null
    }

    fun isEmpty(): Boolean = draft == null

    fun isNotEmpty(): Boolean = draft != null

    fun current(): ActiveTestDraft? = draft

    fun clearSaveHistory() {
        saveCount = 0
    }

    fun failNextSave(error: Exception) {
        nextSaveError = error
    }

    fun failNextGet(error: Exception) {
        nextGetError = error
    }
}
