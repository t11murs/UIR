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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.domain.model.NewsItem
import com.example.uir_android.core.util.openExternalUri
import com.example.uir_android.ui.component.LinkifiedText
import com.example.uir_android.ui.viewmodel.HomeViewModel
import com.example.uir_android.ui.viewmodel.MenuViewModel

@Composable
fun HomeScreen(
    email: String,
    onOpenNews: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val fallbackName = email.substringBefore('@').trim().ifBlank { "пользователь" }
    val displayName = state.userName.ifBlank { fallbackName }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(colors.surfaceVariant, colors.background)
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            WelcomeCard(displayName)
        }
        item {
            CourseInformationCard()
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Новости",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = viewModel::refresh,
                    enabled = !state.isLoading && !state.isRefreshing
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить новости")
                    }
                }
            }
        }
        item {
            DevelopmentFeedbackCard()
        }
        if (state.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.primary)
                }
            }
        } else {
            state.errorMessage?.let { message ->
                item {
                    HomeNewsMessage(
                        message = message,
                        buttonText = "Повторить",
                        onClick = viewModel::refresh
                    )
                }
            }
            if (state.news.isEmpty() && state.errorMessage == null) {
                item {
                    HomeNewsMessage(
                        message = "Новостей пока нет",
                        buttonText = "Обновить",
                        onClick = viewModel::refresh
                    )
                }
            }
            items(state.news, key = { it.id }) { news ->
                HomeNewsCard(news = news, onClick = { onOpenNews(news.id) })
            }
        }
    }
}

@Composable
private fun DevelopmentFeedbackCard() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)
    ) {
        LinkifiedText(
            text = "Это приложение сейчас на этапе разработки, просьба присылать любую обратную связь в Telegram: @t1murs",
            onOpenLink = { openExternalUri(context, it) },
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onPrimaryContainer,
            linkColor = colors.primary
        )
    }
}

@Composable
private fun WelcomeCard(displayName: String) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(colors.primary, colors.tertiary)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "AT",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Algorithms Theory",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.secondary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Добро пожаловать, $displayName!",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CourseInformationCard() {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Система поддержки обучения по курсу «Теория алгоритмов» позволяет:",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            CourseFeature("проходить тренировочные и контрольные тесты")
            CourseFeature("работать с эмулятором машины Тьюринга")
            CourseFeature("отмечать посещаемость лекций и семинаров")
            CourseFeature("отслеживать результаты в личном кабинете")
            LinkifiedText(
                text = "Вопросы и предложения: algorithms.theory@yandex.ru",
                onOpenLink = { openExternalUri(context, it) },
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
                linkColor = colors.primary
            )
        }
    }
}

@Composable
private fun CourseFeature(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", color = colors.secondary, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
    }
}

@Composable
private fun HomeNewsCard(news: NewsItem, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.secondary)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = news.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val preview = news.summary.ifBlank { news.body }
                if (preview.isNotBlank()) {
                    LinkifiedText(
                        text = preview,
                        onOpenLink = { openExternalUri(context, it) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 4
                    )
                }
                news.publishedAt?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (news.attachments.isNotEmpty()) {
                    Button(onClick = onClick) {
                        Icon(Icons.Outlined.AttachFile, contentDescription = null)
                        Text(
                            text = if (news.attachments.size == 1) "Скачать файл" else "Открыть вложения",
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                } else {
                    OutlinedButton(onClick = onClick) {
                        Text("Подробнее")
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeNewsMessage(
    message: String,
    buttonText: String,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(message, textAlign = TextAlign.Center)
            OutlinedButton(onClick = onClick) { Text(buttonText) }
        }
    }
}

@Composable
fun MenuScreen(
    onOpenEmulator: () -> Unit,
    onOpenTests: () -> Unit,
    onOpenLectureAttendance: () -> Unit,
    onOpenSeminarAttendance: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(colors.surfaceVariant, colors.background)
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Меню",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground
        )
        MenuCard(
            title = "Эмулятор машины Тьюринга",
            icon = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
            onClick = onOpenEmulator
        )
        MenuCard(
            title = "Тесты",
            icon = { Icon(Icons.AutoMirrored.Outlined.Assignment, contentDescription = null) },
            onClick = onOpenTests
        )
        if (state.canViewLectureAttendance) {
            MenuCard(
                title = "Посещаемость лекций",
                icon = { Icon(Icons.AutoMirrored.Outlined.FactCheck, contentDescription = null) },
                onClick = onOpenLectureAttendance
            )
        }
        if (state.canViewSeminarAttendance) {
            MenuCard(
                title = "Посещаемость семинаров",
                icon = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                onClick = onOpenSeminarAttendance
            )
        }
    }
}

@Composable
private fun MenuCard(
    title: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(colors.primary, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides colors.onPrimary
                ) {
                    icon()
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface
            )
        }
    }
}
