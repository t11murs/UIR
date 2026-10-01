package com.example.uir_android.domain.test

import com.example.uir_android.domain.model.TestQuestion

fun TestQuestion.isAnswered(values: List<String>): Boolean {
    val filled = values.count(String::isNotBlank)
    return when (typeCode) {
        3 -> filled >= variantGroups.size.coerceAtLeast(1)
        4 -> isTableAnswered(values)
        5 -> orderedContentRows().let { rows ->
            rows.isNotEmpty() && rows.all { row -> values.getOrNull(row.index).isNullOrBlank().not() }
        }
        9 -> filled >= 3
        else -> filled > 0
    }
}

private fun TestQuestion.isTableAnswered(values: List<String>): Boolean {
    val rows = orderedContentRows()
    if (rows.isEmpty() || variants.isEmpty()) return false
    val columnsCount = variants.size
    val selectedCells = values.mapNotNull(String::toIntOrNull).toSet()
    return rows.all { row ->
        val rowStart = row.index * columnsCount + 1
        selectedCells.any { it in rowStart until rowStart + columnsCount }
    }
}

fun TestQuestion.answersForSubmission(values: List<String>): List<String> {
    return when (typeCode) {
        3, 5, 9 -> values
        4 -> values
            .filter(String::isNotBlank)
            .distinct()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }
        else -> values.filter(String::isNotBlank)
    }
}

fun unansweredQuestionCount(
    questions: List<TestQuestion>,
    answers: Map<Int, List<String>>
): Int = questions.count { question ->
    !question.isAnswered(answers[question.id].orEmpty())
}
