package com.example.uir_android.ui.util

import com.example.uir_android.domain.model.TestQuestion
import org.junit.Assert.assertEquals
import org.junit.Test

class TestResultFormattingTest {
    @Test
    fun `table answers are formatted as row and column labels`() {
        val question = TestQuestion(
            typeCode = 4,
            text = listOf("Первая строка", "Вторая строка"),
            variants = listOf("A", "B")
        )

        assertEquals(
            "Первая строка — B\nВторая строка — A",
            question.formatReviewAnswers(listOf("2", "3"))
        )
    }

    @Test
    fun `yes no answers are localized and aligned with statements`() {
        val question = TestQuestion(
            typeCode = 5,
            text = listOf("Утверждение 1", "Утверждение 2")
        )

        assertEquals(
            "Утверждение 1 — Да\nУтверждение 2 — Нет",
            question.formatReviewAnswers(listOf("true", "false"))
        )
    }
}
