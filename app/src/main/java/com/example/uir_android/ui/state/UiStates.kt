package com.example.uir_android.ui.state

import com.example.uir_android.domain.model.AppSettings
import com.example.uir_android.domain.model.TapeCell
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import com.example.uir_android.domain.model.TmTraceEntry
import com.example.uir_android.domain.model.TestDetail
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.model.RegistrationGroup
import com.example.uir_android.domain.model.EmulatorControl
import com.example.uir_android.domain.model.EmulatorSubmitResult
import com.example.uir_android.domain.model.LocalStorageStatus

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
    val isSubmitting: Boolean = false,
    val registrationGroups: List<RegistrationGroup> = emptyList(),
    val isGroupsLoading: Boolean = false,
    val captchaVerified: Boolean = false,
    val storageStatus: LocalStorageStatus = LocalStorageStatus.HEALTHY,
    val storageMessage: String? = null
)

enum class ActiveAssessmentType {
    TEST,
    EMULATOR_CONTROL
}

data class ActiveAssessmentLock(
    val type: ActiveAssessmentType,
    val assessmentId: Int,
    val runId: Int
)

data class TestsUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val assessmentGuardReady: Boolean = false,
    val testsRequestSucceeded: Boolean = false,
    val controlsRequestSucceeded: Boolean = false,
    val tests: List<TestSummary> = emptyList(),
    val emulatorControls: List<TestSummary> = emptyList(),
    val localDraftTestIds: Set<Int> = emptySet(),
    val localDraftControlIds: Set<Int> = emptySet(),
    val activeAssessmentLock: ActiveAssessmentLock? = null,
    val completedResults: Map<Int, TestResult> = emptyMap(),
    val errorMessage: String? = null
)

enum class TestSessionStatus {
    LOADING,
    ACTIVE,
    SUBMITTING,
    FINISHED,
    EXPIRED,
    ERROR
}

data class TestDetailUiState(
    val status: TestSessionStatus = TestSessionStatus.LOADING,
    val test: TestDetail? = null,
    val currentQuestionIndex: Int = 0,
    val markedQuestionIds: Set<Int> = emptySet(),
    val answers: Map<Int, List<String>> = emptyMap(),
    val result: TestResult? = null,
    val errorMessage: String? = null,
    val remainingSeconds: Long? = null,
    val showIncompleteConfirmation: Boolean = false,
    val unansweredCount: Int = 0,
    val isReviewingResult: Boolean = false
) {
    val isLoading: Boolean get() = status == TestSessionStatus.LOADING
    val isSubmitting: Boolean get() = status == TestSessionStatus.SUBMITTING
    val canEdit: Boolean get() = status == TestSessionStatus.ACTIVE
}

data class EmulatorControlsUiState(
    val isLoading: Boolean = false,
    val controls: List<TestSummary> = emptyList(),
    val errorMessage: String? = null
)

enum class DraftSyncStatus {
    IDLE,
    LOCAL_SAVED,
    SYNCING,
    SYNCED,
    ERROR
}

data class EmulatorControlUiState(
    val isLoading: Boolean = true,
    val isSending: Boolean = false,
    val control: EmulatorControl? = null,
    val currentQuestionIndex: Int = 0,
    val programRows: List<ProgramCommandRowUiState> = emptyList(),
    val focusedSymbolField: FocusedCommandSymbolField? = null,
    val isInputTapeFocused: Boolean = false,
    val alphabetText: String = "∂",
    val inputTape: String = "∂",
    val tapeWindow: List<TapeCell> = emptyList(),
    val currentState: String = "S0",
    val steps: Int = 0,
    val halted: Boolean = false,
    val traceList: List<TmTraceEntry> = emptyList(),
    val windowOffset: Int = 0,
    val isRunning: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val draftSyncStatus: DraftSyncStatus = DraftSyncStatus.IDLE,
    val draftSyncError: String? = null,
    val result: EmulatorSubmitResult? = null,
    val remainingSeconds: Long = 0
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
