package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.domain.model.AcademicAccessPolicy
import com.example.uir_android.domain.model.AppThemeMode
import com.example.uir_android.domain.model.ProfileAttendance
import com.example.uir_android.domain.model.ProfileSectionProgress
import com.example.uir_android.core.util.openExternalUri
import com.example.uir_android.ui.component.LinkifiedText
import com.example.uir_android.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    isSubmitting: Boolean,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var testAttemptsExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(colors.surfaceVariant, colors.background)
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Профиль", style = MaterialTheme.typography.headlineMedium)
                    when {
                        state.isLoading -> CircularProgressIndicator()
                        state.profile != null -> {
                            val profile = state.profile!!
                            Text(
                                profile.user.displayName.ifBlank { "ФИО не указано" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            LinkifiedText(
                                text = profile.user.email,
                                onOpenLink = { openExternalUri(context, it) }
                            )
                            Text("Роль: ${profile.user.role.ifBlank { "Не назначена" }}")
                            Text("Группа: ${formatAcademicGroup(profile.groupName)}")
                        }
                        else -> {
                            Text(state.errorMessage ?: "Не удалось загрузить профиль")
                            OutlinedButton(onClick = viewModel::refresh) { Text("Повторить") }
                        }
                    }
                }
            }
        }

        state.profile?.warnings?.takeIf(List<String>::isNotEmpty)?.let { warnings ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = colors.errorContainer,
                        contentColor = colors.onErrorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Часть данных временно недоступна",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        warnings.forEach { warning -> Text(warning) }
                        OutlinedButton(
                            onClick = viewModel::refresh,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Повторить загрузку")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Тема приложения",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeChoice(
                            text = "Системная",
                            selected = themeMode == AppThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(AppThemeMode.SYSTEM) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ThemeChoice(
                            text = "Светлая",
                            selected = themeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ThemeChoice(
                            text = "Тёмная",
                            selected = themeMode == AppThemeMode.DARK,
                            onClick = { onThemeModeChange(AppThemeMode.DARK) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        state.profile?.let { profile ->
            val canViewOwnResults = AcademicAccessPolicy.canViewOwnTestResults(profile.user.academicRole)
            if (canViewOwnResults && profile.sections.isNotEmpty()) {
                item {
                    Text(
                        "Учебный прогресс",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(profile.sections, key = { "section-${it.number}" }) { section ->
                    ProfileProgressCard(section)
                }
            }

            if (canViewOwnResults && profile.gradeSummary != null) {
                item {
                    val summary = profile.gradeSummary
                    ProfileSectionCard("Оценки") {
                        ProfileValue("Разделы", summary.sectionsScore)
                        ProfileValue("Экзамен", summary.examScore)
                        ProfileValue("Общий результат", summary.totalScore)
                        ProfileValue("Итоговая оценка", summary.mark)
                    }
                }
            }

            if (canViewOwnResults) {
                item {
                    ProfileSectionCard("Попытки решения тестов") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Завершено: ${profile.testResults.size}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { testAttemptsExpanded = !testAttemptsExpanded }) {
                                Icon(
                                    imageVector = if (testAttemptsExpanded) Icons.Filled.ExpandLess
                                    else Icons.Filled.ExpandMore,
                                    contentDescription = if (testAttemptsExpanded) "Свернуть" else "Развернуть"
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = viewModel::refresh,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Обновление...", modifier = Modifier.padding(start = 8.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null
                                )
                                Text("Обновить результаты", modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        AnimatedVisibility(visible = testAttemptsExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (profile.testResults.isEmpty()) {
                                    Text("Завершённых тестов пока нет")
                                } else {
                                    profile.testResults.forEach { result ->
                                        val testName = result.testName.ifBlank { "Название не указано" }
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(testName, fontWeight = FontWeight.Bold)
                                                ProfileValue("Результат", result.score)
                                                ProfileValue("Оценка", result.mark)
                                                ProfileValue("Дата", result.completedAt)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (!testAttemptsExpanded && profile.testResults.isNotEmpty()) {
                            Text("Нажмите стрелку, чтобы посмотреть попытки")
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                OutlinedButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Выполняется выход", modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Text("Выйти из аккаунта")
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ProfileProgressCard(section: ProfileSectionProgress) {
    ProfileSectionCard("Раздел ${section.number}") {
        AttendanceStrip("Посещение лекций", section.lectures, showWorkPoints = false)
        AttendanceStrip("Семинары", section.seminars, showWorkPoints = true)
        if (section.scores.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    section.scores.forEach { score ->
                        ProfileValue(formatProfileScoreLabel(score.label), score.value)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceStrip(
    title: String,
    values: List<ProfileAttendance>,
    showWorkPoints: Boolean
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val successContainer = if (isDarkTheme) Color(0xFF173D2A) else Color(0xFFD8F3DC)
    val successContent = if (isDarkTheme) Color(0xFFB7F0C5) else Color(0xFF145A2A)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (values.isEmpty()) {
            Text("Данных пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                values.forEach { value ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (value.present) successContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (value.present) successContent
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("№${value.number} ${if (value.present) "✓" else "—"}")
                            if (showWorkPoints && value.workPoints.isNotBlank()) {
                                Text("${value.workPoints} балл.", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun formatProfileScoreLabel(label: String): String {
    val normalized = label.trim()
    return when {
        normalized.equals("ПЛ", ignoreCase = true) -> "Посещение лекций"
        normalized.equals("ПС", ignoreCase = true) -> "Посещение семинаров"
        normalized.equals("РС", ignoreCase = true) -> "Работа на семинарах"
        normalized.startsWith("КР", ignoreCase = true) ->
            normalized.replaceFirst(Regex("^КР", RegexOption.IGNORE_CASE), "Контрольная работа")
        else -> normalized
    }
}

@Composable
private fun ProfileSectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ProfileValue(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun ThemeChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        },
        modifier = modifier
    )
}

internal fun formatAcademicGroup(groupName: String): String {
    val compact = groupName.trim().replace(Regex("\\s+"), "")
    if (compact.isBlank()) return "Не назначена"
    val match = Regex("^([А-Яа-яA-Za-z])(\\d{2})-?(\\d{3,4})$").matchEntire(compact)
        ?: return groupName.trim()
    return "${match.groupValues[1].uppercase()}${match.groupValues[2]}-${match.groupValues[3]}"
}
