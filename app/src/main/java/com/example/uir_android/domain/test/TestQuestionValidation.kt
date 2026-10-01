package com.example.uir_android.domain.test

import com.example.uir_android.domain.model.TestQuestion

data class TestQuestionIdValidation(
    val nonPositivePositions: List<Int> = emptyList(),
    val duplicateIds: List<Int> = emptyList()
) {
    val isValid: Boolean
        get() = nonPositivePositions.isEmpty() && duplicateIds.isEmpty()
}

data class TestQuestionContentValidation(
    val invalidQuestionNumbers: List<Int> = emptyList()
) {
    val isValid: Boolean
        get() = invalidQuestionNumbers.isEmpty()
}

fun validateTestQuestionIds(questions: List<TestQuestion>): TestQuestionIdValidation {
    val nonPositivePositions = questions.mapIndexedNotNull { index, question ->
        (index + 1).takeIf { question.id <= 0 }
    }
    val duplicateIds = questions.asSequence()
        .map(TestQuestion::id)
        .filter { it > 0 }
        .groupingBy { it }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys
        .sorted()
    return TestQuestionIdValidation(
        nonPositivePositions = nonPositivePositions,
        duplicateIds = duplicateIds
    )
}

fun validateTestQuestionContent(
    questions: List<TestQuestion>
): TestQuestionContentValidation {
    val invalidQuestionNumbers = questions.mapNotNull { question ->
        if ((question.typeCode != 4 && question.typeCode != 5) || question.contentItems.isEmpty()) {
            return@mapNotNull null
        }
        val indices = question.contentItems.map { item -> item.index }.distinct().sorted()
        (question.count.takeIf { it > 0 } ?: question.id)
            .takeIf { indices != indices.indices.toList() }
    }
    return TestQuestionContentValidation(invalidQuestionNumbers)
}
