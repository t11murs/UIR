package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.MonotonicClock
import com.example.uir_android.domain.turing.BLANK_SYMBOL
import com.example.uir_android.domain.turing.PARTIAL_SYMBOL
import com.example.uir_android.domain.turing.parseCompactSymbolSequence
import com.example.uir_android.domain.turing.parseSingleSymbolToken
import com.example.uir_android.domain.model.EmulatorCommandDraft
import com.example.uir_android.domain.model.EmulatorControl
import com.example.uir_android.domain.model.EmulatorControlDraft
import com.example.uir_android.domain.model.EmulatorQuestionDraft
import com.example.uir_android.domain.model.EmulatorRunStatus
import com.example.uir_android.domain.model.TmExecutionState
import com.example.uir_android.domain.model.EmulatorActionResult
import com.example.uir_android.domain.model.EmulatorAnswer
import com.example.uir_android.domain.model.TuringStateData
import com.example.uir_android.domain.model.TuringTaskData
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.EmulatorControlRepository
import com.example.uir_android.domain.repository.EmulatorControlDraftRepository
import com.example.uir_android.domain.usecase.CreateInitialExecutionUseCase
import com.example.uir_android.domain.usecase.ParseProgramUseCase
import com.example.uir_android.domain.usecase.RunUseCase
import com.example.uir_android.domain.usecase.StepUseCase
import com.example.uir_android.ui.state.CommandSymbolFieldTarget
import com.example.uir_android.ui.state.DraftSyncStatus
import com.example.uir_android.ui.state.EmulatorControlUiState
import com.example.uir_android.ui.state.EmulatorControlsUiState
import com.example.uir_android.ui.state.FocusedCommandSymbolField
import com.example.uir_android.ui.state.ProgramCommandRowUiState
import com.example.uir_android.ui.editor.TuringEditor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@HiltViewModel
class EmulatorControlsViewModel @Inject constructor(
    private val repository: EmulatorControlRepository
) : EventViewModel() {
    private val _state = MutableStateFlow(EmulatorControlsUiState(isLoading = true))
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L

    fun refresh() {
        val generation = ++refreshGeneration
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.loadControls()
            if (generation != refreshGeneration) return@launch
            when (result) {
                is AppResult.Error -> {
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                    emitSnackbar(result.message)
                }
                is AppResult.Success -> {
                    _state.update {
                        it.copy(isLoading = false, controls = result.data, errorMessage = null)
                    }
                }
            }
        }
    }
}

@HiltViewModel
class EmulatorControlViewModel @Inject constructor(
    private val repository: EmulatorControlRepository,
    private val authRepository: AuthRepository,
    private val localDraftRepository: EmulatorControlDraftRepository,
    private val parseProgramUseCase: ParseProgramUseCase,
    private val stepUseCase: StepUseCase,
    private val runUseCase: RunUseCase,
    private val createInitialExecutionUseCase: CreateInitialExecutionUseCase,
    private val monotonicClock: MonotonicClock,
    private val dispatchers: AppDispatchers,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val savedStateHandle: SavedStateHandle
) : EventViewModel() {
    private enum class StoredRunRecovery {
        CONTINUE_STORED,
        LOAD_CURRENT,
        STOP
    }

    private data class EditorDraft(
        val alphabetText: String,
        val inputTape: String,
        val rows: List<ProgramCommandRowUiState>
    )

    private data class ServerDraftSaveRequest(
        val loadGeneration: Long,
        val revision: Long,
        val runId: Int,
        val questionId: Int,
        val alphabetText: String,
        val rows: List<ProgramCommandRowUiState>,
        val immediate: Boolean,
        val showMessage: Boolean,
        val reportErrors: Boolean
    )

    private data class SubmissionBuildResult(
        val answers: List<EmulatorAnswer>,
        val invalidQuestionIds: List<Int>,
        val firstInvalidQuestionIndex: Int?
    )

    private val controlId: Int = checkNotNull(savedStateHandle["controlId"])
    private val reviewRequested: Boolean = savedStateHandle["review"] ?: false
    private val requestedResultRunId: Int = savedStateHandle["runId"] ?: 0
    private val _state = MutableStateFlow(EmulatorControlUiState())
    val state = _state.asStateFlow()
    private val drafts = linkedMapOf<Int, EditorDraft>()
    private var executionState: TmExecutionState = createInitialExecutionUseCase("∂")
    private var nextRowId = 1L
    private var localDraftJob: Job? = null
    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var runJob: Job? = null
    private var timerJob: Job? = null
    private var draftOwnerKey: String = ""
    private val localDraftMutex = Mutex()
    private var draftClosed = false
    private var timeoutSubmissionStarted = false
    private var exitSaveInProgress = false
    private var draftFlushedForExit = false
    private val serverDraftSaveRequest = MutableStateFlow<ServerDraftSaveRequest?>(null)
    private var serverDraftRevision = 0L

    init {
        observeServerDraftSaves()
        load()
    }

    fun load() {
        val generation = ++loadGeneration
        loadJob?.cancel()
        localDraftJob?.cancel()
        timerJob?.cancel()
        serverDraftRevision++
        serverDraftSaveRequest.value = null
        loadJob = viewModelScope.launch {
            draftOwnerKey = ""
            _state.update { it.copy(isLoading = true, errorMessage = null, result = null) }
            val ownerKey = try {
                authRepository.currentSession().email.trim().lowercase()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (!isCurrentLoad(generation)) return@launch
                val message = "Не удалось прочитать данные сессии. Повторите попытку"
                _state.update {
                    it.copy(
                        isLoading = false,
                        control = null,
                        errorMessage = message
                    )
                }
                emitSnackbar(message)
                return@launch
            }
            if (!isCurrentLoad(generation)) return@launch
            draftOwnerKey = ownerKey
            if (reviewRequested) {
                loadCompletedResult(generation)
                return@launch
            }
            var storedDraft = try {
                if (ownerKey.isBlank()) {
                    null
                } else {
                    localDraftRepository.get(ownerKey, controlId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val message = "Не удалось прочитать локальный черновик контрольной"
                _state.update { it.copy(isLoading = false, errorMessage = message) }
                emitSnackbar(message)
                return@launch
            }
            if (!isCurrentLoad(generation)) return@launch
            var controlResult = repository.loadControl(controlId, storedDraft?.runId)
            if (!isCurrentLoad(generation)) return@launch
            if (controlResult is AppResult.Error && storedDraft != null) {
                when (recoverStoredRun(storedDraft, ownerKey, generation)) {
                    StoredRunRecovery.STOP -> return@launch
                    StoredRunRecovery.LOAD_CURRENT -> {
                        storedDraft = null
                        controlResult = repository.loadControl(controlId)
                        if (!isCurrentLoad(generation)) return@launch
                    }
                    StoredRunRecovery.CONTINUE_STORED -> Unit
                }
            }
            when (val result = controlResult) {
                is AppResult.Error -> {
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                    emitSnackbar(result.message)
                }
                is AppResult.Success -> {
                    draftClosed = false
                    timeoutSubmissionStarted = false
                    val compatibleDraft = storedDraft?.takeIf {
                        it.matches(result.data.runId, result.data.endsAt)
                    }
                    if (storedDraft != null && compatibleDraft == null) {
                        if (!isCurrentLoad(generation)) return@launch
                        localDraftRepository.delete(ownerKey, controlId)
                        if (!isCurrentLoad(generation)) return@launch
                    }
                    drafts.clear()
                    nextRowId = 1L
                    result.data.questions.forEach { question ->
                        val localQuestion = compatibleDraft?.questions?.get(question.id)
                        drafts[question.id] = if (localQuestion == null) {
                            EditorDraft(
                                alphabetText = question.task.alphabet.joinToString(""),
                                inputTape = "∂",
                                rows = taskToRows(question.task)
                            )
                        } else {
                            EditorDraft(
                                alphabetText = localQuestion.alphabetText,
                                inputTape = localQuestion.inputTape,
                                rows = localQuestion.commands.map { command ->
                                    newRow(command.leftRuleText, command.rightRuleText)
                                }.ifEmpty { listOf(newRow()) }
                            )
                        }
                    }
                    val restoredIndex = compatibleDraft?.currentQuestionIndex
                        ?.coerceIn(0, (result.data.questions.size - 1).coerceAtLeast(0))
                        ?: 0
                    val initialRemainingSeconds = result.data.remainingSeconds?.let { remaining ->
                        boundedAttemptRemainingSeconds(
                            reportedRemainingSeconds = remaining,
                            configuredMinutes = result.data.timeMinutes
                        )
                    }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            control = result.data,
                            currentQuestionIndex = restoredIndex,
                            remainingSeconds = initialRemainingSeconds ?: 0L,
                            errorMessage = null,
                            draftSyncStatus = DraftSyncStatus.IDLE,
                            draftSyncError = null
                        )
                    }
                    clearPendingActionIfRunChanged(result.data.runId)
                    applyQuestion(restoredIndex)
                    scheduleLocalDraftSave()
                    startTimer(initialRemainingSeconds, result.data.endsAt)
                }
            }
        }
    }

    private suspend fun loadCompletedResult(generation: Long) {
        if (requestedResultRunId <= 0) {
            _state.update {
                it.copy(isLoading = false, errorMessage = "Не указан номер завершённой попытки")
            }
            return
        }
        when (val lookup = repository.loadResult(controlId, requestedResultRunId)) {
            is AppResult.Error -> {
                if (!isCurrentLoad(generation)) return
                _state.update { it.copy(isLoading = false, errorMessage = lookup.message) }
                emitSnackbar(lookup.message)
            }
            is AppResult.Success -> {
                if (!isCurrentLoad(generation)) return
                val completedResult = lookup.data.result
                if (lookup.data.status != EmulatorRunStatus.COMPLETED || completedResult == null) {
                    val message = "Завершённый результат контрольной не найден"
                    _state.update { it.copy(isLoading = false, errorMessage = message) }
                    emitSnackbar(message)
                    return
                }
                draftClosed = true
                timerJob?.cancel()
                _state.update {
                    it.copy(
                        isLoading = false,
                        control = EmulatorControl(
                            id = controlId,
                            name = lookup.data.name.ifBlank { "Контрольная с эмулятором" },
                            attempts = 1,
                            available = false,
                            runId = completedResult.runId
                        ),
                        result = completedResult,
                        remainingSeconds = 0L,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private suspend fun recoverStoredRun(
        storedDraft: EmulatorControlDraft,
        ownerKey: String,
        generation: Long
    ): StoredRunRecovery {
        val lookup = repository.loadResult(controlId, storedDraft.runId)
        if (!isCurrentLoad(generation)) return StoredRunRecovery.STOP
        return when (lookup) {
            is AppResult.Error -> {
                _state.update { it.copy(isLoading = false, errorMessage = lookup.message) }
                emitSnackbar(lookup.message)
                StoredRunRecovery.STOP
            }
            is AppResult.Success -> {
                when (lookup.data.status) {
                    EmulatorRunStatus.ACTIVE -> {
                        val activeRunId = lookup.data.activeRunId
                        if (activeRunId == null || activeRunId == storedDraft.runId) {
                            StoredRunRecovery.CONTINUE_STORED
                        } else {
                            if (!deleteReconciledDraft(ownerKey, generation)) {
                                return StoredRunRecovery.STOP
                            }
                            StoredRunRecovery.LOAD_CURRENT
                        }
                    }
                    EmulatorRunStatus.NOT_FOUND -> {
                        if (!deleteReconciledDraft(ownerKey, generation)) {
                            return StoredRunRecovery.STOP
                        }
                        StoredRunRecovery.LOAD_CURRENT
                    }
                    EmulatorRunStatus.COMPLETED -> {
                        val completedResult = lookup.data.result
                        if (completedResult == null) {
                            val message = "Сервер вернул неполный результат контрольной"
                            _state.update { it.copy(isLoading = false, errorMessage = message) }
                            emitSnackbar(message)
                            return StoredRunRecovery.STOP
                        }
                        timerJob?.cancel()
                        draftClosed = true
                        clearPendingAction()
                        if (!deleteReconciledDraft(ownerKey, generation)) {
                            return StoredRunRecovery.STOP
                        }
                        if (!isCurrentLoad(generation)) return StoredRunRecovery.STOP
                        _state.update {
                            it.copy(
                                isLoading = false,
                                control = EmulatorControl(
                                    id = controlId,
                                    name = lookup.data.name.ifBlank { "Контрольная с эмулятором" },
                                    runId = storedDraft.runId
                                ),
                                result = completedResult,
                                errorMessage = null
                            )
                        }
                        StoredRunRecovery.STOP
                    }
                }
            }
        }
    }

    private suspend fun deleteReconciledDraft(ownerKey: String, generation: Long): Boolean {
        if (!isCurrentLoad(generation)) return false
        try {
            localDraftRepository.delete(ownerKey, controlId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emitSnackbar("Не удалось удалить устаревший локальный черновик")
        }
        return isCurrentLoad(generation)
    }

    private fun isCurrentLoad(generation: Long): Boolean = generation == loadGeneration

    fun selectQuestion(index: Int) {
        val size = _state.value.control?.questions.orEmpty().size
        if (index !in 0 until size || index == _state.value.currentQuestionIndex) return
        if (canEditAttempt()) {
            persistCurrentDraft()
            scheduleCurrentServerDraftSave(
                immediate = true,
                showMessage = false,
                reportErrors = false
            )
        }
        applyQuestion(index)
        scheduleLocalDraftSave()
    }

    fun previousQuestion() = selectQuestion(_state.value.currentQuestionIndex - 1)

    fun nextQuestion() = selectQuestion(_state.value.currentQuestionIndex + 1)

    fun updateInputTape(value: String) {
        if (!canEditAttempt()) return
        cancelLocalRun()
        _state.update {
            it.copy(
                inputTape = TuringEditor.displaySymbols(value),
                isInputTapeFocused = true,
                focusedSymbolField = null,
                errorMessage = null
            )
        }
        persistCurrentDraft()
    }

    fun updateAlphabetText(value: String) {
        if (!canEditAttempt()) return
        _state.update {
            it.copy(
                alphabetText = TuringEditor.displaySymbols(value).filterNot(Char::isWhitespace),
                errorMessage = null
            )
        }
        draftChanged()
    }

    fun selectInputTapeField() {
        _state.update { it.copy(isInputTapeFocused = true, focusedSymbolField = null) }
    }

    fun selectSymbolField(rowId: Long, target: CommandSymbolFieldTarget) {
        _state.update {
            it.copy(
                focusedSymbolField = FocusedCommandSymbolField(rowId, target),
                isInputTapeFocused = false
            )
        }
    }

    fun clearSymbolFieldSelection() {
        _state.update { it.copy(focusedSymbolField = null, isInputTapeFocused = false) }
    }

    fun insertSpecialToken(token: String) {
        if (!canEditAttempt()) return
        val symbol = TuringEditor.displaySymbols(token)
        val snapshot = _state.value
        if (snapshot.isInputTapeFocused) {
            updateInputTape(snapshot.inputTape + symbol)
            return
        }
        val focus = snapshot.focusedSymbolField ?: return
        updateRow(focus.rowId) { row ->
            when (focus.target) {
                CommandSymbolFieldTarget.READ -> row.copy(leftRuleText = row.leftRuleText + symbol)
                CommandSymbolFieldTarget.WRITE -> row.copy(rightRuleText = row.rightRuleText + symbol)
            }
        }
    }

    fun addCommandRow() {
        if (!canEditAttempt()) return
        _state.update { it.copy(programRows = it.programRows + newRow()) }
        draftChanged()
    }

    fun removeCommandRow(rowId: Long) {
        if (!canEditAttempt()) return
        _state.update {
            it.copy(
                programRows = it.programRows.filterNot { row -> row.id == rowId }
                    .ifEmpty { listOf(newRow()) },
                focusedSymbolField = it.focusedSymbolField?.takeUnless { field -> field.rowId == rowId }
            )
        }
        draftChanged()
    }

    fun moveCommandRowUp(rowId: Long) {
        if (canEditAttempt()) moveRow(rowId, -1)
    }

    fun moveCommandRowDown(rowId: Long) {
        if (canEditAttempt()) moveRow(rowId, 1)
    }

    fun updateCommandLeftRule(rowId: Long, value: String) {
        if (!canEditAttempt()) return
        updateRow(rowId) { it.copy(leftRuleText = TuringEditor.displaySymbols(value)) }
    }

    fun updateCommandRightRule(rowId: Long, value: String) {
        if (!canEditAttempt()) return
        updateRow(rowId) { it.copy(rightRuleText = TuringEditor.displaySymbols(value)) }
    }

    fun clearProgram() {
        if (!canEditAttempt()) return
        nextRowId = 1L
        _state.update {
            it.copy(
                programRows = listOf(newRow()),
                errorMessage = null,
                statusMessage = null
            )
        }
        reset()
        draftChanged()
    }

    fun saveCurrentDraft(showMessage: Boolean = true) {
        if (!canEditAttempt()) return
        persistCurrentDraft()
        scheduleCurrentServerDraftSave(
            immediate = true,
            showMessage = showMessage,
            reportErrors = showMessage
        )
    }

    fun checkSyntax() {
        performServerAction("syntax")
    }

    fun checkWork() {
        performServerAction("debug")
    }

    fun placeOnTape() {
        if (!canEditAttempt()) return
        val input = normalizedInput() ?: return
        executionState = createInitialExecutionUseCase(input)
        _state.update {
            it.copy(
                inputTape = input,
                windowOffset = 0,
                errorMessage = null,
                statusMessage = "Лента инициализирована"
            )
        }
        pushExecution()
        persistCurrentDraft()
    }

    fun clearTape() {
        if (!canEditAttempt()) return
        executionState = createInitialExecutionUseCase("")
        _state.update {
            it.copy(
                inputTape = "",
                windowOffset = 0,
                errorMessage = null,
                statusMessage = null
            )
        }
        pushExecution()
        persistCurrentDraft()
    }

    fun reset() {
        if (!canEditAttempt()) return
        runJob?.cancel()
        val input = normalizedInput(reportErrors = false) ?: "∂"
        executionState = createInitialExecutionUseCase(input)
        _state.update { it.copy(isRunning = false, errorMessage = null, statusMessage = null) }
        pushExecution()
    }

    fun step() {
        if (!canEditAttempt()) return
        runJob?.cancel()
        runJob = viewModelScope.launch {
            val program = parsedProgram() ?: return@launch
            if (executionState.steps == 0) {
                val input = normalizedInput() ?: return@launch
                executionState = createInitialExecutionUseCase(input)
            }
            executionState = stepUseCase(program, executionState)
            if (executionState.headPos < 0) {
                executionState = executionState.copy(
                    halted = true,
                    errorMessage = "Головка вышла за левую границу ленты"
                )
            }
            pushExecution()
        }
    }

    fun run() {
        if (!canEditAttempt()) return
        performServerAction("run") {
            runLocal()
        }
    }

    fun shiftTapeWindow(delta: Int) {
        _state.update {
            val offset = (it.windowOffset + delta).coerceIn(-10_000, 10_000)
            it.copy(
                windowOffset = offset,
                tapeWindow = TuringEditor.buildTapeWindow(executionState, offset)
            )
        }
    }

    fun submit() {
        val current = _state.value
        val deadlineReached = current.remainingSeconds <= 0L &&
            !current.control?.endsAt.isNullOrBlank()
        submitInternal(dueToTimeout = deadlineReached)
    }

    fun retryTimedOutSubmission() = submitInternal(dueToTimeout = true)

    private fun submitInternal(dueToTimeout: Boolean) {
        val snapshot = _state.value
        if (snapshot.result != null || draftClosed) return
        persistCurrentDraft()
        val control = snapshot.control ?: return
        if (!tryBeginServerRequest(dueToTimeout)) return
        val editorDrafts = control.questions.associate { question ->
            question.id to drafts[question.id]
        }
        val submissionDraft = createLocalDraftSnapshot()
        localDraftJob?.cancel()
        viewModelScope.launch {
            if (submissionDraft != null) {
                try {
                    saveLocalDraftSnapshot(submissionDraft)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    emitSnackbar("Не удалось сохранить локальный черновик перед отправкой")
                }
            }
            val builtSubmission = withContext(dispatchers.default) {
                buildSubmission(control, editorDrafts, dueToTimeout)
            }
            builtSubmission.firstInvalidQuestionIndex?.let { invalidIndex ->
                applyQuestion(invalidIndex)
                val message = "Исправьте алгоритм в задании ${invalidIndex + 1}"
                finishServerRequest(message)
                emitSnackbar(message)
                return@launch
            }
            when (val result = repository.submit(
                controlId = controlId,
                runId = control.runId,
                timedOut = dueToTimeout,
                answers = builtSubmission.answers,
                invalidQuestionIds = builtSubmission.invalidQuestionIds
            )) {
                is AppResult.Error -> {
                    _state.update { current ->
                        val deadlineReached = dueToTimeout ||
                            shouldAutoSubmitTimedAttempt(current.remainingSeconds)
                        current.copy(
                            isSending = false,
                            errorMessage = if (deadlineReached) {
                                "Время закончилось. Ответы сохранены на устройстве, " +
                                    "но сервер не подтвердил завершение: ${result.message}"
                            } else {
                                result.message
                            }
                        )
                    }
                    emitSnackbar(result.message)
                }
                is AppResult.Success -> {
                    timerJob?.cancel()
                    draftClosed = true
                    clearPendingAction()
                    _state.update { it.copy(isSending = false, result = result.data) }
                    deleteLocalDraft()
                    emitSnackbar(result.message ?: "Контрольная завершена")
                }
            }
        }
    }

    private fun performServerAction(action: String, afterSuccess: (() -> Unit)? = null) {
        if (!canEditAttempt()) return
        val question = currentQuestion() ?: return
        val snapshot = _state.value
        val control = snapshot.control ?: return
        if (!tryBeginServerRequest()) return
        persistCurrentDraft()
        viewModelScope.launch {
            val task = withContext(dispatchers.default) {
                buildTask(snapshot.alphabetText, snapshot.programRows, reportErrors = true)
            } ?: run {
                finishServerRequest()
                return@launch
            }
            val requestKey = "${control.runId}:${question.id}:$action:${task.hashCode()}"
            val pendingKey = savedStateHandle.get<String>(PENDING_ACTION_KEY)
            val pendingOperationId = savedStateHandle.get<String>(PENDING_ACTION_ID)
            var operationId: String? = null

            if (pendingKey != null && pendingOperationId != null) {
                when (val receipt = repository.loadActionReceipt(
                    controlId = controlId,
                    runId = control.runId,
                    operationId = pendingOperationId
                )) {
                    is AppResult.Error -> {
                        finishServerRequest(receipt.message)
                        emitSnackbar(
                            "Не удалось проверить предыдущее действие: ${receipt.message}"
                        )
                        return@launch
                    }
                    is AppResult.Success -> {
                        val appliedResult = receipt.data
                        if (appliedResult != null) {
                            clearPendingAction()
                            updateActionResult(appliedResult)
                            if (pendingKey == requestKey) {
                                finishServerRequest()
                                showActionResult(appliedResult, afterSuccess)
                                return@launch
                            }
                        } else if (pendingKey == requestKey) {
                            // Retry the same request with the same ID. If the original request
                            // commits later, the server receipt still prevents a second fee.
                            operationId = pendingOperationId
                        } else {
                            clearPendingAction()
                        }
                    }
                }
            } else if (pendingKey != null || pendingOperationId != null) {
                clearPendingAction()
            }

            val resolvedOperationId = operationId ?: UUID.randomUUID().toString().also { newId ->
                savedStateHandle[PENDING_ACTION_KEY] = requestKey
                savedStateHandle[PENDING_ACTION_ID] = newId
            }
            when (val result = repository.performAction(
                controlId,
                question.id,
                action,
                resolvedOperationId,
                task
            )) {
                is AppResult.Error -> {
                    if (result.type == AppErrorType.AUTHENTICATION ||
                        result.type == AppErrorType.AUTHORIZATION ||
                        result.type == AppErrorType.DATA
                    ) {
                        clearPendingAction()
                    }
                    _state.update { it.copy(isSending = false, errorMessage = result.message) }
                    emitSnackbar(result.message)
                }
                is AppResult.Success -> {
                    clearPendingAction()
                    updateActionResult(result.data)
                    _state.update { it.copy(isSending = false) }
                    showActionResult(result.data, afterSuccess)
                }
            }
        }
    }

    private fun showActionResult(
        result: EmulatorActionResult,
        afterSuccess: (() -> Unit)?
    ) {
        when (result.action) {
            "syntax" -> {
                val message = if (result.syntaxValid) {
                    "Синтаксис корректен"
                } else {
                    result.syntaxErrors.joinToString("\n")
                }
                _state.update {
                    it.copy(
                        statusMessage = message.takeIf { result.syntaxValid },
                        errorMessage = message.takeUnless { result.syntaxValid }
                    )
                }
                emitSnackbar(message)
            }
            "debug" -> {
                val message = "Пройдено скрытых проверок: ${result.passed ?: 0} из ${result.total ?: 0}"
                _state.update { it.copy(statusMessage = message) }
                emitSnackbar(message)
            }
            "run" -> afterSuccess?.invoke()
        }
    }

    private fun tryBeginServerRequest(dueToTimeout: Boolean = false): Boolean {
        while (true) {
            val current = _state.value
            if (current.isSending || current.result != null || draftClosed) return false
            val sending = current.copy(
                isSending = true,
                errorMessage = null,
                remainingSeconds = if (dueToTimeout) 0L else current.remainingSeconds
            )
            if (_state.compareAndSet(current, sending)) return true
        }
    }

    private fun canEditAttempt(): Boolean {
        val current = _state.value
        val timerIsRunning = current.remainingSeconds > 0L || current.control?.endsAt.isNullOrBlank()
        return current.control != null &&
            current.result == null &&
            timerIsRunning &&
            !current.isSending &&
            !draftClosed
    }

    private fun clearPendingActionIfRunChanged(runId: Int) {
        val pendingKey = savedStateHandle.get<String>(PENDING_ACTION_KEY) ?: return
        if (!pendingKey.startsWith("$runId:")) clearPendingAction()
    }

    private fun clearPendingAction() {
        savedStateHandle.remove<String>(PENDING_ACTION_KEY)
        savedStateHandle.remove<String>(PENDING_ACTION_ID)
    }

    private fun finishServerRequest(errorMessage: String? = null) {
        _state.update { it.copy(isSending = false, errorMessage = errorMessage) }
    }

    private fun runLocal() {
        runJob?.cancel()
        runJob = viewModelScope.launch {
            val program = parsedProgram() ?: return@launch
            val input = normalizedInput() ?: return@launch
            if (executionState.steps == 0) {
                executionState = createInitialExecutionUseCase(input)
            }
            _state.update { it.copy(isRunning = true, errorMessage = null) }
            runUseCase(
                parsedProgram = program,
                initialState = executionState,
                maxSteps = 500,
                delayMs = 0
            ).collect { next ->
                executionState = if (next.headPos < 0) {
                    next.copy(halted = true, errorMessage = "Головка вышла за левую границу ленты")
                } else {
                    next
                }
                pushExecution()
                if (executionState.headPos < 0) return@collect
            }
            _state.update { it.copy(isRunning = false) }
        }
    }

    private fun updateActionResult(result: EmulatorActionResult) {
        _state.update { state ->
            val control = state.control ?: return@update state
            val questions = control.questions.map { question ->
                if (question.id == result.questionId) {
                    question.copy(
                        debugCounter = result.debugCounter,
                        syntaxCounter = result.syntaxCounter,
                        runCounter = result.runCounter,
                        feePercent = result.feePercent
                    )
                } else {
                    question
                }
            }
            state.copy(control = control.copy(questions = questions))
        }
    }

    private fun applyQuestion(index: Int) {
        cancelLocalRun()
        val question = _state.value.control?.questions?.getOrNull(index) ?: return
        val draft = drafts[question.id] ?: EditorDraft(
            alphabetText = question.task.alphabet.joinToString(""),
            inputTape = "∂",
            rows = taskToRows(question.task)
        )
        executionState = createInitialExecutionUseCase(draft.inputTape.ifBlank { "∂" })
        _state.update {
            it.copy(
                currentQuestionIndex = index,
                programRows = draft.rows.ifEmpty { listOf(newRow()) },
                alphabetText = draft.alphabetText,
                inputTape = draft.inputTape,
                focusedSymbolField = null,
                isInputTapeFocused = false,
                windowOffset = 0,
                errorMessage = null,
                statusMessage = null
            )
        }
        pushExecution()
    }

    private fun persistCurrentDraft(scheduleSave: Boolean = true) {
        val question = currentQuestion() ?: return
        drafts[question.id] = EditorDraft(
            alphabetText = _state.value.alphabetText,
            inputTape = _state.value.inputTape,
            rows = _state.value.programRows
        )
        if (scheduleSave) scheduleLocalDraftSave()
    }

    fun saveLocalDraft() {
        if (draftClosed || draftFlushedForExit) return
        persistCurrentDraft(scheduleSave = false)
        scheduleLocalDraftSave(immediate = true)
    }

    fun saveLocalDraftAndThen(action: () -> Unit) {
        if (draftClosed) {
            action()
            return
        }
        if (exitSaveInProgress) return
        exitSaveInProgress = true
        persistCurrentDraft(scheduleSave = false)
        val snapshot = createLocalDraftSnapshot()
        localDraftJob?.cancel()
        if (snapshot == null) {
            exitSaveInProgress = false
            action()
            return
        }
        viewModelScope.launch {
            var saved = false
            try {
                saveLocalDraftSnapshot(snapshot)
                saved = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                emitSnackbar("Не удалось сохранить локальный черновик")
            }
            draftFlushedForExit = saved
            exitSaveInProgress = false
            action()
        }
    }

    private fun scheduleLocalDraftSave(immediate: Boolean = false) {
        if (_state.value.control == null) return
        if (draftOwnerKey.isBlank() || draftClosed) return
        localDraftJob?.cancel()
        localDraftJob = viewModelScope.launch {
            if (!immediate) delay(LOCAL_DRAFT_SAVE_DEBOUNCE_MS)
            val snapshot = createLocalDraftSnapshot() ?: return@launch
            try {
                saveLocalDraftSnapshot(snapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val message = "Не удалось сохранить локальный черновик"
                _state.update {
                    it.copy(
                        draftSyncStatus = DraftSyncStatus.ERROR,
                        draftSyncError = message
                    )
                }
                emitSnackbar(message)
            }
        }
    }

    private fun createLocalDraftSnapshot(): EmulatorControlDraft? {
        val control = _state.value.control ?: return null
        if (draftOwnerKey.isBlank() || draftClosed) return null
        return EmulatorControlDraft(
            ownerKey = draftOwnerKey,
            controlId = controlId,
            runId = control.runId,
            endsAt = control.endsAt,
            currentQuestionIndex = _state.value.currentQuestionIndex,
            questions = drafts.mapValues { (_, draft) ->
                EmulatorQuestionDraft(
                    alphabetText = draft.alphabetText,
                    inputTape = draft.inputTape,
                    commands = draft.rows.map { row ->
                        EmulatorCommandDraft(row.leftRuleText, row.rightRuleText)
                    }
                )
            },
            updatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun saveLocalDraftSnapshot(snapshot: EmulatorControlDraft) {
        localDraftMutex.withLock {
            localDraftRepository.save(snapshot)
        }
    }

    private fun deleteLocalDraft() {
        localDraftJob?.cancel()
        if (draftOwnerKey.isBlank()) return
        viewModelScope.launch {
            localDraftMutex.withLock {
                localDraftRepository.delete(draftOwnerKey, controlId)
            }
        }
    }

    private fun draftChanged() {
        cancelLocalRun()
        persistCurrentDraft()
        scheduleCurrentServerDraftSave(
            immediate = false,
            showMessage = false,
            reportErrors = false
        )
    }

    private fun scheduleCurrentServerDraftSave(
        immediate: Boolean,
        showMessage: Boolean,
        reportErrors: Boolean
    ) {
        val revision = ++serverDraftRevision
        _state.update {
            it.copy(
                draftSyncStatus = DraftSyncStatus.LOCAL_SAVED,
                draftSyncError = null
            )
        }
        val control = _state.value.control ?: run {
            serverDraftSaveRequest.value = null
            return
        }
        val question = currentQuestion() ?: run {
            serverDraftSaveRequest.value = null
            return
        }
        val snapshot = _state.value
        serverDraftSaveRequest.value = ServerDraftSaveRequest(
            loadGeneration = loadGeneration,
            revision = revision,
            runId = control.runId,
            questionId = question.id,
            alphabetText = snapshot.alphabetText,
            rows = snapshot.programRows,
            immediate = immediate,
            showMessage = showMessage,
            reportErrors = reportErrors
        )
    }

    private fun observeServerDraftSaves() {
        viewModelScope.launch {
            serverDraftSaveRequest.collectLatest { request ->
                request ?: return@collectLatest
                if (!request.immediate) delay(SERVER_DRAFT_SAVE_DEBOUNCE_MS)
                if (!isCurrentServerDraftRequest(request)) return@collectLatest
                val task = withContext(dispatchers.default) {
                    buildTask(request.alphabetText, request.rows, request.reportErrors)
                } ?: return@collectLatest
                if (!isCurrentServerDraftRequest(request)) return@collectLatest
                _state.update {
                    it.copy(
                        draftSyncStatus = DraftSyncStatus.SYNCING,
                        draftSyncError = null
                    )
                }
                val result = repository.saveDraft(controlId, request.questionId, task)
                if (!isCurrentServerDraftRequest(request)) return@collectLatest
                when (result) {
                    is AppResult.Error -> {
                        _state.update {
                            it.copy(
                                draftSyncStatus = DraftSyncStatus.ERROR,
                                draftSyncError = result.message
                            )
                        }
                        if (request.showMessage) emitSnackbar(result.message)
                    }
                    is AppResult.Success -> {
                        _state.update {
                            it.copy(
                                draftSyncStatus = DraftSyncStatus.SYNCED,
                                draftSyncError = null
                            )
                        }
                        if (request.showMessage) emitSnackbar("Алгоритм сохранён")
                    }
                }
            }
        }
    }

    private fun isCurrentServerDraftRequest(request: ServerDraftSaveRequest): Boolean {
        val control = _state.value.control
        return !draftClosed &&
            request.revision == serverDraftRevision &&
            request.loadGeneration == loadGeneration &&
            control?.runId == request.runId
    }

    private fun currentQuestion() =
        _state.value.control?.questions?.getOrNull(_state.value.currentQuestionIndex)

    private fun buildSubmission(
        control: EmulatorControl,
        editorDrafts: Map<Int, EditorDraft?>,
        dueToTimeout: Boolean
    ): SubmissionBuildResult {
        val answers = mutableListOf<EmulatorAnswer>()
        val invalidQuestionIds = mutableListOf<Int>()
        control.questions.forEachIndexed { index, question ->
            val draft = editorDrafts[question.id]
            val task = draft?.let {
                buildTask(it.alphabetText, it.rows, reportErrors = false)
            }
            if (task == null) {
                if (!dueToTimeout) {
                    return SubmissionBuildResult(
                        answers = emptyList(),
                        invalidQuestionIds = emptyList(),
                        firstInvalidQuestionIndex = index
                    )
                }
                invalidQuestionIds += question.id
            } else {
                answers += EmulatorAnswer(question.id, task)
            }
        }
        return SubmissionBuildResult(
            answers = answers,
            invalidQuestionIds = invalidQuestionIds,
            firstInvalidQuestionIndex = null
        )
    }

    private fun buildTask(reportErrors: Boolean): TuringTaskData? =
        buildTask(_state.value.alphabetText, _state.value.programRows, reportErrors)

    private fun buildTask(
        alphabetText: String,
        rows: List<ProgramCommandRowUiState>,
        reportErrors: Boolean
    ): TuringTaskData? {
        val alphabet = when (val result = parseCompactSymbolSequence(alphabetText)) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                return null
            }
        }.filterNot { it == BLANK_SYMBOL || it == 'Ω' }.toMutableList()

        if (PARTIAL_SYMBOL !in alphabet) alphabet.add(0, PARTIAL_SYMBOL)
        val distinctAlphabet = alphabet.distinct().map(Char::toString)
        val states = linkedMapOf<String, LinkedHashMap<String, String>>()
        states["S0"] = linkedMapOf()

        rows.forEachIndexed { index, row ->
            val left = row.leftRuleText.trim()
            val right = row.rightRuleText.trim()
            if (left.isBlank() && right.isBlank()) return@forEachIndexed
            val leftParts = left.split(Regex("\\s+")).filter(String::isNotBlank)
            if (leftParts.size != 2) {
                if (reportErrors) publishError("Команда ${index + 1}: используйте '<состояние> <символ>'")
                return null
            }
            val state = leftParts[0]
            val read = serverSymbol(leftParts[1], index, reportErrors) ?: return null
            val command = normalizeServerCommand(right)
            val expressions = states.getOrPut(state) { linkedMapOf() }
            if (expressions.containsKey(read)) {
                if (reportErrors) publishError("Команда ${index + 1}: правило для ($state, $read) уже задано")
                return null
            }
            expressions[read] = command
        }

        val allSymbols = distinctAlphabet + "λ"
        val automaton = states.map { (state, commands) ->
            val expressions = linkedMapOf<String, String>()
            allSymbols.forEach { expressions[it] = commands[it].orEmpty() }
            commands.forEach { (symbol, command) -> expressions[symbol] = command }
            TuringStateData(state, expressions)
        }
        return TuringTaskData(distinctAlphabet, automaton)
    }

    private fun normalizeServerCommand(value: String): String {
        val parts = TuringEditor.displaySymbols(value)
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
            .map { token ->
                when (token.uppercase()) {
                    "S" -> "H"
                    "HALT", "\\O" -> "Ω"
                    else -> token
                }
            }
        return parts.joinToString(" ")
    }

    private suspend fun parsedProgram() = withContext(dispatchers.default) {
        val task = buildTask(true) ?: return@withContext null
        val source = serverTaskToProgramSource(task)
        when (val result = parseProgramUseCase(source)) {
            is AppResult.Success -> result.data
            is AppResult.Error -> {
                publishError(result.message)
                null
            }
        }
    }

    private fun serverTaskToProgramSource(task: TuringTaskData): String {
        val lines = mutableListOf<String>()
        task.automaton.forEach { row ->
            row.expressions.forEach { (read, rawCommand) ->
                val command = rawCommand.trim()
                if (command.isBlank()) return@forEach
                val parts = command.split(Regex("\\s+"))
                val readToken = parserSymbol(read)
                if (parts.size == 1) {
                    val move = if (parts[0] == "H") "S" else parts[0]
                    lines += "${row.state} $readToken -> $readToken $move ${row.state}"
                } else if (parts.size == 3) {
                    val write = parserSymbol(parts[0])
                    val move = if (parts[1] == "H") "S" else parts[1]
                    val next = if (parts[2] == "Ω") "HALT" else parts[2]
                    lines += "${row.state} $readToken -> $write $move $next"
                }
            }
        }
        return lines.joinToString("\n")
    }

    private fun taskToRows(task: TuringTaskData): List<ProgramCommandRowUiState> {
        val rows = mutableListOf<ProgramCommandRowUiState>()
        task.automaton.forEach { state ->
            state.expressions.forEach { (symbol, command) ->
                if (command.isNotBlank()) {
                    rows += newRow(
                        left = "${state.state} $symbol",
                        right = command
                    )
                }
            }
        }
        return rows.ifEmpty { listOf(newRow()) }
    }

    private fun serverSymbol(token: String, rowIndex: Int, reportErrors: Boolean): String? {
        return when (val result = parseSingleSymbolToken(token)) {
            is AppResult.Success -> when (result.data) {
                BLANK_SYMBOL -> "λ"
                else -> result.data.toString()
            }
            is AppResult.Error -> {
                if (reportErrors) publishError("Команда ${rowIndex + 1}: ${result.message}")
                null
            }
        }
    }

    private fun parserSymbol(symbol: String): String = TuringEditor.parserTokens(symbol)

    private fun normalizedInput(reportErrors: Boolean = true): String? {
        return when (val result = parseCompactSymbolSequence(_state.value.inputTape)) {
            is AppResult.Success -> {
                val symbols = result.data.filterNot { it == BLANK_SYMBOL }.toMutableList()
                if (symbols.firstOrNull() != PARTIAL_SYMBOL) symbols.add(0, PARTIAL_SYMBOL)
                symbols.joinToString("")
            }
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                null
            }
        }
    }

    private fun updateRow(
        rowId: Long,
        transform: (ProgramCommandRowUiState) -> ProgramCommandRowUiState
    ) {
        _state.update { current ->
            current.copy(
                programRows = TuringEditor.updateRow(current.programRows, rowId, transform)
            )
        }
        draftChanged()
    }

    private fun cancelLocalRun() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false) }
    }

    private fun moveRow(rowId: Long, delta: Int) {
        _state.update { current ->
            current.copy(programRows = TuringEditor.moveRow(current.programRows, rowId, delta))
        }
        draftChanged()
    }

    private fun pushExecution() {
        _state.update {
            it.copy(
                tapeWindow = TuringEditor.buildTapeWindow(executionState, it.windowOffset),
                currentState = executionState.currentState,
                steps = executionState.steps,
                halted = executionState.halted,
                traceList = executionState.trace,
                errorMessage = executionState.errorMessage ?: it.errorMessage,
                statusMessage = executionState.statusMessage ?: it.statusMessage
            )
        }
    }

    private fun startTimer(serverRemainingSeconds: Long?, endsAt: String) {
        timerJob?.cancel()
        val reportedRemainingSeconds = serverRemainingSeconds?.coerceAtLeast(0L)
            ?: parseTestDeadlineMillis(endsAt)?.let {
                remainingTestSeconds(it, System.currentTimeMillis())
            }
            ?: return
        val initialRemainingSeconds = boundedAttemptRemainingSeconds(
            reportedRemainingSeconds = reportedRemainingSeconds,
            configuredMinutes = _state.value.control?.timeMinutes ?: 0
        )
        val deadlineElapsedRealtime = monotonicDeadlineMillis(
            startedAtElapsedRealtime = monotonicClock.elapsedRealtimeMillis(),
            remainingSeconds = initialRemainingSeconds
        )
        timerJob = viewModelScope.launch {
            while (true) {
                val remaining = remainingTestSeconds(
                    deadlineElapsedRealtime,
                    monotonicClock.elapsedRealtimeMillis()
                )
                _state.update { it.copy(remainingSeconds = remaining) }
                if (shouldAutoSubmitTimedAttempt(remaining) && !timeoutSubmissionStarted) {
                    timeoutSubmissionStarted = true
                    _state.update {
                        it.copy(
                            isRunning = false,
                            remainingSeconds = 0L,
                            statusMessage = "Время закончилось. Ответы отправляются автоматически"
                        )
                    }
                    runJob?.cancel()
                    submitInternal(dueToTimeout = true)
                    break
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        localDraftJob?.cancel()
        if (!draftClosed && !draftFlushedForExit) {
            persistCurrentDraft(scheduleSave = false)
            createLocalDraftSnapshot()?.let { snapshot ->
                applicationScope.launch {
                    try {
                        saveLocalDraftSnapshot(snapshot)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        // The UI is gone; the next server load remains the source of truth.
                    }
                }
            }
        }
        super.onCleared()
    }

    private fun publishError(message: String) {
        _state.update { it.copy(errorMessage = message, statusMessage = null) }
        viewModelScope.launch { emitSnackbar(message) }
    }

    private fun newRow(
        left: String = "",
        right: String = ""
    ) = TuringEditor.newRow(nextRowId++, left, right)

    private companion object {
        const val LOCAL_DRAFT_SAVE_DEBOUNCE_MS = 400L
        const val SERVER_DRAFT_SAVE_DEBOUNCE_MS = 800L
        const val PENDING_ACTION_KEY = "emulator_pending_action_key"
        const val PENDING_ACTION_ID = "emulator_pending_action_id"
    }
}

internal fun boundedAttemptRemainingSeconds(
    reportedRemainingSeconds: Long,
    configuredMinutes: Int
): Long {
    val nonNegativeRemaining = reportedRemainingSeconds.coerceAtLeast(0L)
    if (configuredMinutes <= 0) return nonNegativeRemaining
    return nonNegativeRemaining.coerceAtMost(configuredMinutes.toLong() * 60L)
}
