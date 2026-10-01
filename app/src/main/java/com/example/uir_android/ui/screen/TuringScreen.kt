package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.domain.turing.BLANK_INPUT_TOKEN
import com.example.uir_android.domain.turing.OMEGA_INPUT_TOKEN
import com.example.uir_android.domain.turing.PARTIAL_INPUT_TOKEN
import com.example.uir_android.ui.component.RunsHistory
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.component.TapeWindow
import com.example.uir_android.ui.event.handle
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.ui.state.CommandSymbolFieldTarget
import com.example.uir_android.ui.state.ProgramCommandRowUiState
import com.example.uir_android.ui.viewmodel.TuringViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuringScreen(
    onBack: () -> Unit,
    viewModel: TuringViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var programNameInput by rememberSaveable { mutableStateOf("") }
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        viewModel.clearSymbolFieldSelection()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { it.handle(snackbarHostState) }
    }

    Scaffold(
        topBar = {
            Column {
                AppTopBar(
                    title = "Эмулятор машины Тьюринга",
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { showHelp = true }) {
                            Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Помощь")
                        }
                    }
                )
                PersistentSymbolInputBar(
                    enabled = state.focusedSymbolField != null || state.isInputTapeFocused,
                    addCommandEnabled = true,
                    onInsertLambda = { viewModel.insertSpecialToken(BLANK_INPUT_TOKEN) },
                    onInsertOmega = { viewModel.insertSpecialToken(OMEGA_INPUT_TOKEN) },
                    onInsertPartial = { viewModel.insertSpecialToken(PARTIAL_INPUT_TOKEN) },
                    onAddCommand = viewModel::addCommandRow,
                    onHideKeyboard = dismissKeyboard
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .imePadding(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TapeArrowButton(text = "<", onClick = { viewModel.shiftTapeWindow(-1) })
                        TapeWindow(cells = state.tapeWindow, modifier = Modifier.weight(1f))
                        TapeArrowButton(text = ">", onClick = { viewModel.shiftTapeWindow(1) })
                    }
                }
            }

            item {
                TopControlPanel(
                    word = state.inputTape,
                    alphabet = state.alphabetText,
                    stateName = state.currentState,
                    isRunning = state.isRunning,
                    onWordChange = viewModel::updateInputTape,
                    onAlphabetChange = viewModel::updateAlphabetText,
                    onWordFocused = viewModel::selectInputTapeField,
                    onAlphabetFocused = viewModel::clearSymbolFieldSelection,
                    onPlaceOnTape = viewModel::placeOnTape,
                    onClearTape = viewModel::clearTape,
                    onCheckSyntax = viewModel::checkSyntax,
                    onReset = viewModel::reset,
                    onStep = viewModel::step,
                    onRun = viewModel::run
                )
            }

            item {
                ProgramEditorCard(
                    rows = state.programRows,
                    onRemoveRow = viewModel::removeCommandRow,
                    onMoveRowUp = viewModel::moveCommandRowUp,
                    onMoveRowDown = viewModel::moveCommandRowDown,
                    onLeftRuleChange = viewModel::updateCommandLeftRule,
                    onRightRuleChange = viewModel::updateCommandRightRule,
                    onReadFocused = { rowId -> viewModel.selectSymbolField(rowId, CommandSymbolFieldTarget.READ) },
                    onWriteFocused = { rowId -> viewModel.selectSymbolField(rowId, CommandSymbolFieldTarget.WRITE) },
                    onNonSymbolFieldFocused = viewModel::clearSymbolFieldSelection,
                    onSave = {
                        programNameInput = state.programName
                        showSaveDialog = true
                    },
                    onClearProgram = viewModel::clearAll,
                    savedPrograms = state.availablePrograms,
                    onLoadProgram = viewModel::loadProgram
                )
            }

            item {
                StatusBlock(
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage
                )
            }

            if (state.recentRuns.isNotEmpty()) {
                item {
                    InfoCard(title = "Запуски") {
                        RunsHistory(runs = state.recentRuns)
                    }
                }
            }

        }
    }

    if (showHelp) {
        TuringHelpDialog(onDismiss = { showHelp = false })
    }
    if (showSaveDialog) {
        SaveProgramDialog(
            name = programNameInput,
            onNameChange = { programNameInput = it },
            onSave = {
                viewModel.saveProgram(programNameInput)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

@Composable
private fun SaveProgramDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранить алгоритм") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название алгоритма") },
                supportingText = {
                    if (name.isBlank()) Text("Введите название")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (name.isNotBlank()) onSave() }
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = name.isNotBlank()) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun TuringHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Помощь") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Формат команды")
                Text(
                    text = "<состояние> <символ> → <символ> <L|R|S> <состояние>",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                Text("Пример: S0 a → b R S0", fontFamily = FontFamily.Monospace)
                Text("L — шаг влево, R — вправо, S — остаться на месте, H — остановить машину.")
                Text("Короткая правая часть L, R, S или H сохраняет прочитанный символ и текущее состояние.")
                Text("λ вводится как \\l, Ω как \\o, ∂ как \\d. Пробелы внутри символов не используются.")
                Text("Сначала задайте алфавит и команды, нажмите «Проверить синтаксис», затем поместите слово на ленту.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Понятно") }
        }
    )
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun TopControlPanel(
    word: String,
    alphabet: String,
    stateName: String,
    isRunning: Boolean,
    enabled: Boolean = true,
    onWordChange: (String) -> Unit,
    onAlphabetChange: (String) -> Unit,
    onWordFocused: () -> Unit,
    onAlphabetFocused: () -> Unit,
    onPlaceOnTape: () -> Unit,
    onClearTape: () -> Unit,
    onCheckSyntax: () -> Unit,
    onReset: () -> Unit,
    onStep: () -> Unit,
    onRun: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val wideLayout = maxWidth > 980.dp
            if (wideLayout) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LabeledTopField(
                            modifier = Modifier.weight(1f),
                            label = "Слово:",
                            value = word,
                            onValueChange = onWordChange,
                            onFocused = onWordFocused,
                            enabled = enabled
                        )
                        CommandButton("ПОМЕСТИТЬ НА ЛЕНТУ", Modifier.weight(0.9f), enabled) {
                            dismissKeyboard()
                            onPlaceOnTape()
                        }
                        CommandButton("ОЧИСТИТЬ ЛЕНТУ", Modifier.weight(0.9f), enabled) {
                            dismissKeyboard()
                            onClearTape()
                        }
                        StateInfo(stateName = stateName, modifier = Modifier.width(150.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LabeledTopField(
                            label = "Алфавит:",
                            value = alphabet,
                            onValueChange = onAlphabetChange,
                            onFocused = onAlphabetFocused,
                            enabled = enabled
                        )
                        CommandButton("ПРОВЕРИТЬ СИНТАКСИС", Modifier.weight(0.9f), enabled) {
                            dismissKeyboard()
                            onCheckSyntax()
                        }
                        CommandButton("НАЧАТЬ ЗАНОВО", Modifier.weight(0.9f), enabled) {
                            dismissKeyboard()
                            onReset()
                        }
                        CommandButton("СДЕЛАТЬ ШАГ", Modifier.weight(0.8f), enabled && !isRunning) {
                            dismissKeyboard()
                            onStep()
                        }
                        CommandButton("ЗАПУСТИТЬ ДО КОНЦА", Modifier.weight(0.9f), enabled && !isRunning) {
                            dismissKeyboard()
                            onRun()
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledTopField(
                        label = "Слово:",
                        value = word,
                        onValueChange = onWordChange,
                        onFocused = onWordFocused,
                        enabled = enabled
                    )
                    LabeledTopField(
                        label = "Алфавит:",
                        value = alphabet,
                        onValueChange = onAlphabetChange,
                        onFocused = onAlphabetFocused,
                        enabled = enabled
                    )
                    StateInfo(stateName = stateName)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommandButton("ПОМЕСТИТЬ НА ЛЕНТУ", Modifier.weight(1f), enabled) {
                            dismissKeyboard()
                            onPlaceOnTape()
                        }
                        CommandButton("ОЧИСТИТЬ ЛЕНТУ", Modifier.weight(1f), enabled) {
                            dismissKeyboard()
                            onClearTape()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommandButton("ПРОВЕРИТЬ СИНТАКСИС", Modifier.weight(1f), enabled) {
                            dismissKeyboard()
                            onCheckSyntax()
                        }
                        CommandButton("НАЧАТЬ ЗАНОВО", Modifier.weight(1f), enabled) {
                            dismissKeyboard()
                            onReset()
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommandButton("СДЕЛАТЬ ШАГ", Modifier.weight(1f), enabled && !isRunning) {
                            dismissKeyboard()
                            onStep()
                        }
                        CommandButton("ЗАПУСТИТЬ ДО КОНЦА", Modifier.weight(1f), enabled && !isRunning) {
                            dismissKeyboard()
                            onRun()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProgramEditorCard(
    rows: List<ProgramCommandRowUiState>,
    onRemoveRow: (Long) -> Unit,
    onMoveRowUp: (Long) -> Unit,
    onMoveRowDown: (Long) -> Unit,
    onLeftRuleChange: (Long, String) -> Unit,
    onRightRuleChange: (Long, String) -> Unit,
    onReadFocused: (Long) -> Unit,
    onWriteFocused: (Long) -> Unit,
    onNonSymbolFieldFocused: () -> Unit,
    onSave: () -> Unit,
    onClearProgram: () -> Unit,
    enabled: Boolean = true,
    savedPrograms: List<TmProgram>? = null,
    onLoadProgram: ((TmProgram) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var programsExpanded by rememberSaveable { mutableStateOf(false) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                rows.forEach { row ->
                    CommandRowLine(
                        row = row,
                        enabled = enabled,
                        onRemove = {
                            dismissKeyboard()
                            onRemoveRow(row.id)
                        },
                        onMoveUp = {
                            dismissKeyboard()
                            onMoveRowUp(row.id)
                        },
                        onMoveDown = {
                            dismissKeyboard()
                            onMoveRowDown(row.id)
                        },
                        onLeftRuleChange = { onLeftRuleChange(row.id, it) },
                        onRightRuleChange = { onRightRuleChange(row.id, it) },
                        onReadFocused = { onReadFocused(row.id) },
                        onWriteFocused = { onWriteFocused(row.id) },
                        onNonSymbolFieldFocused = onNonSymbolFieldFocused
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = {
                    dismissKeyboard()
                    onSave()
                }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Сохранить программу")
                }
                OutlinedButton(onClick = {
                    dismissKeyboard()
                    showClearConfirmation = true
                }, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Очистить программу")
                }
            }

            if (savedPrograms != null && onLoadProgram != null) {
                OutlinedButton(
                    onClick = {
                        dismissKeyboard()
                        programsExpanded = !programsExpanded
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (programsExpanded) "Скрыть сохранённые алгоритмы"
                        else "Сохранённые алгоритмы (${savedPrograms.size})"
                    )
                }

                if (programsExpanded) {
                    if (savedPrograms.isEmpty()) {
                        Text(
                            text = "Сохранённых алгоритмов пока нет",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedPrograms.forEach { program ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dismissKeyboard()
                                            onLoadProgram(program)
                                            programsExpanded = false
                                        },
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(program.name, fontWeight = FontWeight.SemiBold)
                                        if (program.description.isNotBlank()) {
                                            Text(
                                                text = program.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Очистить алгоритм?") },
            text = { Text("Все введённые команды будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClearProgram()
                    }
                ) {
                    Text("Очистить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
internal fun PersistentSymbolInputBar(
    enabled: Boolean,
    addCommandEnabled: Boolean,
    onInsertLambda: () -> Unit,
    onInsertOmega: () -> Unit,
    onInsertPartial: () -> Unit,
    onAddCommand: () -> Unit,
    onHideKeyboard: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TokenInsertButton("λ", enabled, onInsertLambda)
            TokenInsertButton("Ω", enabled, onInsertOmega)
            TokenInsertButton("∂", enabled, onInsertPartial)
            FilledIconButton(
                onClick = onAddCommand,
                enabled = addCommandEnabled,
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить команду")
            }
            IconButton(onClick = onHideKeyboard) {
                Icon(Icons.Filled.KeyboardHide, contentDescription = "Скрыть клавиатуру")
            }
        }
    }
}

@Composable
private fun CommandRowLine(
    row: ProgramCommandRowUiState,
    enabled: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onLeftRuleChange: (String) -> Unit,
    onRightRuleChange: (String) -> Unit,
    onReadFocused: () -> Unit,
    onWriteFocused: () -> Unit,
    onNonSymbolFieldFocused: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuleGroupField(modifier = Modifier.weight(1f)) {
            InlineTextField(
                value = row.leftRuleText,
                placeholder = "",
                onValueChange = onLeftRuleChange,
                onFocused = onReadFocused,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = "→",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        RuleGroupField(modifier = Modifier.weight(1.35f)) {
            InlineTextField(
                value = row.rightRuleText,
                placeholder = "",
                onValueChange = onRightRuleChange,
                onFocused = onWriteFocused,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveUp, enabled = enabled) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Вверх",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoveDown, enabled = enabled) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Вниз",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RuleGroupField(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Composable
private fun InlineTextField(
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            onValueChange(it.text)
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus()
            keyboardController?.hide()
        }),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = modifier
            .heightIn(min = 24.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { innerTextField ->
            if (value.isBlank() && placeholder.isNotBlank()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            innerTextField()
        }
    )
}

@Composable
private fun InlineSymbolField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .heightIn(min = 24.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { innerTextField ->
            if (value.isBlank()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            innerTextField()
        }
    )
}

@Composable
private fun LabeledTopField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    enabled: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, selection = TextRange(value.length))
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(70.dp))
        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onValueChange(it.text)
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocused() },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
            }),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
    }
}

@Composable
private fun StateInfo(
    stateName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text("Состояние:", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = stateName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TokenInsertButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, enabled = enabled) {
        Text(text)
    }
}

@Composable
internal fun StatusBlock(
    statusMessage: String?,
    errorMessage: String?
) {
    if (statusMessage.isNullOrBlank() && errorMessage.isNullOrBlank()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!statusMessage.isNullOrBlank()) {
                Text(statusMessage, color = MaterialTheme.colorScheme.primary)
            }
            if (!errorMessage.isNullOrBlank()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CommandButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = TextStyle(
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
internal fun TapeArrowButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(width = 44.dp, height = 56.dp)
    ) {
        Text(text, style = MaterialTheme.typography.headlineSmall)
    }
}
