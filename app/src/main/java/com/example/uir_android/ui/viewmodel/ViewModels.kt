package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.util.AppResult
import com.example.uir_android.core.util.BLANK_INPUT_TOKEN
import com.example.uir_android.core.util.DEFAULT_TAPE_RADIUS
import com.example.uir_android.core.util.encodeSymbolToken
import com.example.uir_android.core.util.normalizeSymbolSequenceForDisplay
import com.example.uir_android.core.util.parseCompactSymbolSequence
import com.example.uir_android.core.util.parseSingleSymbolToken
import com.example.uir_android.domain.model.TapeCell
import com.example.uir_android.domain.model.TmExecutionState
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.usecase.CreateInitialExecutionUseCase
import com.example.uir_android.domain.usecase.EnsurePresetProgramsUseCase
import com.example.uir_android.domain.usecase.LoadProgramsUseCase
import com.example.uir_android.domain.usecase.ObserveRunsUseCase
import com.example.uir_android.domain.usecase.ObserveSettingsUseCase
import com.example.uir_android.domain.usecase.ParseProgramUseCase
import com.example.uir_android.domain.usecase.RunUseCase
import com.example.uir_android.domain.usecase.SaveProgramUseCase
import com.example.uir_android.domain.usecase.SaveRunUseCase
import com.example.uir_android.domain.usecase.StepUseCase
import com.example.uir_android.domain.usecase.UpdateDebugEnabledUseCase
import com.example.uir_android.domain.usecase.UpdateMaxRunStepsUseCase
import com.example.uir_android.domain.usecase.UpdateRunDelayUseCase
import com.example.uir_android.ui.state.CommandSymbolFieldTarget
import com.example.uir_android.ui.state.FocusedCommandSymbolField
import com.example.uir_android.ui.state.ProgramCommandRowUiState
import com.example.uir_android.ui.state.SettingsUiState
import com.example.uir_android.ui.state.TuringUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.uir_android.core.util.OMEGA_INPUT_TOKEN
import com.example.uir_android.core.util.PARTIAL_INPUT_TOKEN

abstract class EventViewModel : ViewModel() {
    protected val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()
}

@HiltViewModel
class TuringViewModel @Inject constructor(
    private val parseProgramUseCase: ParseProgramUseCase,
    private val stepUseCase: StepUseCase,
    private val runUseCase: RunUseCase,
    private val createInitialExecutionUseCase: CreateInitialExecutionUseCase,
    private val saveProgramUseCase: SaveProgramUseCase,
    private val loadProgramsUseCase: LoadProgramsUseCase,
    private val observeRunsUseCase: ObserveRunsUseCase,
    private val saveRunUseCase: SaveRunUseCase,
    private val ensurePresetProgramsUseCase: EnsurePresetProgramsUseCase,
    observeSettingsUseCase: ObserveSettingsUseCase
) : EventViewModel() {
    private companion object {
        const val RUN_UI_UPDATE_STEP_INTERVAL = 10

        const val DISPLAY_BLANK_SYMBOL = "λ"
        const val DISPLAY_OMEGA_SYMBOL = "Ω"
        const val DISPLAY_PARTIAL_SYMBOL = "∂"
    }

    private fun toDisplaySymbols(value: String): String {
        return value
            .replace(BLANK_INPUT_TOKEN, DISPLAY_BLANK_SYMBOL)
            .replace(OMEGA_INPUT_TOKEN, DISPLAY_OMEGA_SYMBOL)
            .replace(PARTIAL_INPUT_TOKEN, DISPLAY_PARTIAL_SYMBOL)
    }

    private fun toParserTokens(value: String): String {
        return value
            .replace(DISPLAY_BLANK_SYMBOL, BLANK_INPUT_TOKEN)
            .replace(DISPLAY_OMEGA_SYMBOL, OMEGA_INPUT_TOKEN)
            .replace(DISPLAY_PARTIAL_SYMBOL, PARTIAL_INPUT_TOKEN)
    }

    private val _state = MutableStateFlow(TuringUiState())
    val state = _state.asStateFlow()

    private var executionState = createInitialExecutionUseCase("")
    private var runJob: Job? = null
    private var currentRunPersisted = false
    private var nextRowId = 1L

    init {
        _state.value = TuringUiState(programRows = listOf(newCommandRow()))
        pushExecutionState(executionState)

        viewModelScope.launch {
            ensurePresetProgramsUseCase()
        }

        viewModelScope.launch {
            loadProgramsUseCase().collect { programs ->
                _state.update { current -> current.copy(availablePrograms = programs) }
            }
        }

        viewModelScope.launch {
            observeRunsUseCase().collect { runs ->
                _state.update { it.copy(recentRuns = runs) }
            }
        }

        viewModelScope.launch {
            observeSettingsUseCase().collect { settings ->
                _state.update { current ->
                    current.copy(
                        settings = settings,
                        tapeWindow = buildTapeWindow(executionState, offset = current.windowOffset)
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
        val displayValue = toDisplaySymbols(value)
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
        _state.update {
            val rows = it.programRows + newCommandRow()
            it.copy(programRows = rows)
        }
    }

    fun removeCommandRow(rowId: Long) {
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
        val displayValue = toDisplaySymbols(value)
        updateRow(rowId) {
            it.copy(
                leftRuleText = displayValue,
            )
        }
        selectSymbolField(rowId, CommandSymbolFieldTarget.READ)
    }

    fun updateCommandRightRule(rowId: Long, value: String, cursor: Int = value.length) {
        val displayValue = toDisplaySymbols(value)
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
        val displayToken = toDisplaySymbols(token)

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
            messageChannel.send("Сначала выберите поле ввода")
        }
    }

    fun placeOnTape() {
        cancelRun()
        recreateExecutionFromInput(
            persistCurrentRun = false,
            successMessage = "Лента инициализирована"
        )
    }

    fun clearTape() {
        cancelRun()
        viewModelScope.launch {
            persistRunIfNeeded()
            _state.update {
                it.copy(
                    inputTape = "",
                    windowOffset = 0,
                    errorMessage = null,
                    statusMessage = null
                )
            }
            executionState = createInitialExecutionUseCase("")
            currentRunPersisted = false
            pushExecutionState(executionState)
        }
    }

    fun shiftTapeWindow(delta: Int) {
        val nextOffset = (_state.value.windowOffset + delta).coerceIn(-10_000, 10_000)
        _state.update {
            it.copy(
                windowOffset = nextOffset,
                tapeWindow = buildTapeWindow(executionState, offset = nextOffset)
            )
        }
    }

    fun checkSyntax() {
        cancelRun()
        buildValidatedProgram()?.let {
            _state.update {
                it.copy(
                    errorMessage = null,
                    statusMessage = "Синтаксис корректен"
                )
            }
            viewModelScope.launch { messageChannel.send("Синтаксис корректен") }
        }
    }

    fun step() {
        cancelRun()
        val parsedProgram = buildValidatedProgram() ?: return
        if (executionState.steps == 0 && executionState.tape.isEmpty() && _state.value.inputTape.isNotEmpty()) {
            val normalizedTape = normalizeInputTape(reportErrors = true) ?: return
            executionState = createInitialExecutionUseCase(normalizedTape)
        }
        executionState = stepUseCase(parsedProgram, executionState)
        currentRunPersisted = false
        pushExecutionState(executionState)
        finishRunIfNeeded(executionState)
    }

    fun run() {
        cancelRun()
        val parsedProgram = buildValidatedProgram() ?: return
        if (executionState.steps == 0 && executionState.tape.isEmpty() && _state.value.inputTape.isNotEmpty()) {
            val normalizedTape = normalizeInputTape(reportErrors = true) ?: return
            executionState = createInitialExecutionUseCase(normalizedTape)
        }

        runJob = viewModelScope.launch {
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
                currentRunPersisted = false
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
        viewModelScope.launch {
            persistRunIfNeeded()
            executionState = createInitialExecutionUseCase("")
            currentRunPersisted = false
            nextRowId = 1L
            _state.value = TuringUiState(
                availablePrograms = _state.value.availablePrograms,
                recentRuns = _state.value.recentRuns,
                settings = _state.value.settings,
                programRows = listOf(newCommandRow()),
                tapeWindow = buildTapeWindow(executionState)
            )
        }
    }

    fun saveProgram() {
        cancelRun()
        val sourceText = buildProgramSource(reportErrors = true) ?: return
        val current = _state.value
        val existing = current.availablePrograms.firstOrNull { it.id == current.selectedProgramId }
        val now = System.currentTimeMillis()
        val safeName = current.programName.trim().ifBlank { "Программа $now" }
        val program = TmProgram(
            id = current.selectedProgramId ?: 0,
            name = safeName,
            description = current.programDescription.trim(),
            sourceText = sourceText,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        viewModelScope.launch {
            when (val result = saveProgramUseCase(program)) {
                AppResult.Loading -> Unit
                is AppResult.Error -> messageChannel.send(result.message)
                is AppResult.Success -> {
                    val savedId = if (program.id != 0L) program.id else result.data
                    _state.update {
                        it.copy(
                            selectedProgramId = savedId,
                            programName = safeName,
                            programText = sourceText
                        )
                    }
                    messageChannel.send(result.message ?: "Программа сохранена")
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
        applyLoadedProgram(program)
        viewModelScope.launch {
            messageChannel.send("Загружена программа '${program.name}'")
        }
    }

    private fun applyLoadedProgram(program: TmProgram) {
        val rows = rowsFromSource(program.sourceText)
        val alphabet = buildAlphabetFromRows(rows).ifBlank { _state.value.alphabetText }
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
        executionState = createInitialExecutionUseCase(normalizeInputTape(reportErrors = false).orEmpty())
        currentRunPersisted = false
        pushExecutionState(executionState)
    }

    private fun buildValidatedProgram() = run {
        val sourceText = buildProgramSource(reportErrors = true) ?: return@run null
        val alphabet = parseAlphabet(reportErrors = true) ?: return@run null
        if (!validateRowsAgainstAlphabet(alphabet, reportErrors = true)) {
            return@run null
        }
        if (!validateTapeAgainstAlphabet(alphabet, reportErrors = true)) {
            return@run null
        }

        when (val result = parseProgramUseCase(sourceText)) {
            is AppResult.Error -> {
                publishError(result.message)
                null
            }
            AppResult.Loading -> null
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
                            val nextState = normalizeStateAlias(nextStateRaw)
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
            AppResult.Loading -> null
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
        return when (val result = parseCompactSymbolSequence(toParserTokens(_state.value.inputTape))) {
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                null
            }
            AppResult.Loading -> null
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
        return when (val result = parseCompactSymbolSequence(toParserTokens(_state.value.inputTape))) {
            is AppResult.Error -> {
                if (reportErrors) publishError(result.message)
                false
            }
            AppResult.Loading -> false
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
        viewModelScope.launch {
            if (persistCurrentRun) {
                persistRunIfNeeded()
            }
            val normalizedTape = normalizeInputTape(reportErrors = true) ?: return@launch
            val alphabet = parseAlphabet(reportErrors = true) ?: return@launch
            if (!validateTapeAgainstAlphabet(alphabet, reportErrors = true)) {
                return@launch
            }
            executionState = createInitialExecutionUseCase(normalizedTape)
            currentRunPersisted = false
            _state.update {
                it.copy(
                    windowOffset = 0,
                    errorMessage = null,
                    statusMessage = successMessage
                )
            }
            pushExecutionState(executionState)
        }
    }

    private fun updateRow(rowId: Long, transform: (ProgramCommandRowUiState) -> ProgramCommandRowUiState) {
        _state.update { current ->
            val rows = current.programRows.map { row ->
                if (row.id == rowId) transform(row) else row
            }
            current.copy(
                programRows = rows,
                errorMessage = null,
                statusMessage = null
            )
        }
    }

    private fun moveCommandRow(rowId: Long, delta: Int) {
        _state.update { current ->
            val index = current.programRows.indexOfFirst { it.id == rowId }
            if (index == -1) {
                current
            } else {
                val targetIndex = (index + delta).coerceIn(0, current.programRows.lastIndex)
                if (targetIndex == index) {
                    current
                } else {
                    val rows = current.programRows.toMutableList()
                    val item = rows.removeAt(index)
                    rows.add(targetIndex, item)
                    current.copy(programRows = rows)
                }
            }
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
        viewModelScope.launch { messageChannel.send(message) }
    }

    private fun cancelRun() {
        runJob?.cancel()
        runJob = null
        _state.update { it.copy(isRunning = false) }
    }

    private fun pushExecutionState(state: TmExecutionState) {
        _state.update { current ->
            current.copy(
                tapeWindow = buildTapeWindow(state, offset = current.windowOffset),
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
            viewModelScope.launch { messageChannel.send(state.errorMessage) }
        } else if (!state.statusMessage.isNullOrBlank()) {
            viewModelScope.launch { messageChannel.send(state.statusMessage) }
        }

        val shouldPersist = state.halted || state.errorMessage != null || state.statusMessage?.startsWith("Достигнут лимит") == true
        if (shouldPersist) {
            viewModelScope.launch {
                persistRunIfNeeded()
            }
        }
    }

    private suspend fun persistRunIfNeeded() {
        if (currentRunPersisted || executionState.steps == 0) {
            return
        }
        saveRunUseCase(_state.value.selectedProgramId, executionState)
        currentRunPersisted = true
    }

    private fun buildTapeWindow(
        state: TmExecutionState,
        radius: Int = DEFAULT_TAPE_RADIUS,
        offset: Int = _state.value.windowOffset
    ): List<TapeCell> {
        val visibleCount = radius * 2 + 1
        val center = state.headPos + offset

        val start = when {
            center < 0 -> center - radius
            center <= radius -> 0
            else -> center - radius
        }

        val end = start + visibleCount - 1

        return (start..end).map { index ->
            TapeCell(
                index = index,
                symbol = state.tape[index] ?: '_',
                isHead = index == state.headPos
            )
        }
    }

    private fun newCommandRow(
        leftRuleText: String = "",
        rightRuleText: String = ""
    ) = ProgramCommandRowUiState(
        id = nextRowId++,
        leftRuleText = leftRuleText,
        rightRuleText = rightRuleText
    )

    private fun splitCommandPart(value: String): List<String> {
        return value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
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
            AppResult.Loading -> null
            is AppResult.Success -> result.data
        }
    }

    private fun normalizeStateAlias(rawState: String): String {
        return when (rawState.trim().uppercase()) {
            "Ω", "\\O", "HALT" -> "HALT"
            else -> rawState.trim()
        }
    }

    private fun normalizeMoveAlias(rawMove: String): String? {
        return when (rawMove.trim().uppercase()) {
            "L" -> "L"
            "R" -> "R"
            "S" -> "S"
            "H" -> "S"
            else -> null
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettingsUseCase: ObserveSettingsUseCase,
    private val updateMaxRunStepsUseCase: UpdateMaxRunStepsUseCase,
    private val updateRunDelayUseCase: UpdateRunDelayUseCase,
    private val updateDebugEnabledUseCase: UpdateDebugEnabledUseCase
) : EventViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettingsUseCase().collect { settings ->
                _state.update { current ->
                    current.copy(
                        settings = settings,
                        maxRunStepsInput = if (current.maxRunStepsInput.isBlank() || current.maxRunStepsInput == current.settings.maxRunSteps.toString()) {
                            settings.maxRunSteps.toString()
                        } else {
                            current.maxRunStepsInput
                        },
                        runDelayInput = if (current.runDelayInput.isBlank() || current.runDelayInput == current.settings.runDelayMs.toString()) {
                            settings.runDelayMs.toString()
                        } else {
                            current.runDelayInput
                        }
                    )
                }
            }
        }
    }

    fun updateMaxRunStepsInput(value: String) {
        _state.update { it.copy(maxRunStepsInput = value) }
    }

    fun updateRunDelayInput(value: String) {
        _state.update { it.copy(runDelayInput = value) }
    }

    fun toggleDebug(enabled: Boolean) {
        viewModelScope.launch {
            updateDebugEnabledUseCase(enabled)
            messageChannel.send(if (enabled) "Debug включен" else "Debug выключен")
        }
    }

    fun saveSettings() {
        val maxSteps = _state.value.maxRunStepsInput.trim().toIntOrNull()
        val runDelay = _state.value.runDelayInput.trim().toLongOrNull()

        if (maxSteps == null || maxSteps <= 0) {
            viewModelScope.launch { messageChannel.send("Лимит шагов должен быть положительным числом") }
            return
        }
        if (runDelay == null || runDelay < 0) {
            viewModelScope.launch { messageChannel.send("Скорость должна быть неотрицательным числом") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            updateMaxRunStepsUseCase(maxSteps)
            updateRunDelayUseCase(runDelay)
            _state.update { it.copy(isSaving = false) }
            messageChannel.send("Сохранено")
        }
    }
}




