package com.example.uir_android.domain.usecase

import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.turing.BLANK_SYMBOL
import com.example.uir_android.domain.turing.DEFAULT_TRACE_LIMIT
import com.example.uir_android.domain.turing.parseSingleSymbolToken
import com.example.uir_android.domain.turing.sparseTapeFromInput
import com.example.uir_android.domain.model.MoveDirection
import com.example.uir_android.domain.model.ParsedTmProgram
import com.example.uir_android.domain.model.TmExecutionState
import com.example.uir_android.domain.model.TmRule
import com.example.uir_android.domain.model.TmRuleKey
import com.example.uir_android.domain.model.TmRun
import com.example.uir_android.domain.model.TmTraceEntry
import com.example.uir_android.domain.repository.TmRepository
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CreateInitialExecutionUseCase @Inject constructor() {
    operator fun invoke(inputTape: String): TmExecutionState = TmExecutionState(
        tape = sparseTapeFromInput(inputTape),
        headPos = 0,
        currentState = "S0",
        steps = 0,
        halted = false,
        errorMessage = null,
        statusMessage = null,
        trace = emptyList(),
        originalInputTape = inputTape
    )
}

class ParseProgramUseCase @Inject constructor(
    private val dispatchers: AppDispatchers
) {
    suspend operator fun invoke(sourceText: String): AppResult<ParsedTmProgram> =
        withContext(dispatchers.default) {
            parse(sourceText)
        }

    private fun parse(sourceText: String): AppResult<ParsedTmProgram> {
        val rules = linkedMapOf<TmRuleKey, TmRule>()
        val lines = sourceText.lines()

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || line.startsWith("#")) {
                return@forEachIndexed
            }

            val tokens = line.split(Regex("\\s+"))
            if (tokens.size != 6 || tokens[2] != "->") {
                return AppResult.Error("Строка ${index + 1}: ожидается формат '<state> <read> -> <write> <move> <nextState>'")
            }

            val state = tokens[0]
            val readSymbol = when (val result = parseSingleSymbolToken(tokens[1])) {
                is AppResult.Error -> return AppResult.Error("Строка ${index + 1}: ${result.message}")
                is AppResult.Success -> result.data
            }
            val writeSymbol = when (val result = parseSingleSymbolToken(tokens[3])) {
                is AppResult.Error -> return AppResult.Error("Строка ${index + 1}: ${result.message}")
                is AppResult.Success -> result.data
            }
            val move = when (tokens[4]) {
                "L" -> MoveDirection.L
                "R" -> MoveDirection.R
                "S" -> MoveDirection.S
                else -> return AppResult.Error("Строка ${index + 1}: move должен быть L, R или S")
            }
            val nextState = tokens[5]
            val key = TmRuleKey(state, readSymbol)
            if (rules.containsKey(key)) {
                return AppResult.Error("Строка ${index + 1}: дублирующее правило для ($state, $readSymbol)")
            }
            rules[key] = TmRule(
                state = state,
                readSymbol = readSymbol,
                writeSymbol = writeSymbol,
                move = move,
                nextState = nextState
            )
        }

        if (rules.isEmpty()) {
            return AppResult.Error("Добавьте хотя бы одно правило")
        }

        return AppResult.Success(
            ParsedTmProgram(
                rules = rules,
                sourceText = sourceText
            )
        )
    }
}

class StepUseCase @Inject constructor() {
    operator fun invoke(
        parsedProgram: ParsedTmProgram,
        currentState: TmExecutionState,
        traceLimit: Int = DEFAULT_TRACE_LIMIT
    ): TmExecutionState {
        if (currentState.halted) {
            return currentState.copy(statusMessage = "Машина уже остановлена")
        }
        if (currentState.errorMessage != null) {
            return currentState.copy(statusMessage = "Исправьте ошибку или выполните Reset")
        }

        val readSymbol = currentState.currentSymbol()
        val rule = parsedProgram.rules[TmRuleKey(currentState.currentState, readSymbol)]
            ?: return currentState.copy(
                halted = true,
                errorMessage = "No rule for (${currentState.currentState}, $readSymbol)",
                statusMessage = null
            )

        val newTape = currentState.tape.toMutableMap().apply {
            if (rule.writeSymbol == BLANK_SYMBOL) {
                remove(currentState.headPos)
            } else {
                put(currentState.headPos, rule.writeSymbol)
            }
        }

        val nextHeadPos = when (rule.move) {
            MoveDirection.L -> currentState.headPos - 1
            MoveDirection.R -> currentState.headPos + 1
            MoveDirection.S -> currentState.headPos
        }
        val nextStateName = rule.nextState
        val halted = nextStateName in parsedProgram.haltingStates
        val traceEntry = TmTraceEntry(
            stepNumber = currentState.steps + 1,
            state = currentState.currentState,
            headPos = currentState.headPos,
            readSymbol = readSymbol,
            writeSymbol = rule.writeSymbol,
            move = rule.move,
            nextState = nextStateName
        )

        return currentState.copy(
            tape = newTape,
            headPos = nextHeadPos,
            currentState = nextStateName,
            steps = currentState.steps + 1,
            halted = halted,
            errorMessage = null,
            statusMessage = if (halted) "Машина завершила работу" else null,
            trace = (listOf(traceEntry) + currentState.trace).take(traceLimit)
        )
    }
}

class RunUseCase @Inject constructor(
    private val stepUseCase: StepUseCase,
    private val dispatchers: AppDispatchers
) {
    operator fun invoke(
        parsedProgram: ParsedTmProgram,
        initialState: TmExecutionState,
        maxSteps: Int,
        delayMs: Long,
        traceLimit: Int = DEFAULT_TRACE_LIMIT
    ): Flow<TmExecutionState> = flow {
        var state = initialState
        var executed = 0

        while (!state.halted && state.errorMessage == null && executed < maxSteps) {
            state = stepUseCase(parsedProgram, state, traceLimit)
            executed += 1
            emit(state)
            if (!state.halted && state.errorMessage == null && delayMs > 0L) {
                delay(delayMs)
            }
        }

        if (!state.halted && state.errorMessage == null && executed >= maxSteps) {
            emit(state.copy(statusMessage = "Достигнут лимит шагов: $maxSteps"))
        }
    }.flowOn(dispatchers.default)
}

class SaveRunUseCase @Inject constructor(
    private val repository: TmRepository,
    private val json: Json
) {
    suspend operator fun invoke(
        programId: Long?,
        executionState: TmExecutionState
    ): AppResult<Long> {
        val run = TmRun(
            programId = programId,
            inputTape = executionState.originalInputTape,
            halted = executionState.halted,
            stepsCount = executionState.steps,
            endedState = executionState.currentState,
            errorMessage = executionState.errorMessage,
            traceJson = json.encodeToString(executionState.trace),
            createdAt = System.currentTimeMillis()
        )
        return repository.saveRun(run)
    }
}
