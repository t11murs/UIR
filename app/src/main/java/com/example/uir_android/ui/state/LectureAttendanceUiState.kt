package com.example.uir_android.ui.state

enum class LectureAttendanceMode {
    LOADING,
    ADMIN_MATRIX,
    STEWARD,
    EDITOR,
    DENIED
}

enum class LectureSaveAction {
    ATTENDANCE,
    LIMIT
}

data class LectureGroupUiState(
    val id: Int,
    val name: String
)

data class LectureOptionUiState(
    val id: Int,
    val title: String,
    val limit: Int?,
    val currentCount: Int
)

data class LectureStudentUiState(
    val id: Int,
    val name: String,
    val selected: Boolean
)

data class LectureMatrixCellKey(
    val groupId: Int,
    val lectureId: Int
)

data class LectureMatrixColumnUiState(
    val lectureId: Int,
    val title: String
)

data class LectureAttendanceUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val mode: LectureAttendanceMode = LectureAttendanceMode.LOADING,
    val userName: String = "",
    val role: String = "",
    val canEditLimit: Boolean = false,
    val groups: List<LectureGroupUiState> = emptyList(),
    val selectedGroupId: Int? = null,
    val lectures: List<LectureOptionUiState> = emptyList(),
    val selectedLectureId: Int? = null,
    val students: List<LectureStudentUiState> = emptyList(),
    val selectedCount: Int = 0,
    val requiredCount: Int? = null,
    val limitInput: String = "",
    val selectionMessage: String = "",
    val canSaveAttendance: Boolean = false,
    val errorMessage: String? = null,
    val pendingSaveAction: LectureSaveAction? = null,
    val matrixColumns: List<LectureMatrixColumnUiState> = emptyList(),
    val matrixValues: Map<LectureMatrixCellKey, String> = emptyMap(),
    val matrixCurrentCounts: Map<LectureMatrixCellKey, Int> = emptyMap(),
    val initialMatrixValues: Map<LectureMatrixCellKey, String> = emptyMap()
)
