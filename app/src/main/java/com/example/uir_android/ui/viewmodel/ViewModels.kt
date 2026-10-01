package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.turing.BLANK_INPUT_TOKEN
import com.example.uir_android.ui.util.encodeSymbolToken
import com.example.uir_android.ui.util.normalizeSymbolSequenceForDisplay
import com.example.uir_android.domain.turing.parseCompactSymbolSequence
import com.example.uir_android.domain.turing.parseSingleSymbolToken
import com.example.uir_android.domain.model.TmExecutionState
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.repository.SettingsRepository
import com.example.uir_android.domain.repository.TmRepository
import com.example.uir_android.ui.editor.TuringEditor
import com.example.uir_android.domain.usecase.CreateInitialExecutionUseCase
import com.example.uir_android.domain.usecase.ParseProgramUseCase
import com.example.uir_android.domain.usecase.RunUseCase
import com.example.uir_android.domain.usecase.SaveRunUseCase
import com.example.uir_android.domain.usecase.StepUseCase
import com.example.uir_android.ui.state.CommandSymbolFieldTarget
import com.example.uir_android.ui.state.FocusedCommandSymbolField
import com.example.uir_android.ui.state.ProgramCommandRowUiState
import com.example.uir_android.ui.state.TuringUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.example.uir_android.domain.turing.OMEGA_INPUT_TOKEN
import com.example.uir_android.domain.turing.PARTIAL_INPUT_TOKEN

@HiltViewModel
class TuringViewModel @Inject constructor(
    private val parseProgramUseCase: ParseProgramUseCase,
    private val stepUseCase: StepUseCase,
    private val runUseCase: RunUseCase,
    private val createInitialExecutionUseCase: CreateInitialExecutionUseCase,
    private val tmRepository: TmRepository,
    private val saveRunUseCase: SaveRunUseCase,
    private val dispatchers: AppDispatchers,
    @ApplicationScope private val applicationScope: CoroutineScope,
    settingsRepository: SettingsRepository
) : EventViewModel() {
    private data class RunPersistenceSnapshot(
        val generation: Long,
        val programId: Long?,
        val executionState: TmExecutionState
    )

    private companion object {
        const val RUN_UI_UPDATE_STEP_INTERVAL = 10
    }

    private val _state = MutableStateFlow(TuringUiState())
    val state = _state.asStateFlow()

    private var executionState = createInitialExecutionUseCase("")
    private var runJob: Job? = null
    private var currentRunGeneration = 0L
    private val runPersistenceMutex = Mutex()
    private val persistedRunGenerations = mutableSetOf<Long>()
    private var nextRowId = 1L

    init {
        _state.value = TuringUiState(programRows = listOf(newCommandRow()))
        pushExecutionState(executionState)

        viewModelScope.launch {
            tmRepository.ensurePresetPrograms()
        }

        viewModelScope.launch {
            tmRepository.observePrograms().collect { programs ->
                _state.update { current -> current.copy(availablePrograms = programs) }
            }
        }

        viewModelScope.launch {
            tmRepository.observeRecentRuns().collect { runs ->
                _state.update { it.copy(recentRuns = runs) }
            }
        }

        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update { current ->
                    current.copy(
                        settings = settings,
                        tapeWindow = TuringEditor.buildTapeWindow(executionState, current.windowOffset)
                    )
                }
            }
        }
    }

    fun updateProgramName(value: String) {
        _state.update { it.copy(programName = value) }
    }

    fun updateInputTape(value: String) {
        cancelRun()
        val displayValue = TuringEditor.displaySymbols(value)
        _state.update {
            it.copy(
                inputTape = displayValue,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun updateAlphabetText(value: String) {
        cancelRun()
        _state.update {
            it.copy(
                alphabetText = normalizeSymbolSequenceForDisplay(value),
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    fun addCommandRow() {
        cancelRun()
        _state.update {
            val rows = it.programRows + newCommandRow()
            it.copy(programRows = rows)
        }
    }

    fun removeCommandRow(rowId: Long) {
        cancelRun()
        _state.update {
            val rows = it.programRows.filterNot { row -> row.id == rowId }.ifEmpty { listOf(newCommandRow()) }
            it.copy(
                programRows = rows,
                focusedSymbolField = it.focusedSymbolField?.takeUnless { focus -> focus.rowId == rowId }
            )
        }
    }

    fun moveCommandRowUp(rowId: Long) {
        moveCommandRow(rowId, -1)
    }

    fun moveCommandRowDown(rowId: Long) {
        moveCommandRow(rowId, 1)
    }

    fun updateCommandLeftRule(rowId: Long, value: String, cursor: Int = value.length) {
        val displayValue = TuringEditor.displaySymbols(value)
        updateRow(rowId) {
            it.copy(
                leftRuleText = displayValue,
            )
        }
        selectSymbolField(rowId, CommandSymbolFieldTarget.READ)
    }

    fun updateCommandRightRule(rowId: Long, value: String, cursor: Int = value.length) {
        val displayValue = TuringEditor.displaySymbols(value)
        updateRow(rowId) {
            it.copy(
                rightRuleText = displayValue,
            )
        }
        selectSymbolField(rowId, CommandSymbolFieldTarget.WRITE)
    }

    fun selectSymbolField(rowId: Long, target: CommandSymbolFieldTarget) {
        _state.update {
            it.copy(
                focusedSymbolField = FocusedCommandSymbolField(rowId, target),
                isInputTapeFocused = false
            )
        }
    }

    fun selectInputTapeField() {
        _state.update {
            it.copy(
                isInputTapeFocused = true,
                focusedSymbolField = null
            )
        }
    }

    fun clearSymbolFieldSelection() {
        _state.update {
            it.copy(
                focusedSymbolField = null,
                isInputTapeFocused = false
            )
        }
    }

    fun insertSpecialToken(token: String) {
        val current = _state.value
        val displayToken = TuringEditor.displaySymbols(token)

        if (current.isInputTapeFocused) {
            updateInputTape(current.inputTape + displayToken)
            return
        }

        val focus = current.focusedSymbolField
        if (focus != null) {
            when (focus.target) {
                CommandSymbolFieldTarget.READ -> {
                    val row = current.programRows.firstOrNull { it.id == focus.rowId } ?: return
                    updateCommandLeftRule(focus.rowId, row.leftRuleText + displayToken)
                }
                CommandSymbolFieldTarget.WRITE -> {
                    val row = current.programRows.firstOrNull { it.id == focus.rowId } ?: return
                    updateCommandRightRule(focus.rowId, row.rightRuleText + displayToken)
                }
            }
            return
        }

        viewModelScope.launch {
            emitSnackbar("Сначала выберите поле ввода")
        }
    }

    fun placeOnTape() {
        cancelRun()
        recreateExecutionFromInput(
            persistCurrentRun = true,
            successMessage = "Лента инициализирована"
        )
    }

    fun clearTape() {
        cancelRun()
        val previousRun = createRunPersistenceSnapshot()
        _state.update {
            it.copy(
                inputTape = "",
                windowOffset = 0,
                errorMessage = null,
                statusMessage = null
            )
        }
        replaceExecutionState(createInitialExecutionUseCase(""))
        pushExecutionState(executionState)
        persistRunAsync(previousRun)
    }

    fun shiftTapeWindow(delta: Int) {
        val nextOffset = (_state.value.windowOffset + delta).coerceIn(-10_000, 10_000)
        _state.update {
            it.copy(
                windowOffset = nextOffset,
                tapeWindow = TuringEditor.buildTapeWindow(executionState, nextOffset)
            )
        }
    }

    fun checkSyntax() {
        cancelRun()
        runJob = viewModelScope.launch {
            buildValidatedProgram() ?: return@launch
            _state.update {
                it.copy(
                    errorMessage = null,
                    statusMessage = "Синтаксис корректен"
                )
            }
            emitSnackbar("Синтаксис корректен")
        }
    }

    fun step() {
        cancelRun()
        runJob = viewModelScope.launch {
            val parsedProgram = buildValidatedProgram() ?: return@launch
            if (executionState.steps == 0 && executionState.tape.isEmpty() && _state.value.inputTape.isNotEmpty()) {
                val normalizedTape = normalizeInputTape(reportErrors = true) ?: return@launch
                replaceExecutionState(createInitialExecutionUseCase(normalizedTape))
            }
            executionState = stepUseCase(parsedProgram, executionState)
            pushExecutionState(executionState)
            finishRunIfNeeded(executionState)
        }
    }

    fun run() {
        cancelRun()
        runJob = viewModelScope.launch {
            val parsedProgram = buildValidatedProgram() ?: return@launch
            if (executionState.steps == 0 && executionState.tape.isEmpty() && _state.value.inputTape.isNotEmpty()) {
                val normalizedTape = normalizeInputTape(reportErrors = true) ?: return@launch
                replaceExecutionState(createInitialExecutionUseCase(normalizedTape))
            }
            val shouldRenderEachStep = _state.value.settings.runDelayMs > 0L
            var lastRenderedStep = executionState.steps
            _state.update { it.copy(isRunning = true, errorMessage = null) }
            runUseCase(
                parsedProgram = parsedProgram,
                initialState = executionState,
                maxSteps = _state.value.settings.maxRunSteps,
                delayMs = _state.value.settings.runDelayMs
            ).collect { state ->
                executionState = state
                val shouldRender = shouldRenderEachStep ||
                    state.halted ||
                    state.errorMessage != null ||
                    !state.statusMessage.isNullOrBlank() ||
                    state.steps - lastRenderedStep >= RUN_UI_UPDATE_STEP_INTERVAL

                if (shouldRender) {
                    pushExecutionState(state)
                    finishRunIfNeeded(state)
                    lastRenderedStep = state.steps
                }
            }
            if (lastRenderedStep != executionState.steps) {
                pushExecutionState(executionState)
                finishRunIfNeeded(executionState)
            }
            _state.update { it.copy(isRunning = false) }
        }
    }

    fun reset() {
        cancelRun()
        recreateExecutionFromInput(
            persistCurrentRun = true,
            successMessage = null
        )
    }

    fun clearAll() {
        cancelRun()
        val previousRun = createRunPersistenceSnapshot()
        val previousState = _state.value
        replaceExecutionState(createInitialExecutionUseCase(""))
        nextRowId = 1L
        _state.value = TuringUiState(
            availablePrograms = previousState.availablePrograms,
            recentRuns = previousState.recentRuns,
            settings = previousState.settings,
            programRows = listOf(newCommandRow()),
            tapeWindow = TuringEditor.buildTapeWindow(executionState, offset = 0)
        )
        persistRunAsync(previousRun)
    }

    fun saveProgram(name: String) {
        cancelRun()
        val safeName = name.trim()
        if (safeName.isBlank()) {
            publishError("Введите название алгоритма")
            return
        }
        runJob = viewModelScope.launch {
            val sourceText = withContext(dispatchers.default) {
                buildProgramSource(reportErrors = true)
            } ?: return@launch
            val current = _state.value
            val existing = current.availablePrograms.firstOrNull { it.id == current.selectedProgramId }
            val now = System.currentTimeMillis()
            val program = TmProgram(
                id = current.selectedProgramId ?: 0,
                name = safeName,
                description = current.programDescription.trim(),
                sourceText = sourceText,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            when (val result = tmRepository.saveProgram(program)) {
                is AppResult.Error -> emitSnackbar(result.message)
                is AppResult.Success -> {
                    val savedId = if (program.id != 0L) program.id else result.data
                    _state.update {
                        it.copy(
                            selectedProgramId = savedId,
                            programName = safeName,
                            programText = sourceText
                        )
                    }
                    emitSnackbar(result.message ?: "Программа сохранена")
                }
            }
        }
    }

    fun showLoadDialog() {
        _state.update { it.copy(showLoadDialog = true) }
    }

    fun hideLoadDialog() {
        _state.update { it.copy(showLoadDialog = false) }
    }

    fun loadProgram(program: TmProgram) {
        cancelRun()
        runJob = viewModelScope.launch {
            applyLoadedProgram(program)
            emitSnackbar("Загружена программа '${program.name}'")
        }
    }

    private suspend fun applyLoadedProgram(program: TmProgram) {
        val previousRun = createRunPersistenceSnapshot()
        val (rows, parsedAlphabet) = withContext(dispatchers.default) {
            val parsedRows = rowsFromSource(program.sourceText)
            parsedRows to buildAlphabetFromRows(parsedRows)
        }
        val alphabet = parsedAlphabet.ifBlank { _state.value.alphabetText }
        _state.update {
            it.copy(
                programName = program.name,
                programDescription = program.description,
                programText = buildProgramPreview(rows),
                programRows = rows,
                alphabetText = alphabet,
                selectedProgramId = program.id,
                showLoadDialog = false,
                errorMessage = null,
                statusMessage = null,
                windowOffset = 0,
                focusedSymbolField = null
            )
        }
        replaceExecutionState(
            createInitialExecutionUseCase(normalizeInputTape(reportErrors = false).orEmpty())
        )
        pushExecutionState(executionState)
        persistRunAsync(previousRun)
    }

    private suspend fun buildValidatedProgram() = withContext(dispatchers.default) {
        val sourceText = buildProgramSource(reportErrors = true) ?: return@withContext null
        val alphabet = parseAlphabet(reportErrors = true) ?: return@withContext null
        if (!validateRowsAgainstAlphabet(alphabet, reportErrors = true)) {
            return@withContext null
        }
        if (!validateTapeAgainstAlphabet(alphabet, reportErrors = true)) {
            return@withContext null
        }

        when (val result = parseProgramUseCase(sourceText)) {
            is AppResult.Error -> {
                publishError(result.message)
                null
            }
            is AppResult.Success -> {
                _state.update { it.copy(programText = sourceText, errorMessage = null) }
                result.data
            }
        }
    }

    private fun buildProgramSource(reportErrors: Boolean): String? {
        val lines = mutableListOf<String>()

        _state.value.programRows.forEachIndexed { index, row ->
            val commandIndex = index + 1

            val leftRaw = row.leftRuleText.trim()
            val rightRaw = row.rightRuleText.trim()

            if (leftRaw.isBlank() && rightRaw.isBlank()) {
                return@forEachIndexed
            }

            val leftTokens = splitCommandPart(leftRaw)
            val rightTokens = splitCommandPart(rightRaw)

            if (leftTokens.size != 2) {
                if (reportErrors) {
                    publishError("Команда $commandIndex: левая часть должна быть в формате '<state> <read>'")
                }
                return null
            }

            if (rightTokens.isEmpty()) {
                if (reportErrors) {
                    publishError("Команда $commandIndex: правая часть не заполнена")
                }
                return null
            }

            val currentState = leftTokens[0]

            val readSymbol = parseRuleSymbol(
                token = leftTokens[1],
                commandIndex = commandIndex,
                label = "ошибка в символе чтения",
                reportErrors = reportErrors
            ) ?: return null

            val readEncoded = encodeSymbolToken(readSymbol)

            when (rightTokens.size) {
                1 -> {
                    val rawMove = rightTokens[0].uppercase()

                    when (rawMove) {
                        "L", "R", "S" -> {
                            lines += "$currentState $readEncoded -> $readEncoded $rawMove $currentState"
                        }

                        "H", "Ω", "\\O", "HALT" -> {
                            lines += "$currentState $readEncoded -> $readEncoded S HALT"
                        }

                        else -> {
                            if (reportErrors) {
                                publishError("Команда $commandIndex: короткая форма допускает только L, R, S или H")
                            }
                            return null
                        }
                    }
                }

                3 -> {
                    val writeSymbol = parseRuleSymbol(
                        token = rightTokens[0],
                        commandIndex = commandIndex,
                        label = "ошибка в символе записи",
                        reportErrors = reportErrors
                    ) ?: return null

                    val rawMove = rightTokens[1].uppercase()
                    val nextStateRaw = rightTokens[2]

                    when (rawMove) {
                        "L", "R", "S" -> {
                            val nextState = TuringEditor.normalizeStateAlias(nextStateRaw)
                            lines += "$currentState $readEncoded -> ${encodeSymbolToken(writeSymbol)} $rawMove $nextState"
                        }

                        "H" -> {
                            lines += "$currentState $readEncoded -> ${encodeSymbolToken(writeSymbol)} S HALT"
                        }

                        else -> {
                            if (reportErrors) {
                                publishError("Команда $commandIndex: move должен быть L, R, S или H")
                            }
                            return null
                        }
                    }
                }

                else -> {
                    if (reportErrors) {
                        publishError(
                            "Команда $commandIndex: используйте либо '<write> <move> <nextState>', либо короткую форму '<move>'"
                        )
                    }
                    return null
                }
            }
        }

        if (lines.isEmpty()) {
            if (reportErrors) {
                publishError("Добавьте хотя бы одну команду")
            }
            return null
        }

        return lines.joinToString("\n")
    }

    private fun buildProgramPreview(rows: List<ProgramCommandRowUiState>): String {
        return rows.mapNotNull { row ->
            val left = row.leftRuleText.trim()
            val right = row.rightRuleText.trim()
            val empty = left.isBlank() && right.isBlank()
            if (empty) {
                null
            } else if (left.isBlank() || right.isBlank()) {
                null
            } else {
                "$left -> $right"
            }
        }.joinToString("\n")
    }

    private fun parseAlphabet(reportErrors: Boolean): Set<Char>? {
        return when (val result = parseCompactSymbolSequence(_state.value.alphabetText)) {
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                null
            }
            is AppResult.Success -> {
                if (result.data.isEmpty()) {
                    if (reportErrors) publishError("Введите алфавит")
                    null
                } else {
                    result.data.toSet()
                }
            }
        }
    }

    private fun normalizeInputTape(reportErrors: Boolean): String? {
        return when (val result = parseCompactSymbolSequence(TuringEditor.parserTokens(_state.value.inputTape))) {
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                null
            }
            is AppResult.Success -> result.data.joinToString(separator = "")
        }
    }

    private fun validateRowsAgainstAlphabet(alphabet: Set<Char>, reportErrors: Boolean): Boolean {
        _state.value.programRows.forEachIndexed { index, row ->
            val commandIndex = index + 1

            val leftTokens = splitCommandPart(row.leftRuleText)
            val rightTokens = splitCommandPart(row.rightRuleText)

            val empty = leftTokens.isEmpty() && rightTokens.isEmpty()
            if (empty) {
                return@forEachIndexed
            }

            if (leftTokens.size != 2) {
                return@forEachIndexed
            }

            val readSymbol = when (val result = parseSingleSymbolToken(leftTokens[1])) {
                is AppResult.Success -> result.data
                else -> return@forEachIndexed
            }

            val writeSymbol: Char = when (rightTokens.size) {
                1 -> readSymbol
                3 -> when (val result = parseSingleSymbolToken(rightTokens[0])) {
                    is AppResult.Success -> result.data
                    else -> return@forEachIndexed
                }
                else -> return@forEachIndexed
            }

            if (!isAllowedRuleSymbol(readSymbol, alphabet)) {
                if (reportErrors) publishError("Команда $commandIndex: символ чтения должен входить в алфавит или быть служебным символом")
                return false
            }

            if (!isAllowedRuleSymbol(writeSymbol, alphabet)) {
                if (reportErrors) publishError("Команда $commandIndex: символ записи должен входить в алфавит или быть служебным символом")
                return false
            }
        }

        return true
    }

    private fun validateTapeAgainstAlphabet(alphabet: Set<Char>, reportErrors: Boolean): Boolean {
        return when (val result = parseCompactSymbolSequence(TuringEditor.parserTokens(_state.value.inputTape))) {
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                false
            }
            is AppResult.Success -> {
                if (result.data.any { it !in alphabet }) {
                    if (reportErrors) publishError("Лента содержит символы вне алфавита")
                    false
                } else {
                    true
                }
            }
        }
    }

    private fun recreateExecutionFromInput(
        persistCurrentRun: Boolean,
        successMessage: String?
    ) {
        val normalizedTape = normalizeInputTape(reportErrors = true) ?: return
        val alphabet = parseAlphabet(reportErrors = true) ?: return
        if (!validateTapeAgainstAlphabet(alphabet, reportErrors = true)) {
            return
        }
        val previousRun = createRunPersistenceSnapshot().takeIf { persistCurrentRun }
        replaceExecutionState(createInitialExecutionUseCase(normalizedTape))
        _state.update {
            it.copy(
                windowOffset = 0,
                errorMessage = null,
                statusMessage = successMessage
            )
        }
        pushExecutionState(executionState)
        previousRun?.let(::persistRunAsync)
    }

    private fun updateRow(rowId: Long, transform: (ProgramCommandRowUiState) -> ProgramCommandRowUiState) {
        cancelRun()
        _state.update { current ->
            current.copy(
                programRows = TuringEditor.updateRow(current.programRows, rowId, transform),
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    private fun moveCommandRow(rowId: Long, delta: Int) {
        cancelRun()
        _state.update { current ->
            current.copy(programRows = TuringEditor.moveRow(current.programRows, rowId, delta))
        }
    }
    private fun rowsFromSource(sourceText: String): List<ProgramCommandRowUiState> {
        val rows = sourceText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val tokens = line.split(Regex("\\s+"))
                if (tokens.size != 6 || tokens[2] != "->") {
                    null
                } else {
                    val read = when (val result = parseSingleSymbolToken(tokens[1])) {
                        is AppResult.Success -> encodeSymbolToken(result.data)
                        else -> return@mapNotNull null
                    }
                    val write = when (val result = parseSingleSymbolToken(tokens[3])) {
                        is AppResult.Success -> encodeSymbolToken(result.data)
                        else -> return@mapNotNull null
                    }
                    newCommandRow(
                        leftRuleText = "${tokens[0]} $read",
                        rightRuleText = "$write ${tokens[4]} ${tokens[5]}"
                    )
                }
            }
            .toList()

        return rows.ifEmpty { listOf(newCommandRow()) }
    }

    private fun buildAlphabetFromRows(rows: List<ProgramCommandRowUiState>): String {
        val symbols = linkedSetOf<Char>()
        rows.forEach { row ->
            val leftTokens = row.leftRuleText.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            val rightTokens = row.rightRuleText.trim().split(Regex("\\s+")).filter(String::isNotBlank)
            when (val result = parseSingleSymbolToken(leftTokens.getOrNull(1).orEmpty())) {
                is AppResult.Success -> symbols += result.data
                else -> Unit
            }
            when (val result = parseSingleSymbolToken(rightTokens.getOrNull(0).orEmpty())) {
                is AppResult.Success -> symbols += result.data
                else -> Unit
            }
        }
        symbols.remove('_')
        return if (symbols.isEmpty()) "" else symbols.joinToString(separator = "") { encodeSymbolToken(it) }
    }

    private fun publishError(message: String) {
        _state.update { it.copy(errorMessage = message, statusMessage = null) }
        viewModelScope.launch { emitSnackbar(message) }
    }

    private fun cancelRun() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false) }
    }

    private fun pushExecutionState(state: TmExecutionState) {
        _state.update { current ->
            current.copy(
                tapeWindow = TuringEditor.buildTapeWindow(state, current.windowOffset),
                currentState = state.currentState,
                steps = state.steps,
                halted = state.halted,
                errorMessage = state.errorMessage ?: current.errorMessage,
                statusMessage = state.statusMessage ?: current.statusMessage,
                traceList = state.trace
            )
        }
    }

    private fun finishRunIfNeeded(state: TmExecutionState) {
        if (state.errorMessage != null) {
            viewModelScope.launch { emitSnackbar(state.errorMessage) }
        } else if (!state.statusMessage.isNullOrBlank()) {
            viewModelScope.launch { emitSnackbar(state.statusMessage) }
        }

        val shouldPersist = state.halted || state.errorMessage != null || state.statusMessage?.startsWith("Достигнут лимит") == true
        if (shouldPersist) {
            persistRunAsync(createRunPersistenceSnapshot(state))
        }
    }

    private fun replaceExecutionState(newState: TmExecutionState) {
        currentRunGeneration += 1
        executionState = newState
    }

    private fun createRunPersistenceSnapshot(
        state: TmExecutionState = executionState
    ) = RunPersistenceSnapshot(
        generation = currentRunGeneration,
        programId = _state.value.selectedProgramId,
        executionState = state
    )

    private fun persistRunAsync(snapshot: RunPersistenceSnapshot) {
        if (snapshot.executionState.steps == 0) return
        applicationScope.launch {
            persistRunIfNeeded(snapshot)
        }
    }

    private suspend fun persistRunIfNeeded(snapshot: RunPersistenceSnapshot) {
        if (snapshot.executionState.steps == 0) return
        runPersistenceMutex.withLock {
            if (snapshot.generation in persistedRunGenerations) return
            when (saveRunUseCase(snapshot.programId, snapshot.executionState)) {
                is AppResult.Success -> persistedRunGenerations += snapshot.generation
                is AppResult.Error -> Unit
            }
        }
    }

    private fun newCommandRow(
        leftRuleText: String = "",
        rightRuleText: String = ""
    ) = TuringEditor.newRow(nextRowId++, leftRuleText, rightRuleText)

    private fun splitCommandPart(value: String): List<String> {
        return TuringEditor.splitCommandPart(value)
    }

    private fun parseRuleSymbol(
        token: String,
        commandIndex: Int,
        label: String,
        reportErrors: Boolean
    ): Char? {
        return when (val result = parseSingleSymbolToken(token)) {
            is AppResult.Error -> {
                if (reportErrors) publishError("Команда $commandIndex: $label: ${result.message}")
                null
            }
            is AppResult.Success -> result.data
        }
    }

    private fun isSpecialRuleSymbol(symbol: Char): Boolean {
        val encoded = encodeSymbolToken(symbol)
        return encoded == BLANK_INPUT_TOKEN || encoded == PARTIAL_INPUT_TOKEN
    }

    private fun isAllowedRuleSymbol(symbol: Char, alphabet: Set<Char>): Boolean {
        return symbol in alphabet || isSpecialRuleSymbol(symbol)
    }

}
