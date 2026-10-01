package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.ui.state.LectureAttendanceMode
import com.example.uir_android.ui.state.LectureAttendanceUiState
import com.example.uir_android.ui.state.LectureMatrixCellKey
import com.example.uir_android.ui.state.LectureSaveAction
import com.example.uir_android.ui.viewmodel.LectureAttendanceViewModel
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.event.handle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureAttendanceScreen(
    onBack: () -> Unit,
    viewModel: LectureAttendanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { it.handle(snackbarHostState) }
    }

    val pendingSaveAction = state.pendingSaveAction
    if (pendingSaveAction != null) {
        SaveAttendanceDialog(
            state = state,
            action = pendingSaveAction,
            onConfirm = viewModel::confirmSave,
            onDismiss = viewModel::dismissSaveConfirmation
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Посещаемость лекций",
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
                state.mode == LectureAttendanceMode.DENIED -> AccessDeniedContent(
                    message = state.errorMessage.orEmpty(),
                    modifier = Modifier.align(Alignment.Center)
                )
                state.mode == LectureAttendanceMode.ADMIN_MATRIX -> AdminLectureMatrix(
                    state = state,
                    onValueChanged = viewModel::updateMatrixLimit,
                    onSave = viewModel::saveMatrix
                )
                else -> AttendanceContent(
                    state = state,
                    onSelectGroup = viewModel::selectGroup,
                    onSelectLecture = viewModel::selectLecture,
                    onToggleStudent = viewModel::toggleStudent,
                    onLimitChanged = viewModel::updateLimitInput,
                    onSaveLimit = viewModel::requestLimitSave,
                    onSaveAttendance = viewModel::requestAttendanceSave,
                    onRetry = viewModel::refresh
                )
            }
        }
    }
}

@Composable
private fun AdminLectureMatrix(
    state: LectureAttendanceUiState,
    onValueChanged: (Int, Int, String) -> Unit,
    onSave: () -> Unit
) {
    val horizontalState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Матрица посещаемости", style = MaterialTheme.typography.titleLarge)
        Text(
            "Группы расположены по строкам, недели по столбцам",
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.groups.isEmpty() || state.matrixColumns.isEmpty()) {
            EmptyOrErrorCard(state.errorMessage ?: "Нет данных для матрицы", onRetry = {})
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Группа",
                            modifier = Modifier
                                .width(120.dp)
                                .padding(horizontal = 10.dp),
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(horizontalState),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.matrixColumns.forEach { column ->
                                Text(
                                    column.title,
                                    modifier = Modifier.width(104.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.groups, key = { it.id }) { group ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    group.name,
                                    modifier = Modifier
                                        .width(120.dp)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 10.dp, vertical = 12.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(horizontalState),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    state.matrixColumns.forEach { column ->
                                        val key = LectureMatrixCellKey(group.id, column.lectureId)
                                        val current = state.matrixCurrentCounts[key] ?: 0
                                        OutlinedTextField(
                                            value = state.matrixValues[key].orEmpty(),
                                            onValueChange = {
                                                onValueChanged(group.id, column.lectureId, it)
                                            },
                                            modifier = Modifier.width(104.dp),
                                            enabled = !state.isSaving,
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number
                                            ),
                                            label = { Text("Сейчас $current") }
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = !state.isSaving && state.matrixValues != state.initialMatrixValues,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "Сохранение..." else "Сохранить матрицу")
            }
        }
    }
}

@Composable
private fun AttendanceContent(
    state: LectureAttendanceUiState,
    onSelectGroup: (Int) -> Unit,
    onSelectLecture: (Int) -> Unit,
    onToggleStudent: (Int) -> Unit,
    onLimitChanged: (String) -> Unit,
    onSaveLimit: () -> Unit,
    onSaveAttendance: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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

        if (state.mode == LectureAttendanceMode.EDITOR) {
            item {
                SectionTitle("Группа")
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
        }

        item {
            SectionTitle("Лекция")
            if (state.lectures.isEmpty()) {
                EmptyOrErrorCard(
                    message = state.errorMessage ?: "Нет доступных лекций",
                    onRetry = onRetry
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(
                        items = state.lectures,
                        key = { _, lecture -> lecture.id }
                    ) { index, lecture ->
                        FilterChip(
                            selected = lecture.id == state.selectedLectureId,
                            onClick = { onSelectLecture(lecture.id) },
                            enabled = !state.isSaving,
                            label = {
                                Text(
                                    if (state.mode == LectureAttendanceMode.STEWARD) {
                                        "№${index + 1} ${lecture.title}"
                                    } else {
                                        lecture.title
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }

        if (state.selectedLectureId != null) {
            item {
                AttendanceLimitCard(
                    state = state,
                    onLimitChanged = onLimitChanged,
                    onSaveLimit = onSaveLimit
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Студенты")
                    Text(
                        text = if (state.mode == LectureAttendanceMode.STEWARD) {
                            "${state.selectedCount} из ${state.requiredCount ?: 0}"
                        } else {
                            "${state.selectedCount} из ${state.students.size}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (
                            state.mode == LectureAttendanceMode.STEWARD &&
                            state.selectedCount == state.requiredCount
                        ) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = state.selectionMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.canSaveAttendance) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (state.students.isEmpty()) {
                item { EmptyOrErrorCard("В выбранной группе нет студентов", onRetry) }
            } else {
                items(state.students, key = { it.id }) { student ->
                    StudentAttendanceRow(
                        name = student.name,
                        selected = student.selected,
                        enabled = !state.isSaving,
                        onClick = { onToggleStudent(student.id) }
                    )
                }
                item {
                    Button(
                        onClick = onSaveAttendance,
                        enabled = state.canSaveAttendance && !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Сохранить посещаемость")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AttendanceLimitCard(
    state: LectureAttendanceUiState,
    onLimitChanged: (String) -> Unit,
    onSaveLimit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.mode == LectureAttendanceMode.STEWARD || !state.canEditLimit) {
                Text("Установлено преподавателем", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = state.requiredCount?.toString() ?: "Не установлено",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text("Количество присутствующих", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.limitInput,
                        onValueChange = onLimitChanged,
                        modifier = Modifier.weight(1f),
                        enabled = !state.isSaving,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Всего") }
                    )
                    Button(
                        onClick = onSaveLimit,
                        enabled = !state.isSaving && state.limitInput.isNotBlank()
                    ) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentAttendanceRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                enabled = enabled
            )
            Text(
                text = name,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun EmptyOrErrorCard(message: String, onRetry: () -> Unit) {
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
private fun AccessDeniedContent(message: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(24.dp)) {
        Text(
            text = message,
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SaveAttendanceDialog(
    state: LectureAttendanceUiState,
    action: LectureSaveAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isLimit = action == LectureSaveAction.LIMIT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isLimit) "Сохранить количество?" else "Сохранить посещаемость?") },
        text = {
            Text(
                if (isLimit) {
                    "Для выбранной группы будет установлено: ${state.limitInput}."
                } else {
                    "Будут сохранены отметки для ${state.selectedCount} студентов."
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
