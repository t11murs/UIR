package com.example.uir_android.domain.model

enum class AcademicRole {
    STUDENT,
    STEWARD,
    SEMINARIST,
    LECTURER,
    TEACHER,
    ADMIN,
    UNKNOWN;

    companion object {
        fun fromServer(value: String): AcademicRole = when (value.trim().lowercase()) {
            "студент" -> STUDENT
            "староста" -> STEWARD
            "семинарист" -> SEMINARIST
            "лектор" -> LECTURER
            "преподаватель" -> TEACHER
            "админ", "администратор" -> ADMIN
            else -> UNKNOWN
        }
    }
}

object AcademicAccessPolicy {
    fun canViewOwnTestResults(role: AcademicRole): Boolean =
        role == AcademicRole.STUDENT || role == AcademicRole.STEWARD

    fun canViewOwnAcademicSummary(role: AcademicRole): Boolean =
        role == AcademicRole.STUDENT || role == AcademicRole.STEWARD

    fun canViewLectureAttendance(role: AcademicRole): Boolean =
        role == AcademicRole.STEWARD || canEditLectureAttendance(role)

    fun canViewSeminarAttendance(role: AcademicRole): Boolean =
        canEditSeminarAttendance(role)

    fun canEditLectureAttendance(role: AcademicRole): Boolean =
        role in setOf(
            AcademicRole.SEMINARIST,
            AcademicRole.LECTURER,
            AcademicRole.TEACHER,
            AcademicRole.ADMIN
        )

    fun canSetLectureLimit(role: AcademicRole): Boolean =
        role in setOf(AcademicRole.LECTURER, AcademicRole.TEACHER, AcademicRole.ADMIN)

    fun canCorrectLectureStudents(role: AcademicRole): Boolean =
        canEditLectureAttendance(role)

    fun hasLectureMatrix(role: AcademicRole): Boolean = role == AcademicRole.ADMIN

    fun canEditSeminarAttendance(role: AcademicRole): Boolean =
        role in setOf(AcademicRole.SEMINARIST, AcademicRole.TEACHER, AcademicRole.ADMIN)

    fun canEditAllStatements(role: AcademicRole): Boolean =
        role in setOf(AcademicRole.TEACHER, AcademicRole.ADMIN)

    fun canManageOwnLectureGroup(role: AcademicRole): Boolean = role == AcademicRole.STEWARD

    fun canAccessGroup(user: CurrentUser, groupId: Int, assignedGroupIds: Set<Int>): Boolean =
        when (user.academicRole) {
            AcademicRole.ADMIN -> groupId in assignedGroupIds
            AcademicRole.STEWARD, AcademicRole.STUDENT -> groupId == user.groupId
            AcademicRole.SEMINARIST, AcademicRole.LECTURER, AcademicRole.TEACHER ->
                groupId in assignedGroupIds
            AcademicRole.UNKNOWN -> false
        }
}
