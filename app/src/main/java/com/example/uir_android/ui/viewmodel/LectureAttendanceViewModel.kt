package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.SingleFlightGate
import com.example.uir_android.domain.attendance.LectureLimitDraft
import com.example.uir_android.domain.attendance.buildLectureAttendanceUpdates
import com.example.uir_android.domain.attendance.buildLectureLimitUpdates
import com.example.uir_android.domain.model.CurrentUser
import com.example.uir_android.domain.model.AcademicAccessPolicy
import com.example.uir_android.domain.model.LectureLimitUpdate
import com.example.uir_android.domain.model.LectureStatement
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.domain.repository.AttendanceRepository
import com.example.uir_android.domain.usecase.ValidateLectureAttendanceSelectionUseCase
import com.example.uir_android.ui.state.LectureAttendanceMode
import com.example.uir_android.ui.state.LectureAttendanceUiState
import com.example.uir_android.ui.state.LectureGroupUiState
import com.example.uir_android.ui.state.LectureMatrixCellKey
import com.example.uir_android.ui.state.LectureMatrixColumnUiState
import com.example.uir_android.ui.state.LectureOptionUiState
import com.example.uir_android.ui.state.LectureSaveAction
import com.example.uir_android.ui.state.LectureStudentUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LectureAttendanceViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val academicRepository: AcademicRepository,
    private val attendanceRepository: AttendanceRepository,
    private val validateSelection: ValidateLectureAttendanceSelectionUseCase
) : EventViewModel() {
    private val _state = MutableStateFlow(LectureAttendanceUiState())
    val state = _state.asStateFlow()

    private var currentUser: CurrentUser? = null
    private var loadedStatement: LectureStatement? = null
    private var initialSelectedStudentIds: Set<Int> = emptySet()
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
            val preferredLectureId = _state.value.selectedLectureId
                ?: savedStateHandle[SELECTED_LECTURE_ID_KEY]
            _state.update {
                it.copy(
                    isLoading = true,
                    mode = LectureAttendanceMode.LOADING,
                    errorMessage = null,
                )
            }

            when (val userResult = academicRepository.getCurrentUser()) {
                is AppResult.Error -> showLoadError(userResult.message)
                is AppResult.Success -> {
                    currentUser = userResult.data
                    when {
                        userResult.data.hasLectureMatrix -> loadAdminMatrix(userResult.data)
                        userResult.data.isSteward -> loadSteward(
                            user = userResult.data,
                            preferredLectureId = preferredLectureId
                        )
                        userResult.data.canEditLectureAttendance -> loadEditor(
                            user = userResult.data,
                            preferredGroupId = preferredGroupId,
                            preferredLectureId = preferredLectureId
                        )
                        else -> _state.update {
                            it.copy(
                                isLoading = false,
                                mode = LectureAttendanceMode.DENIED,
                                userName = userResult.data.displayName,
                                role = userResult.data.role,
                                errorMessage = "Для работы с посещаемостью нужна роль старосты или преподавателя"
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectGroup(groupId: Int) {
        if (_state.value.mode != LectureAttendanceMode.EDITOR ||
            groupId == _state.value.selectedGroupId ||
            _state.value.isSaving
        ) {
            return
        }
        savedStateHandle[SELECTED_GROUP_ID_KEY] = groupId
        savedStateHandle[SELECTED_LECTURE_ID_KEY] = null
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    selectedGroupId = groupId,
                    errorMessage = null,
                )
            }
            loadEditorStatement(groupId = groupId, preferredLectureId = null)
        }
    }

    fun selectLecture(lectureId: Int) {
        if (_state.value.isSaving || lectureId == _state.value.selectedLectureId) {
            return
        }
        applySelectedLecture(lectureId)
    }

    fun updateMatrixLimit(groupId: Int, lectureId: Int, value: String) {
        val current = _state.value
        if (current.mode != LectureAttendanceMode.ADMIN_MATRIX || current.isSaving) return
        if (!value.all(Char::isDigit)) return
        val key = LectureMatrixCellKey(groupId, lectureId)
        _state.update { it.copy(matrixValues = it.matrixValues + (key to value)) }
    }

    fun saveMatrix() {
        val current = _state.value
        if (current.mode != LectureAttendanceMode.ADMIN_MATRIX || current.isSaving) return
        val changedValues = current.matrixValues.filter { (key, value) ->
            current.initialMatrixValues[key] != value
        }
        val validation = buildLectureLimitUpdates(
            changedValues.map { (key, value) ->
                LectureLimitDraft(key.lectureId, key.groupId, value)
            }
        )
        if (!validation.isValid) {
            val invalidCells = validation.invalidDrafts.joinToString(limit = 3) { draft ->
                val group = current.groups.firstOrNull { it.id == draft.groupId }?.name
                    ?: "группа ${draft.groupId}"
                val week = current.matrixColumns.firstOrNull { it.lectureId == draft.lectureId }?.title
                    ?: "лекция ${draft.lectureId}"
                "$group, $week"
            }
            _state.update {
                it.copy(
                    errorMessage = "Заполните изменённые ячейки целыми неотрицательными числами: $invalidCells",
                )
            }
            return
        }
        val updates = validation.updates
        if (updates.isEmpty()) {
            emitSnackbar("Нет изменений для сохранения")
            return
        }
        val user = currentUser ?: return
        if (!beginSave()) return
        viewModelScope.launch {
            try {
                when (val result = attendanceRepository.saveLectureLimits(updates)) {
                is AppResult.Error -> {
                    loadAdminMatrix(user)
                    _state.update {
                        it.copy(isSaving = false, errorMessage = result.message)
                    }
                }
                is AppResult.Success -> {
                    loadAdminMatrix(user)
                    _state.update {
                        it.copy(isSaving = false)
                    }
                    emitSnackbar("Матрица посещаемости сохранена")
                }
                }
            } finally {
                endSave()
            }
        }
    }

    fun toggleStudent(studentId: Int) {
        val current = _state.value
        if (current.isSaving || current.mode == LectureAttendanceMode.DENIED) {
            return
        }
        val item = current.students.firstOrNull { it.id == studentId } ?: return
        if (current.mode == LectureAttendanceMode.STEWARD && !item.selected) {
            val limit = current.requiredCount
            if (limit == null || limit <= 0) {
                _state.update { it.copy(errorMessage = current.selectionMessage) }
                return
            }
            if (current.selectedCount >= limit) {
                _state.update {
                    it.copy(errorMessage = "Уже выбрано установленное количество студентов")
                }
                return
            }
        }

        val updatedStudents = current.students.map { student ->
            if (student.id == studentId) student.copy(selected = !student.selected) else student
        }
        publishStudents(updatedStudents)
    }

    fun updateLimitInput(value: String) {
        if (value.all(Char::isDigit)) {
            _state.update {
                it.copy(limitInput = value, errorMessage = null)
            }
        }
    }

    fun requestAttendanceSave() {
        val current = _state.value
        if (!current.canSaveAttendance || current.isSaving) {
            if (current.mode == LectureAttendanceMode.STEWARD) {
                _state.update { it.copy(errorMessage = current.selectionMessage) }
            }
            return
        }
        _state.update { it.copy(pendingSaveAction = LectureSaveAction.ATTENDANCE) }
    }

    fun requestLimitSave() {
        val current = _state.value
        if (current.mode != LectureAttendanceMode.EDITOR || !current.canEditLimit || current.isSaving) {
            return
        }
        val limit = current.limitInput.toIntOrNull()
        when {
            limit == null -> _state.update { it.copy(errorMessage = "Введите количество присутствующих") }
            limit < 0 -> _state.update { it.copy(errorMessage = "Количество не может быть отрицательным") }
            limit > current.students.size -> _state.update {
                it.copy(errorMessage = "Количество не может превышать число студентов в группе")
            }
            else -> _state.update { it.copy(pendingSaveAction = LectureSaveAction.LIMIT) }
        }
    }

    fun dismissSaveConfirmation() {
        _state.update { it.copy(pendingSaveAction = null) }
    }

    fun confirmSave() {
        val action = _state.value.pendingSaveAction ?: return
        _state.update { it.copy(pendingSaveAction = null) }
        when (action) {
            LectureSaveAction.ATTENDANCE -> saveAttendance()
            LectureSaveAction.LIMIT -> saveLimit()
        }
    }

    fun clearMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun loadSteward(user: CurrentUser, preferredLectureId: Int?) {
        when (val statementResult = attendanceRepository.getStewardLectureStatement()) {
            is AppResult.Error -> showLoadError(statementResult.message)
            is AppResult.Success -> {
                _state.update {
                    it.copy(
                        mode = LectureAttendanceMode.STEWARD,
                        userName = user.displayName,
                        role = user.role,
                        selectedGroupId = user.groupId,
                        groups = emptyList()
                    )
                }
                applyStatement(statementResult.data, preferredLectureId)
            }
        }
    }

    private suspend fun loadAdminMatrix(user: CurrentUser) {
        val groupsResult = academicRepository.getGroups()
        val limitsResult = attendanceRepository.getLectureLimits()
        if (groupsResult is AppResult.Error) {
            showLoadError(groupsResult.message)
            return
        }
        if (limitsResult is AppResult.Error) {
            showLoadError(limitsResult.message)
            return
        }
        if (groupsResult !is AppResult.Success || limitsResult !is AppResult.Success) return

        val groups = groupsResult.data
            .filterNot { it.archived }
            .map { LectureGroupUiState(it.id, it.name) }
        val limits = limitsResult.data.limits
        val lectureIds = limits.map { it.lectureId }.distinct().sorted()
        val values = buildMap {
            groups.forEach { group ->
                lectureIds.forEach { lectureId ->
                    val limit = limits.firstOrNull {
                        it.groupId == group.id && it.lectureId == lectureId
                    }?.limit
                    put(LectureMatrixCellKey(group.id, lectureId), limit?.toString().orEmpty())
                }
            }
        }
        val counts = limits.associate {
            LectureMatrixCellKey(it.groupId, it.lectureId) to it.currentCount
        }
        _state.update {
            it.copy(
                isLoading = false,
                mode = LectureAttendanceMode.ADMIN_MATRIX,
                userName = user.displayName,
                role = user.role,
                groups = groups,
                matrixColumns = lectureIds.mapIndexed { index, lectureId ->
                    LectureMatrixColumnUiState(lectureId, "Неделя ${index + 1}")
                },
                matrixValues = values,
                initialMatrixValues = values,
                matrixCurrentCounts = counts,
                errorMessage = if (groups.isEmpty() || lectureIds.isEmpty()) {
                    "Нет данных для матрицы посещаемости"
                } else null
            )
        }
    }

    private suspend fun loadEditor(
        user: CurrentUser,
        preferredGroupId: Int?,
        preferredLectureId: Int?
    ) {
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
                    .map { LectureGroupUiState(id = it.id, name = it.name) }
                val groupId = preferredGroupId
                    ?.takeIf { candidate -> groups.any { it.id == candidate } }
                    ?: groups.firstOrNull()?.id
                _state.update {
                    it.copy(
                        mode = LectureAttendanceMode.EDITOR,
                        userName = user.displayName,
                        role = user.role,
                        canEditLimit = user.canSetLectureLimit,
                        groups = groups,
                        selectedGroupId = groupId
                    )
                }
                if (groupId == null) {
                    showLoadError("На сервере нет доступных групп")
                } else {
                    loadEditorStatement(groupId, preferredLectureId)
                }
            }
        }
    }

    private suspend fun loadEditorStatement(groupId: Int, preferredLectureId: Int?) {
        when (val statementResult = attendanceRepository.getManagedLectureStatement(groupId)) {
            is AppResult.Error -> showLoadError(statementResult.message)
            is AppResult.Success -> applyStatement(statementResult.data, preferredLectureId)
        }
    }

    private fun applyStatement(statement: LectureStatement, preferredLectureId: Int?) {
        loadedStatement = statement
        val cells = linkedMapOf<Int, com.example.uir_android.domain.model.LectureAttendanceCell>()
        statement.rows.forEach { row ->
            row.cells.forEach { cell ->
                cell.lectureId?.let { lectureId ->
                    val previous = cells[lectureId]
                    cells[lectureId] = when {
                        previous == null -> cell
                        previous.limit == null && cell.limit != null -> cell
                        else -> previous
                    }
                }
            }
        }
        val preferred = preferredLectureId?.takeIf(cells::containsKey)
        val selectedLectureId = preferred ?: cells.keys.firstOrNull()
        savedStateHandle[SELECTED_GROUP_ID_KEY] = statement.groupId
        savedStateHandle[SELECTED_LECTURE_ID_KEY] = selectedLectureId
        val lectures = cells.entries.mapIndexed { index, (lectureId, cell) ->
            LectureOptionUiState(
                id = lectureId,
                title = cell.title.ifBlank { "Лекция ${index + 1}" },
                limit = cell.limit,
                currentCount = statement.rows.count { row ->
                    row.cells.any { it.lectureId == lectureId && it.present }
                }
            )
        }
        _state.update {
            it.copy(
                isLoading = false,
                lectures = lectures,
                selectedLectureId = selectedLectureId,
                errorMessage = if (lectures.isEmpty()) "На сервере нет доступных лекций" else null
            )
        }
        if (selectedLectureId != null) {
            applySelectedLecture(selectedLectureId)
        } else {
            initialSelectedStudentIds = emptySet()
            _state.update {
                it.copy(
                    students = emptyList(),
                    selectedCount = 0,
                    requiredCount = null,
                    limitInput = "",
                    canSaveAttendance = false,
                    selectionMessage = "Нет доступных лекций"
                )
            }
        }
    }

    private fun applySelectedLecture(lectureId: Int) {
        val statement = loadedStatement ?: return
        val lecture = _state.value.lectures.firstOrNull { it.id == lectureId } ?: return
        savedStateHandle[SELECTED_LECTURE_ID_KEY] = lectureId
        val students = statement.rows.mapNotNull { row ->
            val cell = row.cells.firstOrNull { it.lectureId == lectureId } ?: return@mapNotNull null
            LectureStudentUiState(
                id = row.student.id,
                name = row.student.displayName.ifBlank { row.student.email },
                selected = cell.present
            )
        }.sortedBy { it.name.lowercase() }
        initialSelectedStudentIds = students.filter { it.selected }.mapTo(linkedSetOf()) { it.id }
        _state.update {
            it.copy(
                selectedLectureId = lectureId,
                students = students,
                requiredCount = lecture.limit,
                limitInput = lecture.limit?.toString().orEmpty(),
                errorMessage = null,
            )
        }
        publishStudents(students)
    }

    private fun publishStudents(students: List<LectureStudentUiState>) {
        val selectedIds = students.filter { it.selected }.mapTo(linkedSetOf()) { it.id }
        val current = _state.value
        val changed = selectedIds != initialSelectedStudentIds
        val validation = validateSelection(selectedIds.size, current.requiredCount)
        val canSave = changed && when (current.mode) {
            LectureAttendanceMode.STEWARD -> validation.isValid
            LectureAttendanceMode.EDITOR -> true
            else -> false
        }
        _state.update {
            it.copy(
                students = students,
                selectedCount = selectedIds.size,
                selectionMessage = if (current.mode == LectureAttendanceMode.STEWARD) {
                    validation.message
                } else {
                    "Выбрано ${selectedIds.size} из ${students.size}"
                },
                canSaveAttendance = canSave,
                errorMessage = null,
            )
        }
    }

    private fun saveAttendance() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        val lectureId = snapshot.selectedLectureId ?: return
        val selectedIds = snapshot.students.filter { it.selected }.map { it.id }
        val initialSelections = initialSelectedStudentIds
        if (!beginSave()) return
        viewModelScope.launch {
            try {
                val result = when (snapshot.mode) {
                    LectureAttendanceMode.STEWARD -> attendanceRepository.saveStewardLectureAttendance(lectureId, selectedIds)
                    LectureAttendanceMode.EDITOR -> {
                        val updates = buildLectureAttendanceUpdates(
                            lectureId = lectureId,
                            currentSelections = snapshot.students.associate { it.id to it.selected },
                            initiallySelectedStudentIds = initialSelections
                        )
                        attendanceRepository.setLectureAttendances(updates)
                    }
                    else -> AppResult.Error("Недостаточно прав для сохранения")
                }
                handleSaveResult(result, "Посещаемость сохранена")
            } finally {
                endSave()
            }
        }
    }

    private fun saveLimit() {
        val snapshot = _state.value
        if (snapshot.isSaving) return
        val lectureId = snapshot.selectedLectureId ?: return
        val groupId = snapshot.selectedGroupId ?: return
        val limit = snapshot.limitInput.toIntOrNull() ?: return
        if (!beginSave()) return
        viewModelScope.launch {
            try {
                handleSaveResult(
                    result = attendanceRepository.saveLectureLimits(
                        listOf(LectureLimitUpdate(lectureId, groupId, limit))
                    ),
                    successMessage = "Количество присутствующих сохранено"
                )
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

    private suspend fun handleSaveResult(result: AppResult<Unit>, successMessage: String) {
        when (result) {
            is AppResult.Error -> reloadCurrentStatementAfterFailure(result.message)
            is AppResult.Success -> reloadCurrentStatement(successMessage)
        }
    }

    private suspend fun reloadCurrentStatementAfterFailure(saveError: String) {
        val snapshot = _state.value
        val reloadResult = when (snapshot.mode) {
            LectureAttendanceMode.STEWARD -> attendanceRepository.getStewardLectureStatement()
            LectureAttendanceMode.EDITOR -> snapshot.selectedGroupId
                ?.let { attendanceRepository.getManagedLectureStatement(it) }
                ?: AppResult.Error("Группа не выбрана")
            else -> AppResult.Error("Невозможно обновить ведомость")
        }
        when (reloadResult) {
            is AppResult.Success -> {
                applyStatement(reloadResult.data, snapshot.selectedLectureId)
                _state.update {
                    it.copy(isSaving = false, errorMessage = saveError)
                }
            }
            is AppResult.Error -> _state.update {
                it.copy(
                    isSaving = false,
                    errorMessage = "$saveError Не удалось обновить ведомость: ${reloadResult.message}",
                )
            }
        }
    }

    private suspend fun reloadCurrentStatement(successMessage: String) {
        val snapshot = _state.value
        val result = when (snapshot.mode) {
            LectureAttendanceMode.STEWARD -> attendanceRepository.getStewardLectureStatement()
            LectureAttendanceMode.EDITOR -> {
                val groupId = snapshot.selectedGroupId
                    ?: return showLoadError("Группа не выбрана")
                attendanceRepository.getManagedLectureStatement(groupId)
            }
            else -> return
        }
        when (result) {
            is AppResult.Error -> _state.update {
                it.copy(isSaving = false, errorMessage = result.message)
            }
            is AppResult.Success -> {
                applyStatement(result.data, snapshot.selectedLectureId)
                _state.update {
                    it.copy(isSaving = false, errorMessage = null)
                }
                emitSnackbar(successMessage)
            }
        }
    }

    private fun showLoadError(message: String) {
        _state.update {
            it.copy(isLoading = false, isSaving = false, errorMessage = message)
        }
    }

    private companion object {
        const val SELECTED_GROUP_ID_KEY = "lecture_attendance_group_id"
        const val SELECTED_LECTURE_ID_KEY = "lecture_attendance_lecture_id"
    }
}
