package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AttachmentDownloadStatus
import com.example.uir_android.domain.model.NewsAttachment
import com.example.uir_android.domain.repository.AttachmentDownloadRepository
import com.example.uir_android.domain.repository.NewsRepository
import com.example.uir_android.domain.usecase.ExtractNewsLinksUseCase
import com.example.uir_android.ui.state.NewsDetailUiState
import com.example.uir_android.ui.state.NewsListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.FileNotFoundException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NewsListViewModel @Inject constructor(
    private val newsRepository: NewsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(NewsListUiState())
    val state = _state.asStateFlow()

    init {
        load(isRefresh = false)
    }

    fun refresh() = load(isRefresh = true)

    private fun load(isRefresh: Boolean) {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = !isRefresh && it.items.isEmpty(),
                    isRefreshing = isRefresh,
                    errorMessage = null
                )
            }
            when (val result = newsRepository.getNews()) {
                is AppResult.Error -> _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.message
                    )
                }
                is AppResult.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        items = result.data,
                        errorMessage = null
                    )
                }
            }
        }
    }
}

@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val newsRepository: NewsRepository,
    private val extractLinks: ExtractNewsLinksUseCase,
    private val downloadRepository: AttachmentDownloadRepository
) : EventViewModel() {
    private val newsId: String = checkNotNull(savedStateHandle["newsId"])
    private val _state = MutableStateFlow(NewsDetailUiState())
    val state = _state.asStateFlow()
    private val downloadJobs = mutableMapOf<String, Job>()

    init {
        load()
    }

    fun retryLoad() = load()

    fun startDownload(attachment: NewsAttachment) {
        if (_state.value.downloads[attachment.url] is AttachmentDownloadStatus.Downloading) return
        downloadJobs.remove(attachment.url)?.cancel()
        downloadJobs[attachment.url] = viewModelScope.launch {
            when (val result = downloadRepository.enqueue(attachment.url, attachment.title)) {
                is AppResult.Error -> updateDownload(
                    attachment.url,
                    AttachmentDownloadStatus.Failed(result.message)
                )
                is AppResult.Success -> {
                    downloadRepository.observe(result.data).collect { status ->
                        updateDownload(attachment.url, status)
                    }
                }
            }
        }
    }

    fun cancelDownload(url: String) {
        val status = _state.value.downloads[url] as? AttachmentDownloadStatus.Downloading ?: return
        downloadJobs.remove(url)?.cancel()
        viewModelScope.launch {
            when (val result = downloadRepository.cancel(status.downloadId)) {
                is AppResult.Error -> updateDownload(
                    url,
                    AttachmentDownloadStatus.Failed(result.message)
                )
                is AppResult.Success -> updateDownload(
                    url,
                    AttachmentDownloadStatus.Failed("Скачивание отменено")
                )
            }
        }
    }

    fun openDownloaded(url: String) {
        val status = _state.value.downloads[url] as? AttachmentDownloadStatus.Completed ?: return
        viewModelScope.launch {
            when (val result = downloadRepository.open(status.downloadId)) {
                is AppResult.Error -> {
                    if (result.cause is FileNotFoundException) {
                        updateDownload(url, AttachmentDownloadStatus.Failed(result.message))
                    }
                    emitSnackbar(result.message)
                }
                is AppResult.Success -> Unit
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = newsRepository.getNewsDetail(newsId)) {
                is AppResult.Error -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is AppResult.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            news = result.data,
                            links = extractLinks(result.data.body),
                            errorMessage = null
                        )
                    }
                    restoreDownloads(result.data.attachments)
                }
            }
        }
    }

    private suspend fun restoreDownloads(attachments: List<NewsAttachment>) {
        attachments.forEach { attachment ->
            val downloadId = downloadRepository.findDownloadId(attachment.url) ?: return@forEach
            trackDownload(attachment.url, downloadId)
        }
    }

    private fun trackDownload(url: String, downloadId: Long) {
        downloadJobs.remove(url)?.cancel()
        downloadJobs[url] = viewModelScope.launch {
            downloadRepository.observe(downloadId).collect { status ->
                updateDownload(url, status)
            }
        }
    }

    private fun updateDownload(url: String, status: AttachmentDownloadStatus) {
        _state.update { current ->
            current.copy(downloads = current.downloads + (url to status))
        }
    }
}
