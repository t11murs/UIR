package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.SingleFlightGate
import com.example.uir_android.domain.model.SeminarAttendanceUpdate
import com.example.uir_android.domain.model.SeminarStatement
import com.example.uir_android.domain.model.AcademicAccessPolicy
import com.example.uir_android.domain.model.SeminarPointsLimitError
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.domain.repository.AttendanceRepository
import com.example.uir_android.domain.usecase.SeminarPointsValidation
import com.example.uir_android.domain.usecase.ValidateSeminarPointsUseCase
import com.example.uir_android.ui.state.SeminarAttendanceMode
import com.example.uir_android.ui.state.SeminarAttendanceUiState
import com.example.uir_android.ui.state.SeminarGroupUiState
import com.example.uir_android.ui.state.SeminarOptionUiState
import com.example.uir_android.ui.state.SeminarStudentUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SeminarAttendanceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val academicRepository: AcademicRepository,
    private val attendanceRepository: AttendanceRepository,
    private val validatePoints: ValidateSeminarPointsUseCase
) : EventViewModel() {
    private data class InitialStudentState(
        val present: Boolean,
        val points: Double
    )

    private val _state = MutableStateFlow(SeminarAttendanceUiState())
    val state = _state.asStateFlow()

    private var loadedStatement: SeminarStatement? = null
    private var initialStates: Map<Int, InitialStudentState> = emptyMap()
    private var serverPointsLimits: Map<Int, Double> = emptyMap()
    private var loadJob: Job? = null
    private val saveGate = SingleFlightGate()

    init {
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val preferredGroupId = _state.value.selectedGroupId
                ?: savedStateHandle[SELECTED_GROUP_ID_KEY]
            val preferredSeminarId = _state.value.selectedSeminarId
                ?: savedStateHandle[SELECTED_SEMINAR_ID_KEY]
            _state.update {
                it.copy(
                    isLoading = true,
                    mode = SeminarAttendanceMode.LOADING,
                    errorMessage = null,
                )
            }
            when (val userResult = academicRepository.getCurrentUser()) {
                is AppResult.Error -> showLoadError(userResult.message)
                is AppResult.Success -> {
                    val user = userResult.data
                    if (!user.canEditSeminarAttendance) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                mode = SeminarAttendanceMode.DENIED,
                                userName = user.displayName,
                                role = user.role,
                                errorMessage = "Редактирование семинаров доступно только преподавателю или администратору"
                            )
                        }
                        return@launch
                    }
                    when (val groupsResult = academicRepository.getGroups()) {
                        is AppResult.Error -> showLoadError(groupsResult.message)
                        is AppResult.Success -> {
                            val groups = groupsResult.data
                                .filterNot { it.archived }
                                .filter { group ->
                                    AcademicAccessPolicy.canAccessGroup(
                                        user = user,
                                        groupId = group.id,
                                        assignedGroupIds = groupsResult.data.mapTo(hashSetOf()) { it.id }
                                    )
                                }
                                .map { SeminarGroupUiState(it.id, it.name) }
                            val groupId = preferredGroupId
                                ?.takeIf { candidate -> groups.any { it.id == candidate } }
                                ?: groups.firstOrNull()?.id
                            _state.update {
                                it.copy(
                                    mode = SeminarAttendanceMode.EDITOR,
                                    userName = user.displayName,
                                    role = user.role,
                                    groups = groups,
                                    selectedGroupId = groupId
                                )
                            }
                            if (groupId == null) {
                                showLoadError("На сервере нет доступных групп")
                            } else {
                                loadStatement(groupId, preferredSeminarId)
                            }
                        }
                    }
                }
            }
        }
    }

    fun selectGroup(groupId: Int) {
        val current = _state.value
        if (current.mode != SeminarAttendanceMode.EDITOR ||
            current.isSaving ||
            current.selectedGroupId == groupId
        ) {
            return
        }
        savedStateHandle[SELECTED_GROUP_ID_KEY] = groupId
        savedStateHandle[SELECTED_SEMINAR_ID_KEY] = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    selectedGroupId = groupId,
                    errorMessage = null,
                )
            }
            loadStatement(groupId, preferredSeminarId = null)
        }
    }

    fun selectSeminar(seminarId: Int) {
        if (_state.value.isSaving || _state.value.selectedSeminarId == seminarId) return
        applySelectedSeminar(seminarId)
    }

    fun togglePresence(studentId: Int) {
        if (_state.value.isSaving) return
        val students = _state.value.students.map { student ->
            if (student.id != studentId) {
                student
            } else {
                val present = !student.present
                student.copy(
                    present = present,
                    pointsInput = if (present) student.pointsInput else "0",
                    pointsError = null
                )
            }
        }
        publishStudents(students)
    }

    fun updatePoints(studentId: Int, input: String) {
        if (_state.value.isSaving || !isPotentialNumber(input)) return
        val students = _state.value.students.map { student ->
            if (student.id != studentId || !student.present) {
                student
            } else {
                student.copy(pointsInput = input)
            }
        }
        publishStudents(students)
    }

    fun requestSave() {
        val current = _state.value
        if (!current.canSave || current.isSaving) return
        _state.update { it.copy(showSaveConfirmation = true) }
    }

    fun dismissSaveConfirmation() {
        _state.update { it.copy(showSaveConfirmation = false) }
    }

    fun confirmSave() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        val statement = loadedStatement
            ?: return showLoadError("Ведомость не загружена")
        val coursePlanId = statement.coursePlanId
            ?: return showLoadError("Сервер не вернул учебный план группы")
        val groupId = snapshot.selectedGroupId
            ?: return showLoadError("Группа не выбрана")
        val seminarId = snapshot.selectedSeminarId
            ?: return showLoadError("Семинар не выбран")
        val sectionNumber = snapshot.seminars
            .firstOrNull { it.id == seminarId }
            ?.sectionNumber
            ?: return showLoadError("Раздел семинара не найден")

        val initialSnapshot = initialStates
        val updates = snapshot.students.mapNotNull { student ->
            val initial = initialSnapshot[student.seminarPassId] ?: return@mapNotNull null
            val points = if (student.present) {
                validateStudentPoints(student).value ?: return@mapNotNull null
            } else {
                0.0
            }
            val presenceChanged = student.present != initial.present
            val pointsChanged = student.present && abs(points - initial.points) > POINTS_EPSILON
            if (!presenceChanged && !pointsChanged) {
                null
            } else {
                SeminarAttendanceUpdate(
                    seminarPassId = student.seminarPassId,
                    coursePlanId = coursePlanId,
                    sectionNumber = sectionNumber,
                    present = student.present,
                    presenceChanged = presenceChanged,
                    workPoints = points,
                    pointsChanged = pointsChanged
                )
            }
        }
        if (!beginSave()) return
        _state.update { it.copy(showSaveConfirmation = false) }
        viewModelScope.launch {
            try {
                when (val result = attendanceRepository.saveSeminarAttendance(updates)) {
                    is AppResult.Error -> {
                        val limitError = result.details as? SeminarPointsLimitError
                        if (limitError == null) {
                            reloadAfterSaveFailure(
                                groupId = groupId,
                                seminarId = seminarId,
                                saveError = result.message
                            )
                        } else {
                            applyServerPointsLimit(limitError, result.message)
                        }
                    }
                    is AppResult.Success -> reloadCurrentStatement("Ведомость семинара сохранена")
                }
            } finally {
                endSave()
            }
        }
    }

    private fun beginSave(): Boolean {
        if (!saveGate.tryAcquire()) return false
        _state.update { it.copy(isSaving = true, errorMessage = null) }
        return true
    }

    private fun endSave() {
        saveGate.release()
        _state.update { it.copy(isSaving = false) }
    }

    private suspend fun loadStatement(groupId: Int, preferredSeminarId: Int?) {
        when (val result = attendanceRepository.getSeminarStatement(groupId)) {
            is AppResult.Error -> showLoadError(result.message)
            is AppResult.Success -> applyStatement(result.data, preferredSeminarId)
        }
    }

    private fun applyStatement(statement: SeminarStatement, preferredSeminarId: Int?) {
        loadedStatement = statement
        serverPointsLimits = emptyMap()
        val cells = linkedMapOf<Int, com.example.uir_android.domain.model.SeminarAttendanceCell>()
        statement.rows.forEach { row ->
            row.cells.forEach { cell -> cells.putIfAbsent(cell.seminarPlanId, cell) }
        }
        val selectedSeminarId = preferredSeminarId?.takeIf(cells::containsKey)
            ?: cells.keys.firstOrNull()
        savedStateHandle[SELECTED_GROUP_ID_KEY] = statement.groupId
        savedStateHandle[SELECTED_SEMINAR_ID_KEY] = selectedSeminarId
        val seminars = cells.values.mapIndexed { index, cell ->
            SeminarOptionUiState(
                id = cell.seminarPlanId,
                title = cell.title.ifBlank { "Семинар ${index + 1}" },
                sectionNumber = cell.sectionNumber
            )
        }
        _state.update {
            it.copy(
                isLoading = false,
                seminars = seminars,
                selectedSeminarId = selectedSeminarId,
                errorMessage = if (seminars.isEmpty()) "На сервере нет доступных семинаров" else null
            )
        }
        if (selectedSeminarId != null) {
            applySelectedSeminar(selectedSeminarId)
        } else {
            initialStates = emptyMap()
            _state.update { it.copy(students = emptyList(), canSave = false, presentCount = 0) }
        }
    }

    private fun applySelectedSeminar(seminarId: Int) {
        val statement = loadedStatement ?: return
        savedStateHandle[SELECTED_SEMINAR_ID_KEY] = seminarId
        val students = statement.rows.mapNotNull { row ->
            val cell = row.cells.firstOrNull { it.seminarPlanId == seminarId }
                ?: return@mapNotNull null
            SeminarStudentUiState(
                id = row.student.id,
                seminarPassId = cell.seminarPassId,
                name = row.student.displayName.ifBlank { row.student.email },
                present = cell.present,
                pointsInput = formatPoints(cell.workPoints)
            )
        }.sortedBy { it.name.lowercase() }
        initialStates = students.associate { student ->
            student.seminarPassId to InitialStudentState(
                present = student.present,
                points = student.pointsInput.replace(',', '.').toDoubleOrNull() ?: 0.0
            )
        }
        _state.update {
            it.copy(
                selectedSeminarId = seminarId,
                students = students,
                errorMessage = null,
            )
        }
        publishStudents(students)
    }

    private fun publishStudents(students: List<SeminarStudentUiState>) {
        var hasInvalidPoints = false
        val validated = students.map { student ->
            if (!student.present) {
                student.copy(pointsError = null)
            } else {
                val validation = validateStudentPoints(student)
                if (!validation.isValid) hasInvalidPoints = true
                student.copy(pointsError = validation.message.takeUnless { validation.isValid })
            }
        }
        val hasChanges = validated.any { student ->
            val initial = initialStates[student.seminarPassId] ?: return@any false
            val points = validateStudentPoints(student).value ?: initial.points
            student.present != initial.present ||
                (student.present && abs(points - initial.points) > POINTS_EPSILON)
        }
        _state.update {
            it.copy(
                students = validated,
                presentCount = validated.count { student -> student.present },
                canSave = hasChanges && !hasInvalidPoints,
                errorMessage = null,
            )
        }
    }

    private fun applyServerPointsLimit(error: SeminarPointsLimitError, message: String) {
        if (_state.value.students.none { it.seminarPassId == error.seminarPassId }) {
            _state.update { it.copy(errorMessage = message) }
            return
        }
        serverPointsLimits = serverPointsLimits + (error.seminarPassId to error.maxWorkPoints)
        publishStudents(_state.value.students)
        _state.update { it.copy(errorMessage = message) }
    }

    private fun validateStudentPoints(student: SeminarStudentUiState): SeminarPointsValidation {
        val validation = validatePoints(student.pointsInput)
        if (!validation.isValid) return validation
        val value = validation.value ?: return validation
        val serverLimit = serverPointsLimits[student.seminarPassId] ?: return validation
        return if (value > serverLimit) {
            SeminarPointsValidation(
                isValid = false,
                message = "Максимум раздела: ${formatPoints(serverLimit)}"
            )
        } else {
            validation
        }
    }

    private suspend fun reloadCurrentStatement(successMessage: String) {
        val snapshot = _state.value
        val groupId = snapshot.selectedGroupId ?: return showLoadError("Группа не выбрана")
        when (val result = attendanceRepository.getSeminarStatement(groupId)) {
            is AppResult.Error -> _state.update {
                it.copy(isSaving = false, errorMessage = result.message)
            }
            is AppResult.Success -> {
                applyStatement(result.data, snapshot.selectedSeminarId)
                _state.update {
                    it.copy(isSaving = false, errorMessage = null)
                }
                emitSnackbar(successMessage)
            }
        }
    }

    private suspend fun reloadAfterSaveFailure(
        groupId: Int,
        seminarId: Int,
        saveError: String
    ) {
        when (val result = attendanceRepository.getSeminarStatement(groupId)) {
            is AppResult.Success -> {
                applyStatement(result.data, seminarId)
                _state.update {
                    it.copy(isSaving = false, errorMessage = saveError)
                }
            }
            is AppResult.Error -> _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = "$saveError Не удалось обновить ведомость: ${result.message}",
                )
            }
        }
    }

    private fun showLoadError(message: String) {
        _state.update { it.copy(isLoading = false, isSaving = false, errorMessage = message) }
    }

    private fun isPotentialNumber(value: String): Boolean =
        value.matches(Regex("-?\\d*([.,]\\d*)?"))

    private fun formatPoints(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        const val POINTS_EPSILON = 0.000001
        const val SELECTED_GROUP_ID_KEY = "seminar_attendance_group_id"
        const val SELECTED_SEMINAR_ID_KEY = "seminar_attendance_seminar_id"
    }
}
