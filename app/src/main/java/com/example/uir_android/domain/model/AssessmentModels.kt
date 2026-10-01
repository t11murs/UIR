package com.example.uir_android.domain.model

data class TestSummary(
    val id: Int = 0,
    val name: String = "",
    val course: String = "",
    val type: String = "",
    val timeMinutes: Int = 0,
    val totalPoints: Double = 0.0,
    val maxPoints: Double = 0.0,
    val questionsCount: Int = 0,
    val attempts: Int = 0,
    val available: Boolean = false,
    val isAdaptive: Boolean = false,
    val hasCurrentRun: Boolean = false,
    val currentResultId: Int? = null,
    val startPath: String = "",
    val startUrl: String = "",
    val isTuringControl: Boolean = false,
    val latestResult: TestResult? = null
)

data class TestQuestion(
    val id: Int = 0,
    val count: Int = 0,
    val typeCode: Int = 0,
    val typeName: String = "",
    val text: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val textParts: List<String> = emptyList(),
    val variants: List<String> = emptyList(),
    val variantGroups: List<List<String>> = emptyList(),
    val supported: Boolean = false,
    val contentItems: List<TestQuestionContentItem> = emptyList()
)

data class TestQuestionContentItem(
    val index: Int,
    val type: String,
    val text: String = "",
    val imageUrl: String = ""
)

data class TestDetail(
    val id: Int = 0,
    val name: String = "",
    val type: String = "",
    val timeMinutes: Int = 0,
    val totalPoints: Double = 0.0,
    val runId: Int = 0,
    val endsAt: String = "",
    val remainingSeconds: Long? = null,
    val questions: List<TestQuestion> = emptyList()
)

data class TestAnswer(val questionId: Int, val values: List<String>)

data class TestResult(
    val runId: Int = 0,
    val score: Double = 0.0,
    val total: Double = 0.0,
    val markRu: String = "",
    val markEu: String = "",
    val finishedAt: String = "",
    val details: List<TestQuestionResult> = emptyList()
)

data class TestQuestionResult(
    val questionId: Int,
    val score: Double = 0.0,
    val points: Double = 0.0,
    val rightPercent: Int = 0,
    val answers: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList()
)

data class CompletedTestReview(val test: TestDetail, val result: TestResult)

data class TuringStateData(val state: String, val expressions: Map<String, String>)

data class TuringTaskData(
    val alphabet: List<String>,
    val automaton: List<TuringStateData>
)

data class TuringFees(
    val debugPercent: Int = 0,
    val syntaxPercent: Int = 0,
    val runPercent: Int = 0,
    val maxPercent: Int = 50
)

data class EmulatorQuestion(
    val id: Int,
    val count: Int,
    val text: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val points: Double = 0.0,
    val debugCounter: Int = 0,
    val syntaxCounter: Int = 0,
    val runCounter: Int = 0,
    val feePercent: Int = 0,
    val task: TuringTaskData
)

data class EmulatorControl(
    val id: Int,
    val name: String,
    val type: String = "",
    val timeMinutes: Int = 0,
    val totalPoints: Double = 0.0,
    val maxPoints: Double = 0.0,
    val questionsCount: Int = 0,
    val attempts: Int = 0,
    val available: Boolean = false,
    val hasCurrentRun: Boolean = false,
    val currentResultId: Int? = null,
    val runId: Int = 0,
    val endsAt: String = "",
    val remainingSeconds: Long? = null,
    val fees: TuringFees = TuringFees(),
    val questions: List<EmulatorQuestion> = emptyList()
)

data class EmulatorActionResult(
    val questionId: Int,
    val action: String,
    val syntaxValid: Boolean = false,
    val syntaxErrors: List<String> = emptyList(),
    val debugCounter: Int = 0,
    val syntaxCounter: Int = 0,
    val runCounter: Int = 0,
    val feePercent: Int = 0,
    val passed: Int? = null,
    val total: Int? = null,
    val rawRightPercent: Int? = null,
    val score: Double? = null
)

data class EmulatorResultDetail(
    val questionId: Int,
    val score: Double = 0.0,
    val points: Double = 0.0,
    val rightPercent: Int = 0,
    val passed: Int = 0,
    val totalSequences: Int = 0,
    val feePercent: Int = 0
)

data class EmulatorSubmitResult(
    val runId: Int,
    val score: Double = 0.0,
    val total: Double = 0.0,
    val markRu: String = "",
    val markEu: String = "",
    val finishedAt: String = "",
    val details: List<EmulatorResultDetail> = emptyList()
)

enum class EmulatorRunStatus {
    ACTIVE,
    COMPLETED,
    NOT_FOUND
}

data class EmulatorResultLookup(
    val name: String = "",
    val completed: Boolean,
    val result: EmulatorSubmitResult? = null,
    val status: EmulatorRunStatus = if (completed) {
        EmulatorRunStatus.COMPLETED
    } else {
        EmulatorRunStatus.ACTIVE
    },
    val activeRunId: Int? = null
)

data class EmulatorAnswer(val questionId: Int, val task: TuringTaskData)
