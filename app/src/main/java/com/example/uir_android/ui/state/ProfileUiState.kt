package com.example.uir_android.ui.state

import com.example.uir_android.domain.model.AcademicProfile

data class ProfileUiState(
    val isLoading: Boolean = true,
    val profile: AcademicProfile? = null,
    val errorMessage: String? = null
)
