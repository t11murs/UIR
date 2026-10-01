package com.example.uir_android.domain.test

import com.example.uir_android.domain.model.TestQuestion

data class TestQuestionContentRow(
    val index: Int,
    val text: String,
    val imageUrls: List<String>
)

fun TestQuestion.orderedContentRows(): List<TestQuestionContentRow> {
    if (contentItems.isEmpty()) {
        val textRows = text.mapIndexed { index, value ->
            TestQuestionContentRow(index = index, text = value, imageUrls = emptyList())
        }
        val imageRows = imageUrls.takeUnless { typeCode == 4 || typeCode == 5 }
            .orEmpty()
            .mapIndexed { index, imageUrl ->
            TestQuestionContentRow(
                index = textRows.size + index,
                text = "",
                imageUrls = listOf(imageUrl)
            )
        }
        return textRows + imageRows
    }

    return contentItems
        .groupBy { item -> item.index }
        .toSortedMap()
        .map { (index, items) ->
            TestQuestionContentRow(
                index = index,
                text = items.asSequence()
                    .map { item -> item.text.trim() }
                    .filter(String::isNotEmpty)
                    .distinct()
                    .joinToString("\n"),
                imageUrls = items.asSequence()
                    .map { item -> item.imageUrl.trim() }
                    .filter(String::isNotEmpty)
                    .distinct()
                    .toList()
            )
        }
}
