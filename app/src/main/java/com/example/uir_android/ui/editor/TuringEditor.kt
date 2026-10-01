package com.example.uir_android.ui.editor

import com.example.uir_android.domain.model.TapeCell
import com.example.uir_android.domain.model.TmExecutionState
import com.example.uir_android.domain.turing.BLANK_INPUT_TOKEN
import com.example.uir_android.domain.turing.BLANK_SYMBOL
import com.example.uir_android.domain.turing.DEFAULT_TAPE_RADIUS
import com.example.uir_android.domain.turing.OMEGA_INPUT_TOKEN
import com.example.uir_android.domain.turing.PARTIAL_INPUT_TOKEN
import com.example.uir_android.ui.state.ProgramCommandRowUiState

object TuringEditor {
    private const val DISPLAY_BLANK_SYMBOL = "λ"
    private const val DISPLAY_OMEGA_SYMBOL = "Ω"
    private const val DISPLAY_PARTIAL_SYMBOL = "∂"

    fun displaySymbols(value: String): String = value
        .replace(BLANK_INPUT_TOKEN, DISPLAY_BLANK_SYMBOL, ignoreCase = true)
        .replace(OMEGA_INPUT_TOKEN, DISPLAY_OMEGA_SYMBOL, ignoreCase = true)
        .replace(PARTIAL_INPUT_TOKEN, DISPLAY_PARTIAL_SYMBOL, ignoreCase = true)

    fun parserTokens(value: String): String = value
        .replace(DISPLAY_BLANK_SYMBOL, BLANK_INPUT_TOKEN)
        .replace(DISPLAY_OMEGA_SYMBOL, OMEGA_INPUT_TOKEN)
        .replace(DISPLAY_PARTIAL_SYMBOL, PARTIAL_INPUT_TOKEN)

    fun newRow(
        id: Long,
        leftRuleText: String = "",
        rightRuleText: String = ""
    ): ProgramCommandRowUiState = ProgramCommandRowUiState(
        id = id,
        leftRuleText = leftRuleText,
        rightRuleText = rightRuleText
    )

    fun updateRow(
        rows: List<ProgramCommandRowUiState>,
        rowId: Long,
        transform: (ProgramCommandRowUiState) -> ProgramCommandRowUiState
    ): List<ProgramCommandRowUiState> = rows.map { row ->
        if (row.id == rowId) transform(row) else row
    }

    fun moveRow(
        rows: List<ProgramCommandRowUiState>,
        rowId: Long,
        delta: Int
    ): List<ProgramCommandRowUiState> {
        val index = rows.indexOfFirst { it.id == rowId }
        if (index < 0 || rows.isEmpty()) return rows
        val targetIndex = (index + delta).coerceIn(rows.indices)
        if (targetIndex == index) return rows
        return rows.toMutableList().apply {
            add(targetIndex, removeAt(index))
        }
    }

    fun buildTapeWindow(
        state: TmExecutionState,
        offset: Int,
        radius: Int = DEFAULT_TAPE_RADIUS
    ): List<TapeCell> {
        val visibleCount = radius * 2 + 1
        val center = state.headPos + offset
        val start = if (center in 0..radius) 0 else center - radius
        return (start until start + visibleCount).map { index ->
            TapeCell(
                index = index,
                symbol = state.tape[index] ?: BLANK_SYMBOL,
                isHead = index == state.headPos
            )
        }
    }

    fun splitCommandPart(value: String): List<String> =
        value.trim().split(Regex("\\s+")).filter(String::isNotBlank)

    fun normalizeStateAlias(rawState: String): String = when (rawState.trim().uppercase()) {
        DISPLAY_OMEGA_SYMBOL, "\\O", "HALT" -> "HALT"
        else -> rawState.trim()
    }

}
