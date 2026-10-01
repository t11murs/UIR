package com.example.uir_android.domain.attendance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LectureAttendanceRulesTest {
    @Test
    fun `matrix validation reports invalid cells without creating updates for them`() {
        val result = buildLectureLimitUpdates(
            listOf(
                LectureLimitDraft(lectureId = 1, groupId = 10, value = "12"),
                LectureLimitDraft(lectureId = 2, groupId = 10, value = "")
            )
        )

        assertFalse(result.isValid)
        assertEquals(listOf(2), result.invalidDrafts.map { it.lectureId })
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun `attendance updates contain only changed students`() {
        val updates = buildLectureAttendanceUpdates(
            lectureId = 7,
            currentSelections = mapOf(1 to true, 2 to false, 3 to true),
            initiallySelectedStudentIds = setOf(1, 2)
        )

        assertEquals(setOf(2, 3), updates.mapTo(mutableSetOf()) { it.studentId })
        assertTrue(updates.first { it.studentId == 3 }.present)
        assertFalse(updates.first { it.studentId == 2 }.present)
    }
}
