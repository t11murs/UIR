package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.ui.state.ProfileUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val academicRepository: AcademicRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = academicRepository.getProfile()) {
                is AppResult.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is AppResult.Success -> _state.update {
                    it.copy(isLoading = false, profile = result.data, errorMessage = null)
                }
            }
        }
    }
}
