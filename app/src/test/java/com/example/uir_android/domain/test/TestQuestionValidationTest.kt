package com.example.uir_android.domain.test

import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionContentItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestQuestionValidationTest {
    @Test
    fun `positive unique question ids are valid`() {
        val result = validateTestQuestionIds(
            listOf(TestQuestion(id = 1), TestQuestion(id = 2))
        )

        assertTrue(result.isValid)
        assertTrue(result.nonPositivePositions.isEmpty())
        assertTrue(result.duplicateIds.isEmpty())
    }

    @Test
    fun `non-positive ids report one-based question positions`() {
        val result = validateTestQuestionIds(
            listOf(TestQuestion(id = 4), TestQuestion(id = 0), TestQuestion(id = -2))
        )

        assertFalse(result.isValid)
        assertEquals(listOf(2, 3), result.nonPositivePositions)
    }

    @Test
    fun `duplicate positive ids are reported once and sorted`() {
        val result = validateTestQuestionIds(
            listOf(
                TestQuestion(id = 8),
                TestQuestion(id = 3),
                TestQuestion(id = 8),
                TestQuestion(id = 3),
                TestQuestion(id = 3)
            )
        )

        assertFalse(result.isValid)
        assertEquals(listOf(3, 8), result.duplicateIds)
    }

    @Test
    fun `answer row indices must be contiguous from zero`() {
        val invalid = TestQuestion(
            id = 9,
            count = 4,
            typeCode = 5,
            contentItems = listOf(
                TestQuestionContentItem(0, "text", text = "Первая"),
                TestQuestionContentItem(2, "image", imageUrl = "https://example.test/third.jpg")
            )
        )

        val result = validateTestQuestionContent(listOf(invalid))

        assertFalse(result.isValid)
        assertEquals(listOf(4), result.invalidQuestionNumbers)
    }
}
