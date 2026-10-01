package com.example.uir_android.ui.util

import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.test.TestQuestionContentRow
import com.example.uir_android.domain.test.orderedContentRows

private val questionHeadingPattern = Regex(
    pattern = "^(?:вопрос|question)\\s*(?:№|#)?\\s*\\d+\\s*[:.]?$",
    option = RegexOption.IGNORE_CASE
)

fun TestQuestion.displayText(): String {
    if (typeCode == 3 && textParts.isNotEmpty()) {
        return textParts.joinToString(" ___ ")
    }
    return text
        .map(String::trim)
        .filter(String::isNotEmpty)
        .filterNot(::isQuestionHeadingText)
        .distinctBy { it.lowercase().replace(Regex("\\s+"), " ") }
        .joinToString("\n")
}

fun isQuestionHeadingText(value: String): Boolean = value.trim().matches(questionHeadingPattern)

fun TestQuestion.formatReviewAnswers(values: List<String>): String {
    if (values.isEmpty()) return ""

    return when (typeCode) {
        2 -> values.joinToString(", ")
        3 -> values.mapIndexed { index, value ->
            "Пропуск ${index + 1}: $value"
        }.joinToString("\n")
        4 -> values.map { value ->
            val cell = value.toIntOrNull()?.minus(1)
            if (cell == null || cell < 0 || variants.isEmpty()) {
                value
            } else {
                val rowIndex = cell / variants.size
                val columnIndex = cell % variants.size
                val row = orderedContentRows().firstOrNull { it.index == rowIndex }
                val column = variants.getOrNull(columnIndex)
                if (row != null && column != null) "${row.displayLabel()} — $column" else value
            }
        }.joinToString("\n")
        5 -> values.mapIndexed { index, value ->
            val answer = when (value.lowercase()) {
                "true" -> "Да"
                "false" -> "Нет"
                else -> value
            }
            orderedContentRows().firstOrNull { it.index == index }
                ?.let { "${it.displayLabel()} — $answer" }
                ?: answer
        }.joinToString("\n")
        8 -> values.joinToString(" или ")
        9 -> values.mapIndexed { index, value ->
            "Ответ ${index + 1}: $value"
        }.joinToString("\n")
        else -> values.joinToString(", ")
    }
}

private fun TestQuestionContentRow.displayLabel(): String = when {
    text.isNotBlank() -> text
    imageUrls.isNotEmpty() -> "Изображение (строка ${index + 1})"
    else -> "Строка ${index + 1}"
}
