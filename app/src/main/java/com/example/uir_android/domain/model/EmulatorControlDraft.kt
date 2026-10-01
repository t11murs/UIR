package com.example.uir_android.domain.model

data class EmulatorCommandDraft(
    val leftRuleText: String,
    val rightRuleText: String
)

data class EmulatorQuestionDraft(
    val alphabetText: String,
    val inputTape: String,
    val commands: List<EmulatorCommandDraft>
)

data class EmulatorControlDraft(
    val ownerKey: String,
    val controlId: Int,
    val runId: Int,
    val endsAt: String,
    val currentQuestionIndex: Int,
    val questions: Map<Int, EmulatorQuestionDraft>,
    val updatedAt: Long
) {
    fun matches(runId: Int, endsAt: String): Boolean =
        this.runId == runId && this.endsAt == endsAt
}
