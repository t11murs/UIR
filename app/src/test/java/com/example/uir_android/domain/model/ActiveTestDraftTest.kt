package com.example.uir_android.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTestDraftTest {
    private val draft = ActiveTestDraft(
        ownerKey = "student@example.org",
        testId = 10,
        runId = 20,
        endsAt = "2026-09-07 15:00:00",
        currentQuestionIndex = 2,
        answers = mapOf(1 to listOf("A")),
        markedQuestionIds = setOf(3),
        updatedAt = 1L
    )

    @Test
    fun `draft matches only the same server run and deadline`() {
        assertTrue(draft.matches(20, "2026-09-07 15:00:00"))
        assertFalse(draft.matches(21, "2026-09-07 15:00:00"))
        assertFalse(draft.matches(20, "2026-09-07 16:00:00"))
    }
}
