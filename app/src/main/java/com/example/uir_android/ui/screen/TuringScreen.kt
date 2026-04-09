package com.example.uir_android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.core.util.BLANK_INPUT_TOKEN
import com.example.uir_android.core.util.OMEGA_INPUT_TOKEN
import com.example.uir_android.core.util.PARTIAL_INPUT_TOKEN
import com.example.uir_android.ui.component.RunsHistory
import com.example.uir_android.ui.component.SavedProgramsDialog
import com.example.uir_android.ui.component.TapeWindow
import com.example.uir_android.ui.component.TraceLog
import com.example.uir_android.ui.state.CommandSymbolFieldTarget
import com.example.uir_android.ui.state.ProgramCommandRowUiState
import com.example.uir_android.ui.viewmodel.TuringViewModel
import kotlinx.coroutines.flow.collectLatest

private val WorkspaceBackground = Color(0xFFF0F0F0)
private val CommandTeal = Color(0xFF189B97)
private val CommandGreen = Color(0xFF0C8C19)
private val SoftCardColor = Color.White
private val DisabledGray = Color(0xFFE0E0E0)
private val LineColor = Color(0xFFD7D7D7)
private val ActionIconColor = Color(0xFF8A8A8A)
private val HintTextColor = Color(0xFF6C6C6C)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TuringScreen(
    viewModel: TuringViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val exampleProgram = state.availablePrograms.firstOrNull { it.name == "Инверсия слова" }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(WorkspaceBackground)
                .padding(innerPadding)
                .padding(16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SoftCardColor,
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
                    tokenButtonsEnabled = state.focusedSymbolField != null || state.isInputTapeFocused,
                    onAddRow = viewModel::addCommandRow,
                    onRemoveRow = viewModel::removeCommandRow,
                    onMoveRowUp = viewModel::moveCommandRowUp,
                    onMoveRowDown = viewModel::moveCommandRowDown,
                    onLeftRuleChange = viewModel::updateCommandLeftRule,
                    onRightRuleChange = viewModel::updateCommandRightRule,
                    onReadFocused = { rowId -> viewModel.selectSymbolField(rowId, CommandSymbolFieldTarget.READ) },
                    onWriteFocused = { rowId -> viewModel.selectSymbolField(rowId, CommandSymbolFieldTarget.WRITE) },
                    onNonSymbolFieldFocused = viewModel::clearSymbolFieldSelection,
                    onInsertLambda = { viewModel.insertSpecialToken(BLANK_INPUT_TOKEN) },
                    onInsertOmega = { viewModel.insertSpecialToken(OMEGA_INPUT_TOKEN) },
                    onInsertPartial = { viewModel.insertSpecialToken(PARTIAL_INPUT_TOKEN) },
                    onSave = viewModel::saveProgram,
                    onClearProgram = viewModel::clearAll
                )
            }

            item {
                StatusBlock(
                    statusMessage = state.statusMessage,
                    errorMessage = state.errorMessage
                )
            }

            if (state.traceList.isNotEmpty()) {
                item {
                    InfoCard(title = "Шаги") {
                        TraceLog(trace = state.traceList)
                    }
                }
            }

            if (state.recentRuns.isNotEmpty()) {
                item {
                    InfoCard(title = "Запуски") {
                        RunsHistory(runs = state.recentRuns)
                    }
                }
            }

            if (state.showLoadDialog) {
                item {
                    SavedProgramsDialog(
                        programs = state.availablePrograms,
                        onSelect = viewModel::loadProgram,
                        onDismiss = viewModel::hideLoadDialog
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftCardColor)
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
private fun TopControlPanel(
    word: String,
    alphabet: String,
    stateName: String,
    isRunning: Boolean,
    onWordChange: (String) -> Unit,
    onAlphabetChange: (String) -> Unit,
    onWordFocused: () -> Unit,
    onPlaceOnTape: () -> Unit,
    onClearTape: () -> Unit,
    onCheckSyntax: () -> Unit,
    onReset: () -> Unit,
    onStep: () -> Unit,
    onRun: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftCardColor)
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
                            onFocused = onWordFocused
                        )
                        CommandButton("ПОМЕСТИТЬ НА ЛЕНТУ", Modifier.weight(0.9f), true, onPlaceOnTape)
                        CommandButton("ОЧИСТИТЬ ЛЕНТУ", Modifier.weight(0.9f), true, onClearTape)
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
                            onValueChange = onAlphabetChange
                        )
                        CommandButton("ПРОВЕРИТЬ СИНТАКСИС", Modifier.weight(0.9f), true, onCheckSyntax)
                        CommandButton("НАЧАТЬ ЗАНОВО", Modifier.weight(0.9f), true, onReset)
                        CommandButton("СДЕЛАТЬ ШАГ", Modifier.weight(0.8f), !isRunning, onStep)
                        CommandButton("ЗАПУСТИТЬ ДО КОНЦА", Modifier.weight(0.9f), !isRunning, onRun)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledTopField(
                        label = "Слово:",
                        value = word,
                        onValueChange = onWordChange,
                        onFocused = onWordFocused
                    )
                    LabeledTopField(
                        label = "Алфавит:",
                        value = alphabet,
                        onValueChange = onAlphabetChange
                    )
                    StateInfo(stateName = stateName)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommandButton("ПОМЕСТИТЬ НА ЛЕНТУ", Modifier.weight(1f), true, onPlaceOnTape)
                        CommandButton("ОЧИСТИТЬ ЛЕНТУ", Modifier.weight(1f), true, onClearTape)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommandButton("ПРОВЕРИТЬ СИНТАКСИС", Modifier.weight(1f), true, onCheckSyntax)
                        CommandButton("НАЧАТЬ ЗАНОВО", Modifier.weight(1f), true, onReset)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommandButton("СДЕЛАТЬ ШАГ", Modifier.weight(1f), !isRunning, onStep)
                        CommandButton("ЗАПУСТИТЬ ДО КОНЦА", Modifier.weight(1f), !isRunning, onRun)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramEditorCard(
    rows: List<ProgramCommandRowUiState>,
    tokenButtonsEnabled: Boolean,
    onAddRow: () -> Unit,
    onRemoveRow: (Long) -> Unit,
    onMoveRowUp: (Long) -> Unit,
    onMoveRowDown: (Long) -> Unit,
    onLeftRuleChange: (Long, String) -> Unit,
    onRightRuleChange: (Long, String) -> Unit,
    onReadFocused: (Long) -> Unit,
    onWriteFocused: (Long) -> Unit,
    onNonSymbolFieldFocused: () -> Unit,
    onInsertLambda: () -> Unit,
    onInsertOmega: () -> Unit,
    onInsertPartial: () -> Unit,
    onSave: () -> Unit,
    onClearProgram: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftCardColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Button(
                    onClick = onAddRow,
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CommandTeal,
                        contentColor = Color.White
                    )
                ) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(items = rows, key = { row -> row.id }) { row ->
                    CommandRowLine(
                        row = row,
                        onRemove = { onRemoveRow(row.id) },
                        onMoveUp = { onMoveRowUp(row.id) },
                        onMoveDown = { onMoveRowDown(row.id) },
                        onLeftRuleChange = { onLeftRuleChange(row.id, it) },
                        onRightRuleChange = { onRightRuleChange(row.id, it) },
                        onReadFocused = { onReadFocused(row.id) },
                        onWriteFocused = { onWriteFocused(row.id) },
                        onNonSymbolFieldFocused = onNonSymbolFieldFocused
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TokenInsertButton("λ", tokenButtonsEnabled, onInsertLambda)
                TokenInsertButton("Ω", tokenButtonsEnabled, onInsertOmega)
                TokenInsertButton("∂", tokenButtonsEnabled, onInsertPartial)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Для ввода λ используйте \"\\l\".", color = HintTextColor)
                Text("Для ввода Ω используйте \"\\o\".", color = HintTextColor)
                Text("Для ввода ∂ используйте \"\\d\".", color = HintTextColor)
                Text("Символы вводятся без пробелов.", color = HintTextColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text("Сохранить программу")
                }
                OutlinedButton(onClick = onClearProgram, modifier = Modifier.weight(1f)) {
                    Text("Очистить программу")
                }
            }
        }
    }
}

@Composable
private fun CommandRowLine(
    row: ProgramCommandRowUiState,
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Удалить",
                    tint = ActionIconColor
                )
            }
            IconButton(onClick = onMoveUp) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Вверх",
                    tint = ActionIconColor
                )
            }
            IconButton(onClick = onMoveDown) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Вниз",
                    tint = ActionIconColor
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
        HorizontalDivider(color = LineColor, thickness = 1.dp)
    }
}

@Composable
private fun InlineTextField(
    value: String,
    placeholder: String = "",
    onValueChange: (String) -> Unit,
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
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
                    color = HintTextColor.copy(alpha = 0.55f),
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
                    color = HintTextColor.copy(alpha = 0.55f),
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
    onFocused: () -> Unit = {}
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(70.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocused() },
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
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
        Text("Состояние:", color = HintTextColor)
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
private fun StatusBlock(
    statusMessage: String?,
    errorMessage: String?
) {
    if (statusMessage.isNullOrBlank() && errorMessage.isNullOrBlank()) {
        return
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftCardColor)
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
            containerColor = CommandTeal,
            contentColor = Color.White,
            disabledContainerColor = DisabledGray,
            disabledContentColor = Color(0xFF9A9A9A)
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
private fun TapeArrowButton(
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
