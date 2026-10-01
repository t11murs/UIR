package com.example.uir_android.domain.repository

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AcademicGroup
import com.example.uir_android.domain.model.AcademicProfile
import com.example.uir_android.domain.model.CurrentUser
import com.example.uir_android.domain.model.LectureAttendanceUpdate
import com.example.uir_android.domain.model.LectureLimitUpdate
import com.example.uir_android.domain.model.LectureLimitsMatrix
import com.example.uir_android.domain.model.LectureStatement
import com.example.uir_android.domain.model.NewsItem
import com.example.uir_android.domain.model.SeminarStatement
import com.example.uir_android.domain.model.SeminarAttendanceUpdate
import com.example.uir_android.domain.model.Student

interface AcademicRepository {
    suspend fun getCurrentUser(): AppResult<CurrentUser>

    suspend fun getGroups(): AppResult<List<AcademicGroup>>

    suspend fun getStudents(groupId: Int): AppResult<List<Student>>

    suspend fun getProfile(): AppResult<AcademicProfile>
}

interface AttendanceRepository {
    suspend fun getLectureLimits(): AppResult<LectureLimitsMatrix>

    suspend fun saveLectureLimits(updates: List<LectureLimitUpdate>): AppResult<Unit>

    suspend fun getStewardLectureStatement(): AppResult<LectureStatement>

    suspend fun getManagedLectureStatement(groupId: Int): AppResult<LectureStatement>

    suspend fun getSeminarStatement(groupId: Int): AppResult<SeminarStatement>

    suspend fun setLectureAttendances(updates: List<LectureAttendanceUpdate>): AppResult<Unit>

    suspend fun saveStewardLectureAttendance(
        lectureId: Int,
        studentIds: List<Int>
    ): AppResult<Unit>

    suspend fun saveSeminarAttendance(
        updates: List<SeminarAttendanceUpdate>
    ): AppResult<Unit>
}

interface NewsRepository {
    suspend fun getNews(): AppResult<List<NewsItem>>

    suspend fun getNewsDetail(newsId: String): AppResult<NewsItem>
}
