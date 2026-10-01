package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AcademicAccessPolicy
import com.example.uir_android.domain.repository.AcademicRepository
import com.example.uir_android.domain.repository.NewsRepository
import com.example.uir_android.ui.state.HomeUiState
import com.example.uir_android.ui.state.MenuUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val academicRepository: AcademicRepository,
    private val newsRepository: NewsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing) return

        viewModelScope.launch {
            val hasNews = _state.value.news.isNotEmpty()
            _state.update {
                it.copy(
                    isLoading = !hasNews,
                    isRefreshing = hasNews,
                    errorMessage = null
                )
            }

            supervisorScope {
                val userRequest = async { academicRepository.getCurrentUser() }
                val newsRequest = async { newsRepository.getNews() }
                val userResult = userRequest.await()
                val newsResult = newsRequest.await()

                _state.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        userName = when (userResult) {
                            is AppResult.Success -> userResult.data.displayName
                            else -> state.userName
                        },
                        news = when (newsResult) {
                            is AppResult.Success -> newsResult.data
                            else -> state.news
                        },
                        errorMessage = (newsResult as? AppResult.Error)?.message
                    )
                }
            }
        }
    }
}

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val academicRepository: AcademicRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MenuUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = academicRepository.getCurrentUser()) {
                is AppResult.Success -> _state.value = MenuUiState(
                    isLoading = false,
                    canViewLectureAttendance = AcademicAccessPolicy.canViewLectureAttendance(
                        result.data.academicRole
                    ),
                    canViewSeminarAttendance = AcademicAccessPolicy.canViewSeminarAttendance(
                        result.data.academicRole
                    )
                )
                else -> _state.value = MenuUiState(isLoading = false)
            }
        }
    }
}
