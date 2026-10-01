package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.domain.model.AttachmentDownloadStatus
import com.example.uir_android.domain.model.NewsAttachment
import com.example.uir_android.domain.model.NewsItem
import com.example.uir_android.core.util.openExternalUri
import com.example.uir_android.ui.component.LinkifiedText
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.event.handle
import com.example.uir_android.ui.state.NewsDetailUiState
import com.example.uir_android.ui.viewmodel.NewsDetailViewModel
import com.example.uir_android.ui.viewmodel.NewsListViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(
    onOpenNews: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: NewsListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Новости",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading && !state.isRefreshing
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.errorMessage != null && state.items.isEmpty() -> NewsMessageCard(
                    message = state.errorMessage.orEmpty(),
                    buttonText = "Повторить",
                    onClick = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.items.isEmpty() -> NewsMessageCard(
                    message = "Новостей пока нет",
                    buttonText = "Обновить",
                    onClick = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state.errorMessage != null) {
                        item {
                            NewsInlineError(state.errorMessage.orEmpty(), viewModel::refresh)
                        }
                    }
                    items(state.items, key = { it.id }) { news ->
                        NewsListCard(news = news, onClick = { onOpenNews(news.id) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(
    onBack: () -> Unit,
    viewModel: NewsDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { it.handle(snackbarHostState) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(title = "Новость", onBack = onBack)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.errorMessage != null -> NewsMessageCard(
                    message = state.errorMessage.orEmpty(),
                    buttonText = "Повторить",
                    onClick = viewModel::retryLoad,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.news != null -> NewsDetailContent(
                    state = state,
                    onOpenUrl = { url ->
                        if (!openExternalUri(context, url)) {
                            scope.launch { snackbarHostState.showSnackbar("Не удалось открыть ссылку") }
                        }
                    },
                    onDownload = viewModel::startDownload,
                    onCancel = viewModel::cancelDownload,
                    onOpenDownloaded = viewModel::openDownloaded
                )
            }
        }
    }
}

@Composable
private fun NewsListCard(news: NewsItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = news.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = news.publishedAt ?: "Дата публикации не указана",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (news.summary.isNotBlank()) {
                LinkifiedText(
                    text = news.summary,
                    onOpenLink = { openExternalUri(context, it) },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (news.attachments.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.AttachFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Вложений: ${news.attachments.size}",
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsDetailContent(
    state: NewsDetailUiState,
    onOpenUrl: (String) -> Unit,
    onDownload: (NewsAttachment) -> Unit,
    onCancel: (String) -> Unit,
    onOpenDownloaded: (String) -> Unit
) {
    val news = state.news ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = news.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = news.publishedAt ?: "Дата публикации не указана",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                SelectionContainer {
                    LinkifiedText(
                        text = news.body,
                        onOpenLink = onOpenUrl,
                        modifier = Modifier.padding(18.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        if (state.links.isNotEmpty()) {
            item { NewsSectionTitle("Ссылки") }
            items(state.links, key = { it }) { link ->
                OutlinedButton(onClick = { onOpenUrl(link) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, contentDescription = null)
                    Text(link, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (news.attachments.isNotEmpty()) {
            item { NewsSectionTitle("Прикреплённые файлы") }
            items(news.attachments, key = { it.url }) { attachment ->
                NewsAttachmentCard(
                    attachment = attachment,
                    status = state.downloads[attachment.url],
                    onOpenUrl = { onOpenUrl(attachment.url) },
                    onDownload = { onDownload(attachment) },
                    onCancel = { onCancel(attachment.url) },
                    onOpenDownloaded = { onOpenDownloaded(attachment.url) }
                )
            }
        }
    }
}

@Composable
private fun NewsAttachmentCard(
    attachment: NewsAttachment,
    status: AttachmentDownloadStatus?,
    onOpenUrl: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenDownloaded: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = attachment.title,
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            when (status) {
                is AttachmentDownloadStatus.Downloading -> {
                    if (status.progressPercent != null) {
                        LinearProgressIndicator(
                            progress = { status.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Скачано: ${status.progressPercent}%")
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text("Скачивание...")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Отменить")
                    }
                }
                is AttachmentDownloadStatus.Completed -> {
                    Button(onClick = onOpenDownloaded, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                        Text("Открыть файл", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                is AttachmentDownloadStatus.Failed -> {
                    Text(status.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null)
                        Text("Повторить", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                null -> Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text("Скачать", modifier = Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(onClick = onOpenUrl, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                Text("Открыть ссылку", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun NewsInlineError(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(message, modifier = Modifier.weight(1f))
            IconButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Повторить")
            }
        }
    }
}

@Composable
private fun NewsMessageCard(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.padding(24.dp)) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
private fun NewsSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}
