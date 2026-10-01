package com.example.uir_android.domain.model

import com.example.uir_android.core.common.AppErrorDetails

data class CurrentUser(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val groupId: Int,
    val role: String
) {
    val academicRole: AcademicRole = AcademicRole.fromServer(role)

    val displayName: String = listOf(lastName, firstName)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    val canEditLectureAttendance: Boolean =
        AcademicAccessPolicy.canEditLectureAttendance(academicRole)

    val canSetLectureLimit: Boolean =
        AcademicAccessPolicy.canSetLectureLimit(academicRole)

    val hasLectureMatrix: Boolean =
        AcademicAccessPolicy.hasLectureMatrix(academicRole)

    val canEditSeminarAttendance: Boolean =
        AcademicAccessPolicy.canEditSeminarAttendance(academicRole)

    val canEditStatements: Boolean =
        AcademicAccessPolicy.canEditAllStatements(academicRole)

    val isSteward: Boolean = AcademicAccessPolicy.canManageOwnLectureGroup(academicRole)
}

data class AcademicGroup(
    val id: Int,
    val name: String,
    val description: String = "",
    val coursePlanId: Int? = null,
    val archived: Boolean = false
)

data class ProfileTestResult(
    val testName: String,
    val score: String,
    val mark: String,
    val completedAt: String,
    val studentName: String = "",
    val groupName: String = ""
)

data class ProfileGradeSummary(
    val sectionsScore: String,
    val examScore: String,
    val totalScore: String,
    val mark: String
)

data class ProfileAttendance(
    val number: Int,
    val present: Boolean,
    val workPoints: String = ""
)

data class ProfileScore(
    val label: String,
    val value: String
)

data class ProfileSectionProgress(
    val number: Int,
    val lectures: List<ProfileAttendance>,
    val seminars: List<ProfileAttendance>,
    val scores: List<ProfileScore>
)

data class AcademicProfile(
    val user: CurrentUser,
    val groupName: String,
    val gradeSummary: ProfileGradeSummary?,
    val testResults: List<ProfileTestResult>,
    val sections: List<ProfileSectionProgress>,
    val warnings: List<String> = emptyList()
)

data class Student(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val groupId: Int,
    val role: String
) {
    val displayName: String = listOf(lastName, firstName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

data class LectureLimit(
    val lectureId: Int,
    val groupId: Int,
    val limit: Int?,
    val currentCount: Int
)

data class LectureLimitUpdate(
    val lectureId: Int,
    val groupId: Int,
    val limit: Int
)

data class LectureAttendanceUpdate(
    val studentId: Int,
    val lectureId: Int,
    val present: Boolean
)

data class LectureLimitsMatrix(
    val limits: List<LectureLimit>
)

data class LectureAttendanceCell(
    val lecturePlanId: Int?,
    val lectureId: Int?,
    val present: Boolean,
    val title: String = "",
    val limit: Int? = null
)

data class LectureAttendanceRow(
    val student: Student,
    val cells: List<LectureAttendanceCell>
)

data class LectureStatement(
    val groupId: Int,
    val coursePlanId: Int?,
    val rows: List<LectureAttendanceRow>
)

data class SeminarAttendanceCell(
    val seminarPassId: Int,
    val seminarPlanId: Int,
    val sectionNumber: Int,
    val present: Boolean,
    val workPoints: Double,
    val title: String = ""
)

data class SeminarAttendanceUpdate(
    val seminarPassId: Int,
    val coursePlanId: Int,
    val sectionNumber: Int,
    val present: Boolean,
    val presenceChanged: Boolean,
    val workPoints: Double,
    val pointsChanged: Boolean
)

data class SeminarPointsLimitError(
    val seminarPassId: Int,
    val maxWorkPoints: Double,
    val actualWorkPoints: Double?
) : AppErrorDetails

data class SeminarAttendanceRow(
    val student: Student,
    val cells: List<SeminarAttendanceCell>
)

data class SeminarStatement(
    val groupId: Int,
    val coursePlanId: Int?,
    val rows: List<SeminarAttendanceRow>
)

data class NewsAttachment(
    val title: String,
    val url: String
)

data class NewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val publishedAt: String?,
    val attachments: List<NewsAttachment>
)
