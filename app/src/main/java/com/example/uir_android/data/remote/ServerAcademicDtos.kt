package com.example.uir_android.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerCurrentUserDto(
    val id: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val group: Int = 0,
    val role: String = ""
)

@Serializable
data class ServerAcademicGroupDto(
    @SerialName("group_id") val id: Int = 0,
    @SerialName("group_name") val name: String = "",
    val description: String = "",
    val archived: Int = 0,
    @SerialName("id_course_plan") val coursePlanId: Int? = null
)

@Serializable
data class ServerStudentDto(
    val id: Int = 0,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    val email: String = "",
    val group: Int = 0,
    val role: String = "",
    @SerialName("group_name") val groupName: String = ""
)

@Serializable
data class ServerLectureLimitDto(
    val lectureId: Int,
    val groupId: Int,
    val limit: Int?,
    val currentCount: Int
)

@Serializable
data class ServerLectureLimitsPageDto(
    val csrfToken: String = "",
    val limits: List<ServerLectureLimitDto>
)

@Serializable
data class ServerLecturePassDto(
    val lecturePlanId: Int?,
    val lectureId: Int?,
    val present: Boolean,
    val title: String = "",
    val limit: Int? = null
)

@Serializable
data class ServerLectureStatementRowDto(
    val student: ServerStudentDto,
    val passes: List<ServerLecturePassDto>
)

@Serializable
data class ServerLectureStatementDto(
    val groupId: Int,
    val coursePlanId: Int?,
    val rows: List<ServerLectureStatementRowDto>,
    val csrfToken: String = ""
)

@Serializable
data class ServerSeminarPassDto(
    val seminarPassId: Int,
    val seminarPlanId: Int,
    val sectionNumber: Int,
    val present: Boolean,
    val workPoints: Double,
    val title: String = ""
)

@Serializable
data class ServerSeminarAttendanceUpdateDto(
    val seminarPassId: Int,
    val coursePlanId: Int,
    val sectionNumber: Int,
    val present: Boolean,
    val presenceChanged: Boolean,
    val workPoints: Double,
    val pointsChanged: Boolean
)

@Serializable
data class ServerSeminarStatementRowDto(
    val student: ServerStudentDto,
    val passes: List<ServerSeminarPassDto>
)

@Serializable
data class ServerSeminarStatementDto(
    val groupId: Int,
    val coursePlanId: Int?,
    val rows: List<ServerSeminarStatementRowDto>,
    val csrfToken: String = ""
)

@Serializable
data class ServerNewsAttachmentDto(
    val title: String,
    val url: String
)

@Serializable
data class ServerNewsDto(
    val id: String,
    val title: String,
    val summary: String,
    val body: String,
    val publishedAt: String?,
    val attachments: List<ServerNewsAttachmentDto>
)

@Serializable
data class ServerProfileResultDto(
    val testName: String,
    val score: String,
    val mark: String,
    val completedAt: String,
    val studentName: String = "",
    val groupName: String = ""
)

@Serializable
data class ServerProfileSummaryDto(
    val sectionsScore: String,
    val examScore: String,
    val totalScore: String,
    val mark: String
)

@Serializable
data class ServerProfileAttendanceDto(
    val number: Int,
    val present: Boolean,
    val workPoints: String = ""
)

@Serializable
data class ServerProfileScoreDto(
    val label: String,
    val value: String
)

@Serializable
data class ServerProfileSectionDto(
    val number: Int,
    val lectures: List<ServerProfileAttendanceDto>,
    val seminars: List<ServerProfileAttendanceDto>,
    val scores: List<ServerProfileScoreDto>
)

@Serializable
data class ServerProfileDto(
    val user: ServerCurrentUserDto,
    val groupName: String,
    val summary: ServerProfileSummaryDto?,
    val results: List<ServerProfileResultDto>,
    val sections: List<ServerProfileSectionDto>,
    val warnings: List<String> = emptyList()
)

@Serializable
internal data class MobileAcademicGroupDto(
    val id: Int = 0,
    val name: String = ""
)

@Serializable
internal data class MobileAcademicGroupsEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val groups: List<MobileAcademicGroupDto> = emptyList()
)

@Serializable
internal data class AcademicGroupsV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val groups: List<ServerAcademicGroupDto>? = null
)

@Serializable
internal data class AcademicStudentsV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val students: List<ServerStudentDto>? = null
)

@Serializable
internal data class AcademicProfileV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val profile: ServerProfileDto? = null
)

@Serializable
internal data class LectureLimitsV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val limits: List<ServerLectureLimitDto>? = null
)

@Serializable
internal data class LectureStatementV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val statement: ServerLectureStatementDto? = null
)

@Serializable
internal data class SeminarStatementV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val statement: ServerSeminarStatementDto? = null
)

@Serializable
internal data class NewsV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null,
    val news: List<ServerNewsDto>? = null
)

@Serializable
internal data class AcademicBasicV1Envelope(
    val apiVersion: Int,
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class ServerLectureLimitUpdateDto(
    val lectureId: Int,
    val groupId: Int,
    val limit: Int
)

@Serializable
internal data class LectureLimitsUpdateV1Request(
    val operationId: String,
    val updates: List<ServerLectureLimitUpdateDto>
)

@Serializable
internal data class StewardLectureAttendanceV1Request(
    val operationId: String,
    val lectureId: Int,
    val studentIds: List<Int>
)

@Serializable
data class ServerLectureAttendanceUpdateDto(
    val studentId: Int,
    val lectureId: Int,
    val present: Boolean
)

@Serializable
internal data class CurrentUserEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val user: ServerCurrentUserDto? = null
)

@Serializable
internal data class AcademicBasicEnvelope(
    val success: Boolean? = null,
    val message: String? = null,
    val error: List<String> = emptyList()
)

@Serializable
internal data class LectureAttendanceBatchRequest(
    val operationId: String,
    val updates: List<ServerLectureAttendanceUpdateDto>
)

@Serializable
internal data class SeminarAttendanceBatchRequest(
    val operationId: String,
    val updates: List<ServerSeminarAttendanceUpdateDto>
)

@Serializable
internal data class AttendanceBatchEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val revision: String? = null,
    val errorCode: String? = null,
    val seminarPassId: Int? = null,
    val maxWorkPoints: Double? = null,
    val actualWorkPoints: Double? = null
)
