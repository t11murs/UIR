package com.example.uir_android.data.repository.impl

import com.example.uir_android.data.remote.ServerAcademicGroupDto
import com.example.uir_android.data.remote.AcademicRemoteDataSource
import com.example.uir_android.data.remote.AttendanceRemoteDataSource
import com.example.uir_android.data.remote.NewsRemoteDataSource
import com.example.uir_android.data.remote.ServerCurrentUserDto
import com.example.uir_android.data.remote.ServerLectureLimitDto
import com.example.uir_android.data.remote.ServerLectureLimitUpdateDto
import com.example.uir_android.data.remote.ServerLectureAttendanceUpdateDto
import com.example.uir_android.data.remote.ServerLecturePassDto
import com.example.uir_android.data.remote.ServerLectureStatementDto
import com.example.uir_android.data.remote.ServerNewsAttachmentDto
import com.example.uir_android.data.remote.ServerNewsDto
import com.example.uir_android.data.remote.ServerProfileDto
import com.example.uir_android.data.remote.ServerSeminarPassDto
import com.example.uir_android.data.remote.ServerSeminarAttendanceUpdateDto
import com.example.uir_android.data.remote.ServerSeminarStatementDto
import com.example.uir_android.data.remote.ServerStudentDto
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AcademicGroup
import com.example.uir_android.domain.model.AcademicProfile
import com.example.uir_android.domain.model.CurrentUser
import com.example.uir_android.domain.model.LectureAttendanceCell
import com.example.uir_android.domain.model.LectureAttendanceRow
import com.example.uir_android.domain.model.LectureAttendanceUpdate
import com.example.uir_android.domain.model.LectureLimit
import com.example.uir_android.domain.model.LectureLimitUpdate
import com.example.uir_android.domain.model.LectureLimitsMatrix
import com.example.uir_android.domain.model.LectureStatement
import com.example.uir_android.domain.model.NewsAttachment
import com.example.uir_android.domain.model.NewsItem
import com.example.uir_android.domain.model.ProfileGradeSummary
import com.example.uir_android.domain.model.ProfileAttendance
import com.example.uir_android.domain.model.ProfileScore
import com.example.uir_android.domain.model.ProfileSectionProgress
import com.example.uir_android.domain.model.ProfileTestResult
import com.example.uir_android.domain.model.SeminarAttendanceCell
import com.example.uir_android.domain.model.SeminarAttendanceRow
import com.example.uir_android.domain.model.SeminarAttendanceUpdate
import com.example.uir_android.domain.model.SeminarStatement
import com.example.uir_android.domain.model.Student
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.domain.repository.AttendanceRepository
import com.example.uir_android.domain.repository.NewsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AcademicRepositoryImpl @Inject constructor(
    private val service: AcademicRemoteDataSource
) : AcademicRepository {
    override suspend fun getCurrentUser(): AppResult<CurrentUser> {
        return service.loadCurrentUser().mapData { it.toDomain() }
    }

    override suspend fun getGroups(): AppResult<List<AcademicGroup>> {
        return service.loadGroups().mapData { groups ->
            groups.map { it.toDomain() }
        }
    }

    override suspend fun getStudents(groupId: Int): AppResult<List<Student>> {
        return service.loadStudents(groupId).mapData { students ->
            students.map { it.toDomain(fallbackGroupId = groupId) }
        }
    }

    override suspend fun getProfile(): AppResult<AcademicProfile> {
        return service.loadProfile().mapData { it.toDomain() }
    }
}

@Singleton
class AttendanceRepositoryImpl @Inject constructor(
    private val service: AttendanceRemoteDataSource
) : AttendanceRepository {
    override suspend fun getLectureLimits(): AppResult<LectureLimitsMatrix> {
        return service.loadLectureLimits().mapData { page ->
            LectureLimitsMatrix(limits = page.limits.map { it.toDomain() })
        }
    }

    override suspend fun saveLectureLimits(updates: List<LectureLimitUpdate>): AppResult<Unit> {
        return service.saveLectureLimits(updates.map { it.toDto() })
    }

    override suspend fun getStewardLectureStatement(): AppResult<LectureStatement> {
        return service.loadStewardLectureStatement().mapData { it.toDomain() }
    }

    override suspend fun getManagedLectureStatement(groupId: Int): AppResult<LectureStatement> {
        return service.loadManagedLectureStatement(groupId).mapData { it.toDomain() }
    }

    override suspend fun getSeminarStatement(groupId: Int): AppResult<SeminarStatement> {
        return service.loadSeminarStatement(groupId).mapData { it.toDomain() }
    }

    override suspend fun setLectureAttendances(
        updates: List<LectureAttendanceUpdate>
    ): AppResult<Unit> {
        return service.setLectureAttendances(
            updates.map { update ->
                ServerLectureAttendanceUpdateDto(
                    studentId = update.studentId,
                    lectureId = update.lectureId,
                    present = update.present
                )
            }
        )
    }

    override suspend fun saveStewardLectureAttendance(
        lectureId: Int,
        studentIds: List<Int>
    ): AppResult<Unit> {
        return service.saveStewardLectureAttendance(
            lectureId = lectureId,
            studentIds = studentIds
        )
    }

    override suspend fun saveSeminarAttendance(
        updates: List<SeminarAttendanceUpdate>
    ): AppResult<Unit> {
        return service.saveSeminarAttendance(
            updates.map { update ->
                ServerSeminarAttendanceUpdateDto(
                    seminarPassId = update.seminarPassId,
                    coursePlanId = update.coursePlanId,
                    sectionNumber = update.sectionNumber,
                    present = update.present,
                    presenceChanged = update.presenceChanged,
                    workPoints = update.workPoints,
                    pointsChanged = update.pointsChanged
                )
            }
        )
    }
}

@Singleton
class NewsRepositoryImpl @Inject constructor(
    private val service: NewsRemoteDataSource
) : NewsRepository {
    @Volatile
    private var cachedNews: List<NewsItem> = emptyList()

    override suspend fun getNews(): AppResult<List<NewsItem>> {
        val result = service.loadNews().mapData { news ->
            news.map { it.toDomain() }
        }
        if (result is AppResult.Success) {
            cachedNews = result.data
        }
        return result
    }

    override suspend fun getNewsDetail(newsId: String): AppResult<NewsItem> {
        cachedNews.firstOrNull { it.id == newsId }?.let { item ->
            return AppResult.Success(item)
        }
        return when (val result = getNews()) {
            is AppResult.Error -> result
            is AppResult.Success -> {
                val item = result.data.firstOrNull { it.id == newsId }
                if (item == null) {
                    AppResult.Error("Новость не найдена")
                } else {
                    AppResult.Success(item)
                }
            }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AcademicRepositoryModule {
    @Binds
    abstract fun bindAcademicRepository(impl: AcademicRepositoryImpl): AcademicRepository

    @Binds
    abstract fun bindAttendanceRepository(impl: AttendanceRepositoryImpl): AttendanceRepository

    @Binds
    abstract fun bindNewsRepository(impl: NewsRepositoryImpl): NewsRepository

}

private inline fun <T, R> AppResult<T>.mapData(transform: (T) -> R): AppResult<R> {
    return when (this) {
        is AppResult.Error -> this
        is AppResult.Success -> AppResult.Success(transform(data), message)
    }
}

private fun ServerCurrentUserDto.toDomain(): CurrentUser = CurrentUser(
    id = id,
    firstName = firstName,
    lastName = lastName,
    email = email,
    groupId = group,
    role = role
)

private fun ServerAcademicGroupDto.toDomain(): AcademicGroup = AcademicGroup(
    id = id,
    name = name,
    description = description,
    coursePlanId = coursePlanId,
    archived = archived != 0
)

private fun ServerStudentDto.toDomain(fallbackGroupId: Int? = null): Student = Student(
    id = id,
    firstName = firstName,
    lastName = lastName,
    email = email,
    groupId = group.takeIf { it != 0 } ?: fallbackGroupId ?: 0,
    role = role
)

private fun ServerLectureLimitDto.toDomain(): LectureLimit = LectureLimit(
    lectureId = lectureId,
    groupId = groupId,
    limit = limit,
    currentCount = currentCount
)

private fun LectureLimitUpdate.toDto(): ServerLectureLimitUpdateDto = ServerLectureLimitUpdateDto(
    lectureId = lectureId,
    groupId = groupId,
    limit = limit
)

private fun ServerLectureStatementDto.toDomain(): LectureStatement = LectureStatement(
    groupId = groupId,
    coursePlanId = coursePlanId,
    rows = rows.map { row ->
        LectureAttendanceRow(
            student = row.student.toDomain(fallbackGroupId = groupId),
            cells = row.passes.map { it.toDomain() }
        )
    }
)

private fun ServerLecturePassDto.toDomain(): LectureAttendanceCell = LectureAttendanceCell(
    lecturePlanId = lecturePlanId,
    lectureId = lectureId,
    present = present,
    title = title,
    limit = limit
)

private fun ServerSeminarStatementDto.toDomain(): SeminarStatement = SeminarStatement(
    groupId = groupId,
    coursePlanId = coursePlanId,
    rows = rows.map { row ->
        SeminarAttendanceRow(
            student = row.student.toDomain(fallbackGroupId = groupId),
            cells = row.passes.map { it.toDomain() }
        )
    }
)

private fun ServerSeminarPassDto.toDomain(): SeminarAttendanceCell = SeminarAttendanceCell(
    seminarPassId = seminarPassId,
    seminarPlanId = seminarPlanId,
    sectionNumber = sectionNumber,
    present = present,
    workPoints = workPoints,
    title = title
)

private fun ServerNewsDto.toDomain(): NewsItem = NewsItem(
    id = id,
    title = title,
    summary = summary,
    body = body,
    publishedAt = publishedAt,
    attachments = attachments.map { it.toDomain() }
)

private fun ServerNewsAttachmentDto.toDomain(): NewsAttachment = NewsAttachment(
    title = title,
    url = url
)

private fun ServerProfileDto.toDomain(): AcademicProfile = AcademicProfile(
    user = user.toDomain(),
    groupName = groupName,
    gradeSummary = summary?.let {
        ProfileGradeSummary(
            sectionsScore = it.sectionsScore,
            examScore = it.examScore,
            totalScore = it.totalScore,
            mark = it.mark
        )
    },
    testResults = results.map {
        ProfileTestResult(
            testName = it.testName,
            score = it.score,
            mark = it.mark,
            completedAt = it.completedAt,
            studentName = it.studentName,
            groupName = it.groupName
        )
    },
    sections = sections.map { section ->
        ProfileSectionProgress(
            number = section.number,
            lectures = section.lectures.map {
                ProfileAttendance(it.number, it.present, it.workPoints)
            },
            seminars = section.seminars.map {
                ProfileAttendance(it.number, it.present, it.workPoints)
            },
            scores = section.scores.map { ProfileScore(it.label, it.value) }
        )
    },
    warnings = warnings
)
