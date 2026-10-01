package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AcademicGroup
import com.example.uir_android.domain.model.CurrentUser
import com.example.uir_android.domain.model.SeminarAttendanceCell
import com.example.uir_android.domain.model.SeminarAttendanceRow
import com.example.uir_android.domain.model.SeminarAttendanceUpdate
import com.example.uir_android.domain.model.SeminarStatement
import com.example.uir_android.domain.model.SeminarPointsLimitError
import com.example.uir_android.domain.model.Student
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.domain.repository.AttendanceRepository
import com.example.uir_android.domain.usecase.ValidateSeminarPointsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class SeminarAttendanceViewModelTest {
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
    fun `partial save error reloads authoritative server state`() = runTest(dispatcher) {
        val academicRepository = mock(AcademicRepository::class.java)
        val attendanceRepository = mock(AttendanceRepository::class.java)
        `when`(academicRepository.getCurrentUser()).thenReturn(
            AppResult.Success(
                CurrentUser(1, "Иван", "Иванов", "teacher@test.ru", 10, "Семинарист")
            )
        )
        `when`(academicRepository.getGroups()).thenReturn(
            AppResult.Success(listOf(AcademicGroup(id = 10, name = "Б01-001")))
        )
        `when`(attendanceRepository.getSeminarStatement(10)).thenReturn(
            AppResult.Success(statement(present = false)),
            AppResult.Success(statement(present = true))
        )
        `when`(attendanceRepository.saveSeminarAttendance(anyList<SeminarAttendanceUpdate>())).thenReturn(
            AppResult.Error("Часть данных сохранена: 1 из 2 операций")
        )
        val viewModel = SeminarAttendanceViewModel(
            savedStateHandle = SavedStateHandle(),
            academicRepository = academicRepository,
            attendanceRepository = attendanceRepository,
            validatePoints = ValidateSeminarPointsUseCase()
        )
        advanceUntilIdle()

        viewModel.togglePresence(studentId = 2)
        viewModel.requestSave()
        viewModel.confirmSave()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.students.single().present)
        assertEquals(1, viewModel.state.value.presentCount)
        assertEquals(
            "Часть данных сохранена: 1 из 2 операций",
            viewModel.state.value.errorMessage
        )
    }

    @Test
    fun `rapid confirmation sends seminar attendance once`() = runTest(dispatcher) {
        val academicRepository = mock(AcademicRepository::class.java)
        val attendanceRepository = mock(AttendanceRepository::class.java)
        `when`(academicRepository.getCurrentUser()).thenReturn(
            AppResult.Success(
                CurrentUser(1, "Иван", "Иванов", "teacher@test.ru", 10, "Семинарист")
            )
        )
        `when`(academicRepository.getGroups()).thenReturn(
            AppResult.Success(listOf(AcademicGroup(id = 10, name = "Б01-001")))
        )
        `when`(attendanceRepository.getSeminarStatement(10)).thenReturn(
            AppResult.Success(statement(present = false)),
            AppResult.Success(statement(present = true))
        )
        `when`(attendanceRepository.saveSeminarAttendance(anyList<SeminarAttendanceUpdate>())).thenReturn(
            AppResult.Success(Unit)
        )
        val viewModel = SeminarAttendanceViewModel(
            savedStateHandle = SavedStateHandle(),
            academicRepository = academicRepository,
            attendanceRepository = attendanceRepository,
            validatePoints = ValidateSeminarPointsUseCase()
        )
        advanceUntilIdle()

        viewModel.togglePresence(studentId = 2)
        viewModel.requestSave()
        viewModel.confirmSave()
        viewModel.confirmSave()
        advanceUntilIdle()

        verify(attendanceRepository, times(1)).saveSeminarAttendance(
            anyList<SeminarAttendanceUpdate>()
        )
    }

    @Test
    fun `server points limit marks exact student and preserves local edits`() = runTest(dispatcher) {
        val academicRepository = mock(AcademicRepository::class.java)
        val attendanceRepository = mock(AttendanceRepository::class.java)
        `when`(academicRepository.getCurrentUser()).thenReturn(
            AppResult.Success(
                CurrentUser(1, "Иван", "Иванов", "teacher@test.ru", 10, "Семинарист")
            )
        )
        `when`(academicRepository.getGroups()).thenReturn(
            AppResult.Success(listOf(AcademicGroup(id = 10, name = "Б01-001")))
        )
        `when`(attendanceRepository.getSeminarStatement(10)).thenReturn(
            AppResult.Success(statement(present = false))
        )
        `when`(attendanceRepository.saveSeminarAttendance(anyList<SeminarAttendanceUpdate>())).thenReturn(
            AppResult.Error(
                message = "Баллы превышают максимум раздела: 5",
                details = SeminarPointsLimitError(
                    seminarPassId = 40,
                    maxWorkPoints = 5.0,
                    actualWorkPoints = 8.0
                )
            )
        )
        val viewModel = SeminarAttendanceViewModel(
            savedStateHandle = SavedStateHandle(),
            academicRepository = academicRepository,
            attendanceRepository = attendanceRepository,
            validatePoints = ValidateSeminarPointsUseCase()
        )
        advanceUntilIdle()

        viewModel.togglePresence(studentId = 2)
        viewModel.updatePoints(studentId = 2, input = "8")
        viewModel.requestSave()
        viewModel.confirmSave()
        advanceUntilIdle()

        val rejected = viewModel.state.value.students.single()
        assertTrue(rejected.present)
        assertEquals("8", rejected.pointsInput)
        assertEquals("Максимум раздела: 5", rejected.pointsError)
        assertTrue(!viewModel.state.value.canSave)
        verify(attendanceRepository, times(1)).getSeminarStatement(10)

        viewModel.updatePoints(studentId = 2, input = "4")

        val corrected = viewModel.state.value.students.single()
        assertEquals(null, corrected.pointsError)
        assertTrue(viewModel.state.value.canSave)
    }

    private fun statement(present: Boolean) = SeminarStatement(
        groupId = 10,
        coursePlanId = 30,
        rows = listOf(
            SeminarAttendanceRow(
                student = Student(2, "Пётр", "Петров", "student@test.ru", 10, "Студент"),
                cells = listOf(
                    SeminarAttendanceCell(
                        seminarPassId = 40,
                        seminarPlanId = 50,
                        sectionNumber = 1,
                        present = present,
                        workPoints = 0.0,
                        title = "Семинар 1"
                    )
                )
            )
        )
    )
}
