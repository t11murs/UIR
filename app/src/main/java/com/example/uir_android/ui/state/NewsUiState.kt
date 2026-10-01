package com.example.uir_android.ui.state

import com.example.uir_android.domain.model.AttachmentDownloadStatus
import com.example.uir_android.domain.model.NewsItem

data class NewsListUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<NewsItem> = emptyList(),
    val errorMessage: String? = null
)

data class NewsDetailUiState(
    val isLoading: Boolean = true,
    val news: NewsItem? = null,
    val links: List<String> = emptyList(),
    val downloads: Map<String, AttachmentDownloadStatus> = emptyMap(),
    val errorMessage: String? = null
)
