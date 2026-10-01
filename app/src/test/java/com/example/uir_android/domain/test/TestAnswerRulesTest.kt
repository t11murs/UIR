package com.example.uir_android.domain.test

import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionContentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAnswerRulesTest {
    @Test
    fun `single question requires one non blank answer`() {
        val question = TestQuestion(id = 1, typeCode = 1)

        assertFalse(question.isAnswered(emptyList()))
        assertFalse(question.isAnswered(listOf("")))
        assertTrue(question.isAnswered(listOf("2")))
    }

    @Test
    fun `table question requires an answer for every row`() {
        val question = TestQuestion(
            id = 2,
            typeCode = 4,
            text = listOf("Строка 1", "Строка 2"),
            variants = listOf("A", "B", "C")
        )

        assertFalse(question.isAnswered(listOf("1")))
        assertFalse(question.isAnswered(listOf("1", "2")))
        assertTrue(question.isAnswered(listOf("1", "4")))
        assertTrue(question.isAnswered(listOf("1", "3", "5", "6")))
    }

    @Test
    fun `table answers are deduplicated and sorted before submission`() {
        val question = TestQuestion(id = 2, typeCode = 4)

        assertEquals(
            listOf("1", "3", "6"),
            question.answersForSubmission(listOf("6", "1", "3", "1", ""))
        )
    }

    @Test
    fun `image-only table row keeps original server index`() {
        val question = TestQuestion(
            id = 2,
            typeCode = 4,
            variants = listOf("A", "B"),
            contentItems = listOf(
                TestQuestionContentItem(0, "text", text = "Строка 1"),
                TestQuestionContentItem(1, "image", imageUrl = "https://example.test/row.jpg"),
                TestQuestionContentItem(2, "text", text = "Строка 3")
            )
        )

        val rows = question.orderedContentRows()

        assertEquals(listOf(0, 1, 2), rows.map { it.index })
        assertEquals(listOf("https://example.test/row.jpg"), rows[1].imageUrls)
        assertFalse(question.isAnswered(listOf("1", "3")))
        assertTrue(question.isAnswered(listOf("1", "3", "5")))
    }

    @Test
    fun `image-only yes-no row keeps answer position`() {
        val question = TestQuestion(
            id = 5,
            typeCode = 5,
            contentItems = listOf(
                TestQuestionContentItem(0, "text", text = "Утверждение 1"),
                TestQuestionContentItem(1, "image", imageUrl = "https://example.test/statement.jpg"),
                TestQuestionContentItem(2, "text", text = "Утверждение 3")
            )
        )

        assertFalse(question.isAnswered(listOf("true", "", "false")))
        assertTrue(question.isAnswered(listOf("true", "false", "true")))
    }

    @Test
    fun `gap answers preserve empty positions before submission`() {
        val question = TestQuestion(id = 3, typeCode = 3)

        assertEquals(
            listOf("", "Второй", ""),
            question.answersForSubmission(listOf("", "Второй", ""))
        )
    }

    @Test
    fun `yes no answers preserve empty positions before submission`() {
        val question = TestQuestion(id = 5, typeCode = 5)

        assertEquals(
            listOf("", "false", "true"),
            question.answersForSubmission(listOf("", "false", "true"))
        )
    }

    @Test
    fun `three text answers preserve empty positions before submission`() {
        val question = TestQuestion(id = 9, typeCode = 9)

        assertEquals(
            listOf("Первый", "", "Третий"),
            question.answersForSubmission(listOf("Первый", "", "Третий"))
        )
    }

    @Test
    fun `counts all unanswered questions`() {
        val questions = listOf(
            TestQuestion(id = 1, typeCode = 1),
            TestQuestion(id = 2, typeCode = 1)
        )

        assertEquals(1, unansweredQuestionCount(questions, mapOf(1 to listOf("A"))))
    }
}
