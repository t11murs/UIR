package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AcademicGroup
import com.example.uir_android.domain.model.CurrentUser
import com.example.uir_android.domain.model.LectureLimit
import com.example.uir_android.domain.model.LectureLimitUpdate
import com.example.uir_android.domain.model.LectureLimitsMatrix
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.domain.repository.AttendanceRepository
import com.example.uir_android.domain.usecase.ValidateLectureAttendanceSelectionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class LectureAttendanceViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid matrix save sends one request`() = runTest(dispatcher) {
        val academicRepository = mock(AcademicRepository::class.java)
        val attendanceRepository = mock(AttendanceRepository::class.java)
        `when`(academicRepository.getCurrentUser()).thenReturn(
            AppResult.Success(CurrentUser(1, "Админ", "", "admin@test.ru", 0, "Администратор"))
        )
        `when`(academicRepository.getGroups()).thenReturn(
            AppResult.Success(listOf(AcademicGroup(id = 10, name = "Б01-001")))
        )
        `when`(attendanceRepository.getLectureLimits()).thenReturn(
            AppResult.Success(
                LectureLimitsMatrix(listOf(LectureLimit(lectureId = 20, groupId = 10, limit = 1, currentCount = 1)))
            ),
            AppResult.Success(
                LectureLimitsMatrix(listOf(LectureLimit(lectureId = 20, groupId = 10, limit = 2, currentCount = 1)))
            )
        )
        `when`(attendanceRepository.saveLectureLimits(anyList<LectureLimitUpdate>())).thenReturn(
            AppResult.Success(Unit)
        )
        val viewModel = LectureAttendanceViewModel(
            savedStateHandle = SavedStateHandle(),
            academicRepository = academicRepository,
            attendanceRepository = attendanceRepository,
            validateSelection = ValidateLectureAttendanceSelectionUseCase()
        )
        advanceUntilIdle()

        viewModel.updateMatrixLimit(groupId = 10, lectureId = 20, value = "2")
        viewModel.saveMatrix()
        viewModel.saveMatrix()
        advanceUntilIdle()

        verify(attendanceRepository, times(1)).saveLectureLimits(anyList<LectureLimitUpdate>())
    }
}
