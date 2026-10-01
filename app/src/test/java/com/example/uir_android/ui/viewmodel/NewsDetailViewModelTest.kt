package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AttachmentDownloadStatus
import com.example.uir_android.domain.model.NewsAttachment
import com.example.uir_android.domain.model.NewsItem
import com.example.uir_android.domain.repository.AttachmentDownloadRepository
import com.example.uir_android.domain.repository.NewsRepository
import com.example.uir_android.domain.usecase.ExtractNewsLinksUseCase
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class NewsDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `tracked download is restored without enqueueing a duplicate`() = runTest(dispatcher) {
        val newsRepository = mock(NewsRepository::class.java)
        val downloadRepository = mock(AttachmentDownloadRepository::class.java)
        val attachment = NewsAttachment(title = "Материал", url = "https://example.test/file.pdf")

        `when`(newsRepository.getNewsDetail("42")).thenReturn(
            AppResult.Success(
                NewsItem(
                    id = "42",
                    title = "Новость",
                    summary = "",
                    body = "",
                    publishedAt = null,
                    attachments = listOf(attachment)
                )
            )
        )
        `when`(downloadRepository.findDownloadId(attachment.url)).thenReturn(1001L)
        `when`(downloadRepository.observe(1001L)).thenReturn(
            flowOf(AttachmentDownloadStatus.Downloading(1001L, 65))
        )

        val viewModel = NewsDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("newsId" to "42")),
            newsRepository = newsRepository,
            extractLinks = ExtractNewsLinksUseCase(),
            downloadRepository = downloadRepository
        )
        advanceUntilIdle()

        assertEquals(
            AttachmentDownloadStatus.Downloading(1001L, 65),
            viewModel.state.value.downloads[attachment.url]
        )
        verify(downloadRepository, never()).enqueue(attachment.url, attachment.title)
    }

    @Test
    fun `missing completed file becomes retryable after open`() = runTest(dispatcher) {
        val newsRepository = mock(NewsRepository::class.java)
        val downloadRepository = mock(AttachmentDownloadRepository::class.java)
        val attachment = NewsAttachment(title = "Материал", url = "https://example.test/file.pdf")
        val news = NewsItem(
            id = "42",
            title = "Новость",
            summary = "",
            body = "",
            publishedAt = null,
            attachments = listOf(attachment)
        )
        `when`(newsRepository.getNewsDetail("42")).thenReturn(AppResult.Success(news))
        `when`(downloadRepository.findDownloadId(attachment.url)).thenReturn(1001L)
        `when`(downloadRepository.observe(1001L)).thenReturn(
            flowOf(AttachmentDownloadStatus.Completed(1001L))
        )
        `when`(downloadRepository.open(1001L)).thenReturn(
            AppResult.Error("Скачанный файл не найден", FileNotFoundException())
        )
        val viewModel = NewsDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("newsId" to "42")),
            newsRepository = newsRepository,
            extractLinks = ExtractNewsLinksUseCase(),
            downloadRepository = downloadRepository
        )
        advanceUntilIdle()

        viewModel.openDownloaded(attachment.url)
        advanceUntilIdle()

        assertEquals(
            AttachmentDownloadStatus.Failed("Скачанный файл не найден"),
            viewModel.state.value.downloads[attachment.url]
        )
    }
}
