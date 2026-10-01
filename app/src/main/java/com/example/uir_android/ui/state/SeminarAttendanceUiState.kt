package com.example.uir_android.ui.state

enum class SeminarAttendanceMode {
    LOADING,
    EDITOR,
    DENIED
}

data class SeminarGroupUiState(
    val id: Int,
    val name: String
)

data class SeminarOptionUiState(
    val id: Int,
    val title: String,
    val sectionNumber: Int
)

data class SeminarStudentUiState(
    val id: Int,
    val seminarPassId: Int,
    val name: String,
    val present: Boolean,
    val pointsInput: String,
    val pointsError: String? = null
)

data class SeminarAttendanceUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val mode: SeminarAttendanceMode = SeminarAttendanceMode.LOADING,
    val userName: String = "",
    val role: String = "",
    val groups: List<SeminarGroupUiState> = emptyList(),
    val selectedGroupId: Int? = null,
    val seminars: List<SeminarOptionUiState> = emptyList(),
    val selectedSeminarId: Int? = null,
    val students: List<SeminarStudentUiState> = emptyList(),
    val presentCount: Int = 0,
    val canSave: Boolean = false,
    val showSaveConfirmation: Boolean = false,
    val errorMessage: String? = null
)
