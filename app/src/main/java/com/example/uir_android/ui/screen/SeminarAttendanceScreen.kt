package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.ui.state.SeminarAttendanceMode
import com.example.uir_android.ui.state.SeminarAttendanceUiState
import com.example.uir_android.ui.state.SeminarStudentUiState
import com.example.uir_android.ui.viewmodel.SeminarAttendanceViewModel
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.event.handle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeminarAttendanceScreen(
    onBack: () -> Unit,
    viewModel: SeminarAttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { it.handle(snackbarHostState) }
    }

    if (state.showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSaveConfirmation,
            title = { Text("Сохранить ведомость?") },
            text = {
                Text("Будут сохранены присутствие и баллы. Присутствуют: ${state.presentCount} из ${state.students.size}.")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSave) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSaveConfirmation) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Посещаемость семинаров",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading && !state.isSaving
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Обновить")
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
                state.mode == SeminarAttendanceMode.DENIED -> Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> SeminarAttendanceContent(
                    state = state,
                    onSelectGroup = viewModel::selectGroup,
                    onSelectSeminar = viewModel::selectSeminar,
                    onTogglePresence = viewModel::togglePresence,
                    onPointsChanged = viewModel::updatePoints,
                    onSave = viewModel::requestSave,
                    onRetry = viewModel::refresh
                )
            }
        }
    }
}

@Composable
private fun SeminarAttendanceContent(
    state: SeminarAttendanceUiState,
    onSelectGroup: (Int) -> Unit,
    onSelectSeminar: (Int) -> Unit,
    onTogglePresence: (Int) -> Unit,
    onPointsChanged: (Int, String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = state.userName.ifBlank { state.role },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (state.userName.isNotBlank() && state.role.isNotBlank()) {
                Text(state.role, style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            SeminarSectionTitle("Группа")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.groups, key = { it.id }) { group ->
                    FilterChip(
                        selected = group.id == state.selectedGroupId,
                        onClick = { onSelectGroup(group.id) },
                        enabled = !state.isSaving,
                        label = { Text(group.name) }
                    )
                }
            }
        }

        item {
            SeminarSectionTitle("Семинар")
            if (state.seminars.isEmpty()) {
                SeminarEmptyCard(state.errorMessage ?: "Нет доступных семинаров", onRetry)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.seminars, key = { it.id }) { seminar ->
                        FilterChip(
                            selected = seminar.id == state.selectedSeminarId,
                            onClick = { onSelectSeminar(seminar.id) },
                            enabled = !state.isSaving,
                            label = { Text(seminar.title) }
                        )
                    }
                }
            }
        }

        if (state.selectedSeminarId != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeminarSectionTitle("Студенты")
                    Text(
                        text = "${state.presentCount} из ${state.students.size}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "Допустимые баллы: от -50 до 100. Максимум раздела дополнительно проверяет сервер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.students.isEmpty()) {
                item { SeminarEmptyCard("В выбранной группе нет студентов", onRetry) }
            } else {
                items(state.students, key = { it.seminarPassId }) { student ->
                    SeminarStudentCard(
                        student = student,
                        enabled = !state.isSaving,
                        onTogglePresence = { onTogglePresence(student.id) },
                        onPointsChanged = { onPointsChanged(student.id, it) }
                    )
                }
                item {
                    Button(
                        onClick = onSave,
                        enabled = state.canSave && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Сохранить ведомость")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeminarStudentCard(
    student: SeminarStudentUiState,
    enabled: Boolean,
    onTogglePresence: () -> Unit,
    onPointsChanged: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = student.pointsError?.let {
            BorderStroke(2.dp, MaterialTheme.colorScheme.error)
        },
        colors = CardDefaults.cardColors(
            containerColor = if (student.present) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = student.present,
                    onCheckedChange = { onTogglePresence() },
                    enabled = enabled
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(student.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (student.present) "Присутствует" else "Отсутствует",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            OutlinedTextField(
                value = student.pointsInput,
                onValueChange = onPointsChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled && student.present,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                label = { Text("Баллы за активность") },
                isError = student.pointsError != null,
                supportingText = student.pointsError?.let { message ->
                    { Text(message) }
                }
            )
        }
    }
}

@Composable
private fun SeminarEmptyCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(message)
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("Обновить", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SeminarSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}
