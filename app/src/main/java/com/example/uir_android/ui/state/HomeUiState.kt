package com.example.uir_android.ui.state

import com.example.uir_android.domain.model.NewsItem

data class HomeUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val userName: String = "",
    val news: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)

data class MenuUiState(
    val isLoading: Boolean = true,
    val canViewLectureAttendance: Boolean = false,
    val canViewSeminarAttendance: Boolean = false
)
