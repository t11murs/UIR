package com.example.uir_android.ui.state

import com.example.uir_android.domain.model.AppSettings
import com.example.uir_android.domain.model.TapeCell
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import com.example.uir_android.domain.model.TmTraceEntry

enum class CommandSymbolFieldTarget {
    READ,
    WRITE
}

data class FocusedCommandSymbolField(
    val rowId: Long,
    val target: CommandSymbolFieldTarget
)

data class ProgramCommandRowUiState(
    val id: Long,
    val leftRuleText: String = "",
    val rightRuleText: String = ""
)

data class AuthUiState(
    val isReady: Boolean = false,
    val hasAccount: Boolean = false,
    val isLoggedIn: Boolean = false,
    val email: String = "",
    val isSubmitting: Boolean = false
)

data class TuringUiState(
    val programName: String = "",
    val programDescription: String = "",
    val programText: String = "",
    val programRows: List<ProgramCommandRowUiState> = emptyList(),
    val focusedSymbolField: FocusedCommandSymbolField? = null,
    val inputTape: String = "",
    val alphabetText: String = "ab∂",
    val tapeWindow: List<TapeCell> = emptyList(),
    val currentState: String = "S0",
    val steps: Int = 0,
    val halted: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val traceList: List<TmTraceEntry> = emptyList(),
    val availablePrograms: List<TmProgram> = emptyList(),
    val recentRuns: List<TmRun> = emptyList(),
    val selectedProgramId: Long? = null,
    val isRunning: Boolean = false,
    val showLoadDialog: Boolean = false,
    val windowOffset: Int = 0,
    val settings: AppSettings = AppSettings(
        maxRunSteps = 200,
        runDelayMs = 150L,
        debugEnabled = false
    ),
    val isInputTapeFocused: Boolean = false
)

data class SettingsUiState(
    val settings: AppSettings = AppSettings(
        maxRunSteps = 200,
        runDelayMs = 150L,
        debugEnabled = false
    ),
    val maxRunStepsInput: String = "200",
    val runDelayInput: String = "150",
    val isSaving: Boolean = false
)
