package com.example.uir_android.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicAccessPolicyTest {
    @Test
    fun `student and steward can view their own test results in profile`() {
        assertTrue(AcademicAccessPolicy.canViewOwnTestResults(AcademicRole.STUDENT))
        assertTrue(AcademicAccessPolicy.canViewOwnTestResults(AcademicRole.STEWARD))
        assertFalse(AcademicAccessPolicy.canViewOwnTestResults(AcademicRole.TEACHER))
        assertFalse(AcademicAccessPolicy.canViewOwnTestResults(AcademicRole.ADMIN))

        assertTrue(AcademicAccessPolicy.canViewOwnAcademicSummary(AcademicRole.STUDENT))
        assertTrue(AcademicAccessPolicy.canViewOwnAcademicSummary(AcademicRole.STEWARD))
        assertFalse(AcademicAccessPolicy.canViewOwnAcademicSummary(AcademicRole.TEACHER))
        assertFalse(AcademicAccessPolicy.canViewOwnAcademicSummary(AcademicRole.ADMIN))
    }

    @Test
    fun `server roles have centralized attendance permissions`() {
        assertFalse(AcademicAccessPolicy.canViewLectureAttendance(AcademicRole.STUDENT))
        assertFalse(AcademicAccessPolicy.canViewSeminarAttendance(AcademicRole.STUDENT))
        assertTrue(AcademicAccessPolicy.canViewLectureAttendance(AcademicRole.STEWARD))
        assertFalse(AcademicAccessPolicy.canViewSeminarAttendance(AcademicRole.STEWARD))
        assertTrue(AcademicAccessPolicy.canEditLectureAttendance(AcademicRole.LECTURER))
        assertFalse(AcademicAccessPolicy.canEditSeminarAttendance(AcademicRole.LECTURER))
        assertTrue(AcademicAccessPolicy.canEditSeminarAttendance(AcademicRole.SEMINARIST))
        assertTrue(AcademicAccessPolicy.canCorrectLectureStudents(AcademicRole.SEMINARIST))
        assertFalse(AcademicAccessPolicy.canSetLectureLimit(AcademicRole.SEMINARIST))
        assertTrue(AcademicAccessPolicy.canSetLectureLimit(AcademicRole.LECTURER))
        assertTrue(AcademicAccessPolicy.canEditLectureAttendance(AcademicRole.ADMIN))
        assertTrue(AcademicAccessPolicy.canEditSeminarAttendance(AcademicRole.ADMIN))
        assertFalse(AcademicAccessPolicy.canEditLectureAttendance(AcademicRole.STUDENT))
        assertTrue(AcademicAccessPolicy.hasLectureMatrix(AcademicRole.ADMIN))
        assertFalse(AcademicAccessPolicy.hasLectureMatrix(AcademicRole.TEACHER))
    }

    @Test
    fun `teacher can access only assigned groups`() {
        val teacher = CurrentUser(1, "Иван", "Иванов", "teacher@test.ru", 0, "Преподаватель")

        assertTrue(AcademicAccessPolicy.canAccessGroup(teacher, 10, setOf(10, 11)))
        assertFalse(AcademicAccessPolicy.canAccessGroup(teacher, 12, setOf(10, 11)))
    }

    @Test
    fun `steward can access only own group`() {
        val steward = CurrentUser(2, "Пётр", "Петров", "steward@test.ru", 7, "Староста")

        assertTrue(AcademicAccessPolicy.canAccessGroup(steward, 7, emptySet()))
        assertFalse(AcademicAccessPolicy.canAccessGroup(steward, 8, setOf(8)))
    }
}
