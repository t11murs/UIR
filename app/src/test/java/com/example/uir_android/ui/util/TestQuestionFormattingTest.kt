package com.example.uir_android.ui.util

import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionContentItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TestQuestionFormattingTest {
    @Test
    fun `display text removes repeated lines and generated question heading`() {
        val question = TestQuestion(
            count = 23,
            text = listOf(
                "Вопрос 23",
                "Что такое алгоритм?",
                "  что   такое алгоритм? "
            )
        )

        assertEquals("Что такое алгоритм?", question.displayText())
    }

    @Test
    fun `review keeps label for image-only yes-no row`() {
        val question = TestQuestion(
            typeCode = 5,
            contentItems = listOf(
                TestQuestionContentItem(
                    index = 0,
                    type = "image",
                    imageUrl = "https://example.test/statement.jpg"
                )
            )
        )

        assertEquals(
            "Изображение (строка 1) — Да",
            question.formatReviewAnswers(listOf("true"))
        )
    }
}
