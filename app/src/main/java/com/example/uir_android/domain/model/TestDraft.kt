package com.example.uir_android.domain.model

data class ActiveTestDraft(
    val ownerKey: String,
    val testId: Int,
    val runId: Int,
    val endsAt: String,
    val currentQuestionIndex: Int,
    val answers: Map<Int, List<String>>,
    val markedQuestionIds: Set<Int>,
    val updatedAt: Long
) {
    fun matches(runId: Int, endsAt: String): Boolean {
        return this.runId == runId && this.endsAt == endsAt
    }
}
