package com.example.uir_android.domain.model

import com.example.uir_android.core.util.BLANK_SYMBOL
import kotlinx.serialization.Serializable

@Serializable
enum class MoveDirection {
    L,
    R,
    S
}

data class AppSettings(
    val maxRunSteps: Int,
    val runDelayMs: Long,
    val debugEnabled: Boolean
)

data class TmProgram(
    val id: Long = 0,
    val name: String,
    val description: String,
    val sourceText: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class TmTraceEntry(
    val stepNumber: Int,
    val state: String,
    val headPos: Int,
    val readSymbol: Char,
    val writeSymbol: Char,
    val move: MoveDirection,
    val nextState: String
)

data class TmRun(
    val id: Long = 0,
    val programId: Long?,
    val inputTape: String,
    val halted: Boolean,
    val stepsCount: Int,
    val endedState: String,
    val errorMessage: String?,
    val traceJson: String,
    val createdAt: Long
)

data class TmRuleKey(
    val state: String,
    val readSymbol: Char
)

data class TmRule(
    val state: String,
    val readSymbol: Char,
    val writeSymbol: Char,
    val move: MoveDirection,
    val nextState: String
)

data class ParsedTmProgram(
    val rules: Map<TmRuleKey, TmRule>,
    val haltingStates: Set<String> = setOf("HALT", "q_accept", "q_reject"),
    val sourceText: String
)

data class TmExecutionState(
    val tape: Map<Int, Char> = emptyMap(),
    val headPos: Int = 0,
    val currentState: String = "S0",
    val steps: Int = 0,
    val halted: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val trace: List<TmTraceEntry> = emptyList(),
    val originalInputTape: String = ""
) {
    fun currentSymbol(): Char = tape[headPos] ?: BLANK_SYMBOL
}

data class TapeCell(
    val index: Int,
    val symbol: Char,
    val isHead: Boolean
)
