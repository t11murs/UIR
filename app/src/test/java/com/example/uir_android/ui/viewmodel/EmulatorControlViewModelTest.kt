package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.MonotonicClock
import com.example.uir_android.domain.model.AccountSession
import com.example.uir_android.domain.model.EmulatorActionResult
import com.example.uir_android.domain.model.EmulatorAnswer
import com.example.uir_android.domain.model.EmulatorControl
import com.example.uir_android.domain.model.EmulatorControlDraft
import com.example.uir_android.domain.model.EmulatorQuestion
import com.example.uir_android.domain.model.EmulatorResultLookup
import com.example.uir_android.domain.model.EmulatorRunStatus
import com.example.uir_android.domain.model.EmulatorSubmitResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.model.TuringStateData
import com.example.uir_android.domain.model.TuringTaskData
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.EmulatorControlRepository
import com.example.uir_android.domain.repository.EmulatorControlDraftRepository
import com.example.uir_android.domain.usecase.CreateInitialExecutionUseCase
import com.example.uir_android.domain.usecase.ParseProgramUseCase
import com.example.uir_android.domain.usecase.RunUseCase
import com.example.uir_android.domain.usecase.StepUseCase
import com.example.uir_android.ui.state.DraftSyncStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class EmulatorControlViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()
    private lateinit var repository: EmulatorControlRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var localDraftRepository: FakeEmulatorControlDraftRepository
    private lateinit var applicationScope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mock(EmulatorControlRepository::class.java)
        authRepository = mock(AuthRepository::class.java)
        localDraftRepository = FakeEmulatorControlDraftRepository()
        applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid syntax taps send only one penalized request`() = runTest(dispatcher) {
        prepareLoadedControl()
        `when`(
            repository.loadActionReceipt(CONTROL_ID, 10, OPERATION_ID)
        ).thenReturn(AppResult.Success(null))
        `when`(
            repository.performAction(
                CONTROL_ID,
                QUESTION_ID,
                "syntax",
                OPERATION_ID,
                expectedTask()
            )
        ).thenReturn(
            AppResult.Success(
                EmulatorActionResult(
                    questionId = QUESTION_ID,
                    action = "syntax",
                    syntaxValid = true
                )
            )
        )
        val viewModel = createViewModel(withPendingSyntaxAction = true)
        advanceUntilIdle()

        viewModel.checkSyntax()
        viewModel.checkSyntax()
        advanceUntilIdle()

        verify(repository, times(1)).performAction(
            CONTROL_ID,
            QUESTION_ID,
            "syntax",
            OPERATION_ID,
            expectedTask()
        )
    }

    @Test
    fun `session read failure is retryable and does not leave control loading`() = runTest(dispatcher) {
        `when`(authRepository.currentSession())
            .thenThrow(IllegalStateException("DataStore unavailable"))
            .thenReturn(AccountSession(email = "student@test.ru"))
        `when`(repository.loadControl(CONTROL_ID)).thenReturn(
            AppResult.Success(control(runId = 10, name = "Контрольная"))
        )
        val viewModel = createViewModel()

        advanceUntilIdle()

        assertTrue(!viewModel.state.value.isLoading)
        assertEquals(null, viewModel.state.value.control)
        assertEquals(
            "Не удалось прочитать данные сессии. Повторите попытку",
            viewModel.state.value.errorMessage
        )
        verify(repository, never()).loadControl(CONTROL_ID)

        viewModel.load()
        advanceUntilIdle()

        assertTrue(!viewModel.state.value.isLoading)
        assertEquals(10, viewModel.state.value.control?.runId)
        assertEquals(null, viewModel.state.value.errorMessage)
        verify(repository, times(1)).loadControl(CONTROL_ID)
    }

    @Test
    fun `ambiguous action is reconciled by receipt without a second penalty`() = runTest(dispatcher) {
        prepareLoadedControl()
        val applied = EmulatorActionResult(
            questionId = QUESTION_ID,
            action = "syntax",
            syntaxValid = true,
            syntaxCounter = 1
        )
        `when`(
            repository.loadActionReceipt(CONTROL_ID, 10, OPERATION_ID)
        ).thenReturn(
            AppResult.Success(null),
            AppResult.Success(applied)
        )
        `when`(
            repository.performAction(
                CONTROL_ID,
                QUESTION_ID,
                "syntax",
                OPERATION_ID,
                expectedTask()
            )
        ).thenReturn(AppResult.Error("Нет соединения", type = AppErrorType.NETWORK))
        val viewModel = createViewModel(withPendingSyntaxAction = true)
        advanceUntilIdle()

        viewModel.checkSyntax()
        advanceUntilIdle()
        viewModel.checkSyntax()
        advanceUntilIdle()

        verify(repository, times(1)).performAction(
            CONTROL_ID,
            QUESTION_ID,
            "syntax",
            OPERATION_ID,
            expectedTask()
        )
        verify(repository, times(2)).loadActionReceipt(CONTROL_ID, 10, OPERATION_ID)
        assertEquals(1, viewModel.state.value.control?.questions?.single()?.syntaxCounter)
    }

    @Test
    fun `new action proceeds after previous receipt is reconciled`() = runTest(dispatcher) {
        val actionRepository = ActionReconciliationRepository(
            control = control(runId = 10, name = "Контрольная"),
            receipt = EmulatorActionResult(
                questionId = QUESTION_ID,
                action = "syntax",
                syntaxValid = true,
                syntaxCounter = 1
            ),
            actionResult = EmulatorActionResult(
                questionId = QUESTION_ID,
                action = "debug",
                syntaxValid = true,
                syntaxCounter = 1,
                debugCounter = 1,
                passed = 2,
                total = 3
            )
        )
        repository = actionRepository
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        val viewModel = createViewModel(withPendingSyntaxAction = true)
        advanceUntilIdle()

        viewModel.checkWork()
        advanceUntilIdle()

        assertEquals(1, actionRepository.receiptChecks)
        assertEquals(listOf("debug"), actionRepository.actions)
        assertEquals(1, viewModel.state.value.control?.questions?.single()?.syntaxCounter)
        assertEquals(1, viewModel.state.value.control?.questions?.single()?.debugCounter)
    }

    @Test
    fun `new draft cancels an older in flight server save`() = runTest(dispatcher) {
        val draftRepository = CancellingDraftSaveRepository(
            control = control(runId = 10, name = "Контрольная")
        )
        repository = draftRepository
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateAlphabetText("a")
        advanceTimeBy(801)
        runCurrent()
        assertTrue(draftRepository.firstSaveStarted)

        viewModel.updateAlphabetText("ab")
        advanceTimeBy(801)
        advanceUntilIdle()

        assertTrue(draftRepository.firstSaveCancelled)
        assertEquals(2, draftRepository.savedTasks.size)
        assertEquals(listOf("∂", "a", "b"), draftRepository.savedTasks.last().alphabet)
        assertEquals(DraftSyncStatus.SYNCED, viewModel.state.value.draftSyncStatus)
    }

    @Test
    fun `draft synchronization error remains visible in state`() = runTest(dispatcher) {
        prepareLoadedControl()
        `when`(repository.saveDraft(CONTROL_ID, QUESTION_ID, expectedTask())).thenReturn(
            AppResult.Error("Сервер недоступен", type = AppErrorType.NETWORK)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveCurrentDraft(showMessage = false)
        advanceUntilIdle()

        assertEquals(DraftSyncStatus.ERROR, viewModel.state.value.draftSyncStatus)
        assertEquals("Сервер недоступен", viewModel.state.value.draftSyncError)
    }

    @Test
    fun `expired control rejects editor changes`() = runTest(dispatcher) {
        val answers = listOf(EmulatorAnswer(QUESTION_ID, expectedTask()))
        `when`(repository.submit(CONTROL_ID, 10, true, answers, emptyList())).thenReturn(
            AppResult.Error("Нет соединения", type = AppErrorType.NETWORK)
        )
        prepareLoadedControl(endsAt = "2000-01-01 00:00:00")
        val viewModel = createViewModel()
        advanceUntilIdle()
        val before = viewModel.state.value

        viewModel.updateAlphabetText("abc")
        viewModel.updateCommandLeftRule(before.programRows.first().id, "S1 a")
        viewModel.addCommandRow()

        assertEquals(before.alphabetText, viewModel.state.value.alphabetText)
        assertEquals(before.programRows, viewModel.state.value.programRows)
    }

    @Test
    fun `rapid submit taps send only one completion request`() = runTest(dispatcher) {
        prepareLoadedControl()
        val answers = listOf(EmulatorAnswer(QUESTION_ID, expectedTask()))
        `when`(repository.submit(CONTROL_ID, 10, false, answers, emptyList())).thenReturn(
            AppResult.Success(EmulatorSubmitResult(runId = 10))
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.submit()
        viewModel.submit()
        advanceUntilIdle()

        verify(repository, times(1)).submit(CONTROL_ID, 10, false, answers, emptyList())
    }

    @Test
    fun `expired control is submitted as timed out attempt`() = runTest(dispatcher) {
        val answers = listOf(EmulatorAnswer(QUESTION_ID, expectedTask()))
        `when`(repository.submit(CONTROL_ID, 10, true, answers, emptyList())).thenReturn(
            AppResult.Success(EmulatorSubmitResult(runId = 10))
        )
        prepareLoadedControl(endsAt = "2000-01-01 00:00:00")

        createViewModel()
        advanceUntilIdle()

        verify(repository, times(1)).submit(CONTROL_ID, 10, true, answers, emptyList())
    }

    @Test
    fun `timed out invalid algorithm cannot fall back to stale server draft`() = runTest(dispatcher) {
        prepareLoadedControl()
        `when`(
            repository.submit(
                CONTROL_ID,
                10,
                true,
                emptyList(),
                listOf(QUESTION_ID)
            )
        ).thenReturn(AppResult.Success(EmulatorSubmitResult(runId = 10)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateCommandLeftRule(viewModel.state.value.programRows.first().id, "invalid")
        viewModel.retryTimedOutSubmission()
        advanceUntilIdle()

        verify(repository, times(1)).submit(
            CONTROL_ID,
            10,
            true,
            emptyList(),
            listOf(QUESTION_ID)
        )
    }

    @Test
    fun `back navigation waits for latest local draft`() = runTest(dispatcher) {
        prepareLoadedControl()
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.updateInputTape("101")
        var navigated = false

        viewModel.saveLocalDraftAndThen { navigated = true }
        advanceUntilIdle()

        assertTrue(navigated)
        assertEquals(
            "101",
            localDraftRepository.draft?.questions?.get(QUESTION_ID)?.inputTape
        )
    }

    @Test
    fun `completed server run is restored before a new control is opened`() = runTest(dispatcher) {
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        localDraftRepository.save(
            EmulatorControlDraft(
                ownerKey = "student@test.ru",
                controlId = CONTROL_ID,
                runId = 10,
                endsAt = "2026-09-26 12:00:00",
                currentQuestionIndex = 0,
                questions = emptyMap(),
                updatedAt = 1L
            )
        )
        `when`(repository.loadResult(CONTROL_ID, 10)).thenReturn(
            AppResult.Success(
                EmulatorResultLookup(
                    name = "Контрольная",
                    completed = true,
                    result = EmulatorSubmitResult(runId = 10, score = 5.0)
                )
            )
        )
        `when`(repository.loadControl(CONTROL_ID, 10)).thenReturn(
            AppResult.Error("Контрольная уже завершена", type = AppErrorType.DATA)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(10, viewModel.state.value.result?.runId)
        assertEquals("Контрольная", viewModel.state.value.control?.name)
        assertTrue(localDraftRepository.draft == null)
        verify(repository, never()).loadControl(CONTROL_ID)
    }

    @Test
    fun `active stored run resumes without requesting a result`() = runTest(dispatcher) {
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        localDraftRepository.save(
            EmulatorControlDraft(
                ownerKey = "student@test.ru",
                controlId = CONTROL_ID,
                runId = 10,
                endsAt = "2026-09-26 12:00:00",
                currentQuestionIndex = 0,
                questions = emptyMap(),
                updatedAt = 1L
            )
        )
        `when`(repository.loadControl(CONTROL_ID, 10)).thenReturn(
            AppResult.Success(control(runId = 10, name = "Контрольная"))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(10, viewModel.state.value.control?.runId)
        verify(repository, times(1)).loadControl(CONTROL_ID, 10)
        verify(repository, never()).loadResult(CONTROL_ID, 10)
    }

    @Test
    fun `completed run is reconciled when stored attempt cannot be resumed`() = runTest(dispatcher) {
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        localDraftRepository.save(
            EmulatorControlDraft(
                ownerKey = "student@test.ru",
                controlId = CONTROL_ID,
                runId = 10,
                endsAt = "2026-09-26 12:00:00",
                currentQuestionIndex = 0,
                questions = emptyMap(),
                updatedAt = 1L
            )
        )
        `when`(repository.loadControl(CONTROL_ID, 10)).thenReturn(
            AppResult.Error("Контрольная уже завершена", type = AppErrorType.DATA)
        )
        `when`(repository.loadResult(CONTROL_ID, 10)).thenReturn(
            AppResult.Success(
                EmulatorResultLookup(
                    name = "Контрольная",
                    completed = true,
                    result = EmulatorSubmitResult(runId = 10, score = 5.0)
                )
            )
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(10, viewModel.state.value.result?.runId)
        verify(repository, times(1)).loadControl(CONTROL_ID, 10)
        verify(repository, times(1)).loadResult(CONTROL_ID, 10)
    }

    @Test
    fun `missing stored run is discarded and current run is loaded`() = runTest(dispatcher) {
        prepareLoadedControl(runId = 11)
        localDraftRepository.save(
            EmulatorControlDraft(
                ownerKey = "student@test.ru",
                controlId = CONTROL_ID,
                runId = 10,
                endsAt = "2026-09-26 12:00:00",
                currentQuestionIndex = 0,
                questions = emptyMap(),
                updatedAt = 1L
            )
        )
        `when`(repository.loadResult(CONTROL_ID, 10)).thenReturn(
            AppResult.Success(
                EmulatorResultLookup(
                    completed = false,
                    status = EmulatorRunStatus.NOT_FOUND,
                    activeRunId = 11
                )
            )
        )
        `when`(repository.loadControl(CONTROL_ID, 10)).thenReturn(
            AppResult.Error("Активная попытка не найдена", type = AppErrorType.DATA)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(11, viewModel.state.value.control?.runId)
        verify(repository, times(1)).loadControl(CONTROL_ID)
        verify(repository, times(1)).loadControl(CONTROL_ID, 10)
    }

    @Test
    fun `late response from cancelled load cannot overwrite newer control`() = runTest(dispatcher) {
        val firstResponse = CompletableDeferred<AppResult<EmulatorControl>>()
        val secondResponse = CompletableDeferred<AppResult<EmulatorControl>>()
        repository = SequencedControlLoadRepository(firstResponse, secondResponse)
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        val viewModel = createViewModel()
        runCurrent()

        viewModel.load()
        runCurrent()
        secondResponse.complete(AppResult.Success(control(runId = 20, name = "Новая КР")))
        runCurrent()
        firstResponse.complete(AppResult.Success(control(runId = 10, name = "Старая КР")))
        advanceUntilIdle()

        assertEquals(20, viewModel.state.value.control?.runId)
        assertEquals("Новая КР", viewModel.state.value.control?.name)
    }

    @Test
    fun `review mode loads completed run without opening a new attempt`() = runTest(dispatcher) {
        val completed = EmulatorSubmitResult(
            runId = 55,
            score = 8.0,
            total = 10.0,
            markRu = "4"
        )
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        `when`(repository.loadResult(CONTROL_ID, 55)).thenReturn(
            AppResult.Success(
                EmulatorResultLookup(
                    name = "Завершённая КР",
                    completed = true,
                    result = completed,
                    status = EmulatorRunStatus.COMPLETED
                )
            )
        )

        val viewModel = createViewModel(review = true, resultRunId = 55)
        advanceUntilIdle()

        assertEquals(completed, viewModel.state.value.result)
        assertEquals("Завершённая КР", viewModel.state.value.control?.name)
        verify(repository, times(1)).loadResult(CONTROL_ID, 55)
        verify(repository, never()).loadControl(CONTROL_ID)
    }

    private suspend fun prepareLoadedControl(endsAt: String = "", runId: Int = 10) {
        `when`(authRepository.currentSession()).thenReturn(
            AccountSession(email = "student@test.ru")
        )
        `when`(repository.loadControl(CONTROL_ID)).thenReturn(
            AppResult.Success(
                EmulatorControl(
                    id = CONTROL_ID,
                    name = "Контрольная",
                    runId = runId,
                    endsAt = endsAt,
                    questions = listOf(
                        EmulatorQuestion(
                            id = QUESTION_ID,
                            count = 1,
                            task = TuringTaskData(emptyList(), emptyList())
                        )
                    )
                )
            )
        )
    }

    private fun createViewModel(
        withPendingSyntaxAction: Boolean = false,
        review: Boolean = false,
        resultRunId: Int = 0
    ): EmulatorControlViewModel {
        val step = StepUseCase()
        val appDispatchers = AppDispatchers(dispatcher, dispatcher, dispatcher)
        val initialState = mutableMapOf<String, Any>(
            "controlId" to CONTROL_ID,
            "review" to review,
            "runId" to resultRunId
        )
        if (withPendingSyntaxAction) {
            initialState["emulator_pending_action_key"] =
                "10:$QUESTION_ID:syntax:${expectedTask().hashCode()}"
            initialState["emulator_pending_action_id"] = OPERATION_ID
        }
        return EmulatorControlViewModel(
            repository = repository,
            authRepository = authRepository,
            localDraftRepository = localDraftRepository,
            parseProgramUseCase = ParseProgramUseCase(appDispatchers),
            stepUseCase = step,
            runUseCase = RunUseCase(
                stepUseCase = step,
                dispatchers = appDispatchers
            ),
            createInitialExecutionUseCase = CreateInitialExecutionUseCase(),
            monotonicClock = MonotonicClock { 10_000L },
            dispatchers = appDispatchers,
            applicationScope = applicationScope,
            savedStateHandle = SavedStateHandle(initialState)
        )
    }

    private fun expectedTask() = TuringTaskData(
        alphabet = listOf("∂"),
        automaton = listOf(
            TuringStateData(
                state = "S0",
                expressions = linkedMapOf("∂" to "", "λ" to "")
            )
        )
    )

    private fun control(runId: Int, name: String) = EmulatorControl(
        id = CONTROL_ID,
        name = name,
        runId = runId,
        questions = listOf(
            EmulatorQuestion(
                id = QUESTION_ID,
                count = 1,
                task = TuringTaskData(emptyList(), emptyList())
            )
        )
    )

    private companion object {
        const val CONTROL_ID = 7
        const val QUESTION_ID = 17
        const val OPERATION_ID = "test-operation-0001"
    }
}

private class CancellingDraftSaveRepository(
    private val control: EmulatorControl
) : EmulatorControlRepository {
    var firstSaveStarted = false
        private set
    var firstSaveCancelled = false
        private set
    val savedTasks = mutableListOf<TuringTaskData>()

    override suspend fun loadControls(): AppResult<List<TestSummary>> =
        AppResult.Success(emptyList())

    override suspend fun loadControl(controlId: Int, runId: Int?): AppResult<EmulatorControl> =
        AppResult.Success(control)

    override suspend fun loadResult(controlId: Int, runId: Int) =
        AppResult.Error("Результат недоступен")

    override suspend fun saveDraft(
        controlId: Int,
        questionId: Int,
        task: TuringTaskData
    ): AppResult<Unit> {
        savedTasks += task
        if (savedTasks.size == 1) {
            firstSaveStarted = true
            try {
                awaitCancellation()
            } catch (error: CancellationException) {
                firstSaveCancelled = true
                throw error
            }
        }
        return AppResult.Success(Unit)
    }

    override suspend fun performAction(
        controlId: Int,
        questionId: Int,
        action: String,
        operationId: String,
        task: TuringTaskData
    ) = AppResult.Error("Действие недоступно")

    override suspend fun loadActionReceipt(
        controlId: Int,
        runId: Int,
        operationId: String
    ): AppResult<EmulatorActionResult?> = AppResult.Success(null)

    override suspend fun submit(
        controlId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<EmulatorAnswer>,
        invalidQuestionIds: List<Int>
    ) = AppResult.Error("Отправка недоступна")
}

private class ActionReconciliationRepository(
    private val control: EmulatorControl,
    private val receipt: EmulatorActionResult,
    private val actionResult: EmulatorActionResult
) : EmulatorControlRepository {
    var receiptChecks = 0
        private set
    val actions = mutableListOf<String>()

    override suspend fun loadControls(): AppResult<List<TestSummary>> =
        AppResult.Success(emptyList())

    override suspend fun loadControl(controlId: Int, runId: Int?): AppResult<EmulatorControl> =
        AppResult.Success(control)

    override suspend fun loadResult(controlId: Int, runId: Int) =
        AppResult.Error("Результат недоступен")

    override suspend fun saveDraft(
        controlId: Int,
        questionId: Int,
        task: TuringTaskData
    ) = AppResult.Success(Unit)

    override suspend fun performAction(
        controlId: Int,
        questionId: Int,
        action: String,
        operationId: String,
        task: TuringTaskData
    ): AppResult<EmulatorActionResult> {
        actions += action
        return AppResult.Success(actionResult)
    }

    override suspend fun loadActionReceipt(
        controlId: Int,
        runId: Int,
        operationId: String
    ): AppResult<EmulatorActionResult?> {
        receiptChecks++
        return AppResult.Success(receipt)
    }

    override suspend fun submit(
        controlId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<EmulatorAnswer>,
        invalidQuestionIds: List<Int>
    ) = AppResult.Error("Отправка недоступна")
}

private class SequencedControlLoadRepository(
    private val firstResponse: CompletableDeferred<AppResult<EmulatorControl>>,
    private val secondResponse: CompletableDeferred<AppResult<EmulatorControl>>
) : EmulatorControlRepository {
    private var loadCount = 0

    override suspend fun loadControls(): AppResult<List<TestSummary>> =
        AppResult.Success(emptyList())

    override suspend fun loadControl(controlId: Int, runId: Int?): AppResult<EmulatorControl> {
        val response = if (loadCount++ == 0) firstResponse else secondResponse
        return try {
            response.await()
        } catch (_: CancellationException) {
            withContext(NonCancellable) { response.await() }
        }
    }

    override suspend fun loadResult(controlId: Int, runId: Int) =
        AppResult.Error("Результат недоступен")

    override suspend fun saveDraft(
        controlId: Int,
        questionId: Int,
        task: TuringTaskData
    ) = AppResult.Success(Unit)

    override suspend fun performAction(
        controlId: Int,
        questionId: Int,
        action: String,
        operationId: String,
        task: TuringTaskData
    ) = AppResult.Error("Действие недоступно")

    override suspend fun loadActionReceipt(
        controlId: Int,
        runId: Int,
        operationId: String
    ): AppResult<EmulatorActionResult?> = AppResult.Success(null)

    override suspend fun submit(
        controlId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<EmulatorAnswer>,
        invalidQuestionIds: List<Int>
    ) = AppResult.Error("Отправка недоступна")
}

private class FakeEmulatorControlDraftRepository : EmulatorControlDraftRepository {
    var draft: EmulatorControlDraft? = null
        private set

    override suspend fun get(ownerKey: String, controlId: Int): EmulatorControlDraft? = draft

    override suspend fun getActiveControlIds(ownerKey: String): Set<Int> =
        draft?.let { setOf(it.controlId) }.orEmpty()

    override suspend fun save(draft: EmulatorControlDraft) {
        this.draft = draft
    }

    override suspend fun delete(ownerKey: String, controlId: Int) {
        draft = null
    }
}
