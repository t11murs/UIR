package com.example.uir_android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.domain.model.EmulatorQuestion
import com.example.uir_android.domain.model.EmulatorResultDetail
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.turing.BLANK_INPUT_TOKEN
import com.example.uir_android.domain.turing.OMEGA_INPUT_TOKEN
import com.example.uir_android.domain.turing.PARTIAL_INPUT_TOKEN
import com.example.uir_android.core.util.openExternalUri
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.component.LinkifiedText
import com.example.uir_android.ui.component.TapeWindow
import com.example.uir_android.ui.state.CommandSymbolFieldTarget
import com.example.uir_android.ui.state.DraftSyncStatus
import com.example.uir_android.ui.viewmodel.EmulatorControlViewModel
import com.example.uir_android.ui.event.handle
import com.example.uir_android.ui.viewmodel.EmulatorControlsViewModel
import com.example.uir_android.ui.viewmodel.isCompleted
import com.example.uir_android.ui.util.formatRemainingDuration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun EmulatorControlsScreen(
    onOpenControl: (Int) -> Unit,
    onOpenResult: (Int, Int) -> Unit,
    viewModel: EmulatorControlsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.refresh()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Контрольные с эмулятором",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall
            )
            OutlinedButton(
                onClick = viewModel::refresh,
                enabled = !state.isLoading,
                modifier = Modifier.widthIn(min = 124.dp)
            ) {
                Text(
                    text = if (state.isLoading) "Загрузка..." else "Обновить",
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.controls.isEmpty() -> EmptyControls(
                    message = state.errorMessage ?: "Доступных контрольных с эмулятором нет",
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.controls, key = TestSummary::id) { control ->
                        EmulatorControlCard(control, onOpenControl, onOpenResult)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyControls(
    message: String,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(message)
        Button(onClick = onRefresh, modifier = Modifier.widthIn(min = 150.dp)) {
            Text("Обновить")
        }
    }
}

@Composable
internal fun EmulatorControlCard(
    control: TestSummary,
    onOpenControl: (Int) -> Unit,
    onOpenResult: (Int, Int) -> Unit
) {
    val completed = control.isCompleted()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                completed -> MaterialTheme.colorScheme.tertiaryContainer
                control.hasCurrentRun -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(control.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Контрольная с эмулятором")
            Text("Заданий: ${control.questionsCount}")
            Text("Время: ${control.timeMinutes} мин. Максимум: ${formatPoints(control.maxPoints)}")
            if (control.hasCurrentRun) {
                Text("Попытка уже начата, время продолжает идти.")
            }
            if (completed) {
                Text("Контрольная пройдена")
                control.latestResult?.let { result ->
                    Text("Баллы: ${formatPoints(result.score)} из ${formatPoints(result.total)}")
                    Text("Оценка: ${result.markRu.ifBlank { result.markEu }}")
                }
                val runId = control.latestResult?.runId ?: control.currentResultId
                Button(
                    onClick = { runId?.let { onOpenResult(control.id, it) } },
                    enabled = runId != null && runId > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Посмотреть результат и ошибки")
                }
                if (runId == null || runId <= 0) {
                    Text(
                        "Сервер не передал номер завершённой попытки",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Button(
                    onClick = { onOpenControl(control.id) },
                    enabled = control.available || control.hasCurrentRun,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (control.hasCurrentRun) "Вернуться к контрольной" else "Начать")
                }
            }
        }
    }
}

@Composable
fun EmulatorControlScreen(
    onBack: () -> Unit,
    reviewMode: Boolean = false,
    onStarted: (Int, Int) -> Unit = { _, _ -> },
    onFinished: (Int) -> Unit = {},
    viewModel: EmulatorControlViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showHelp by remember { mutableStateOf(false) }
    var showSubmitConfirmation by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val navigationLocked = !reviewMode &&
        (state.isLoading || (state.control != null && state.result == null))
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        viewModel.clearSymbolFieldSelection()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { it.handle(snackbar) }
    }
    LaunchedEffect(state.remainingSeconds) {
        if (state.control != null && state.remainingSeconds <= 0L) {
            showSubmitConfirmation = false
            dismissKeyboard()
        }
    }
    LaunchedEffect(state.result?.runId, state.control?.id) {
        if (state.result != null) state.control?.id?.let(onFinished)
    }
    LaunchedEffect(state.control?.id, state.control?.runId, state.result) {
        val control = state.control ?: return@LaunchedEffect
        if (!reviewMode && state.result == null && control.runId > 0) {
            onStarted(control.id, control.runId)
        }
    }

    if (showHelp) {
        EmulatorControlHelpDialog(onDismiss = { showHelp = false })
    }
    if (showSubmitConfirmation) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmation = false },
            title = { Text("Сдать контрольную?") },
            text = {
                Text("После отправки изменить алгоритмы и ответы будет нельзя.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubmitConfirmation = false
                        dismissKeyboard()
                        viewModel.submit()
                    }
                ) {
                    Text("Сдать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmation = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    BackHandler(enabled = navigationLocked) {
        coroutineScope.launch {
            snackbar.showSnackbar("Сначала завершите текущую контрольную")
        }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.saveLocalDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                AppTopBar(
                    title = state.control?.name ?: "Контрольная с эмулятором",
                    onBack = if (navigationLocked) null else onBack,
                    actions = {
                        if (!reviewMode && state.control != null && state.result == null) {
                            IconButton(onClick = { showHelp = true }) {
                                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Помощь")
                            }
                        }
                    }
                )
                if (state.control != null && state.result == null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = if (state.remainingSeconds > 0) {
                                "Осталось: ${formatRemainingDuration(state.remainingSeconds)}"
                            } else {
                                "Время закончилось"
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = if (state.remainingSeconds > 0) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                        )
                    }
                    PersistentSymbolInputBar(
                        enabled = state.remainingSeconds > 0L &&
                            !state.isSending &&
                            (state.focusedSymbolField != null || state.isInputTapeFocused),
                        addCommandEnabled = state.remainingSeconds > 0L && !state.isSending,
                        onInsertLambda = { viewModel.insertSpecialToken(BLANK_INPUT_TOKEN) },
                        onInsertOmega = { viewModel.insertSpecialToken(OMEGA_INPUT_TOKEN) },
                        onInsertPartial = { viewModel.insertSpecialToken(PARTIAL_INPUT_TOKEN) },
                        onAddCommand = viewModel::addCommandRow,
                        onHideKeyboard = dismissKeyboard
                    )
                }
            }
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            state.control == null -> EmptyControls(
                message = state.errorMessage ?: "Контрольная не загружена",
                onRefresh = viewModel::load,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            state.result != null -> ControlResult(
                name = state.control!!.name,
                score = state.result!!.score,
                total = state.result!!.total,
                mark = state.result!!.markRu.ifBlank { state.result!!.markEu },
                details = state.result!!.details,
                onBack = onBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            else -> {
                val control = state.control!!
                val question = control.questions.getOrNull(state.currentQuestionIndex)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                        .imePadding(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        QuestionSelector(
                            questions = control.questions,
                            currentIndex = state.currentQuestionIndex,
                            onSelect = viewModel::selectQuestion
                        )
                    }
                    if (question != null) {
                        item {
                            QuestionTask(question)
                        }
                        item {
                            PenaltyCard(
                                question = question,
                                debugFee = control.fees.debugPercent,
                                syntaxFee = control.fees.syntaxPercent,
                                runFee = control.fees.runPercent
                            )
                        }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TapeArrowButton("<") { viewModel.shiftTapeWindow(-1) }
                                    TapeWindow(state.tapeWindow, Modifier.weight(1f))
                                    TapeArrowButton(">") { viewModel.shiftTapeWindow(1) }
                                }
                            }
                        }
                        item {
                            TopControlPanel(
                                word = state.inputTape,
                                alphabet = state.alphabetText,
                                stateName = state.currentState,
                                isRunning = state.isRunning || state.isSending || state.remainingSeconds == 0L,
                                enabled = state.remainingSeconds > 0L && !state.isSending,
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
                                onReadFocused = {
                                    viewModel.selectSymbolField(it, CommandSymbolFieldTarget.READ)
                                },
                                onWriteFocused = {
                                    viewModel.selectSymbolField(it, CommandSymbolFieldTarget.WRITE)
                                },
                                onNonSymbolFieldFocused = viewModel::clearSymbolFieldSelection,
                                onSave = { viewModel.saveCurrentDraft() },
                                onClearProgram = viewModel::clearProgram,
                                enabled = state.remainingSeconds > 0L && !state.isSending
                            )
                        }
                        item {
                            DraftSyncIndicator(
                                status = state.draftSyncStatus,
                                error = state.draftSyncError
                            )
                        }
                        item {
                            StatusBlock(state.statusMessage, state.errorMessage)
                        }
                        item {
                            ControlActions(
                                currentIndex = state.currentQuestionIndex,
                                questionsCount = control.questions.size,
                                enabled = !state.isSending && state.remainingSeconds > 0,
                                canRetrySubmission = !state.isSending &&
                                    state.remainingSeconds == 0L && state.errorMessage != null,
                                onPrevious = viewModel::previousQuestion,
                                onNext = viewModel::nextQuestion,
                                onCheckWork = viewModel::checkWork,
                                onSubmit = {
                                    dismissKeyboard()
                                    showSubmitConfirmation = true
                                },
                                onRetrySubmission = viewModel::retryTimedOutSubmission
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftSyncIndicator(
    status: DraftSyncStatus,
    error: String?
) {
    if (status == DraftSyncStatus.IDLE) return
    val text = when (status) {
        DraftSyncStatus.IDLE -> return
        DraftSyncStatus.LOCAL_SAVED -> "Черновик сохранён локально"
        DraftSyncStatus.SYNCING -> "Синхронизация с сервером"
        DraftSyncStatus.SYNCED -> "Черновик синхронизирован"
        DraftSyncStatus.ERROR -> "Ошибка синхронизации: ${error ?: "неизвестная ошибка"}"
    }
    val color = when (status) {
        DraftSyncStatus.ERROR -> MaterialTheme.colorScheme.error
        DraftSyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == DraftSyncStatus.SYNCING) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
        }
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun EmulatorControlHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Помощь по эмулятору") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Каждая команда состоит из двух полей:")
                Text("Слева: <состояние> <читаемый символ>", fontWeight = FontWeight.SemiBold)
                Text("Справа: <записываемый символ> <движение> <следующее состояние>", fontWeight = FontWeight.SemiBold)
                Text("Пример: S0 a  →  b R S1")
                Text("Допустимые движения: L — влево, R — вправо, H или S — без движения.")
                Text("Короткая команда может содержать только движение, например R.")
                Text("λ — пустой символ, Ω — завершение, ∂ — левая граница ленты.")
                Text("Для ввода используйте кнопки λ, Ω и ∂ на закреплённой панели.")
                Text("Серверные проверки и запуски могут уменьшать балл согласно указанным штрафам.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}

@Composable
private fun QuestionSelector(
    questions: List<EmulatorQuestion>,
    currentIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(questions.size) { index ->
            val selected = index == currentIndex
            if (selected) {
                Button(onClick = { onSelect(index) }) {
                    Text("${index + 1}")
                }
            } else {
                OutlinedButton(onClick = { onSelect(index) }) {
                    Text("${index + 1}")
                }
            }
        }
    }
}

@Composable
private fun QuestionTask(question: EmulatorQuestion) {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Задание ${question.count}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text("Максимум: ${formatPoints(question.points)}")
            val text = question.text.filter(String::isNotBlank).joinToString("\n")
            LinkifiedText(
                text = text.ifBlank { "Условие не указано" },
                onOpenLink = { openExternalUri(context, it) }
            )
            question.imageUrls.forEach { QuestionImage(it) }
        }
    }
}

@Composable
private fun PenaltyCard(
    question: EmulatorQuestion,
    debugFee: Int,
    syntaxFee: Int,
    runFee: Int
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Текущий штраф: ${question.feePercent}%", fontWeight = FontWeight.Bold)
            Text("Проверки синтаксиса: ${question.syntaxCounter} × $syntaxFee%")
            Text("Запуски: ${question.runCounter} × $runFee%")
            Text("Неудачные проверки работы: ${question.debugCounter} × $debugFee%")
            Text("Общий штраф ограничен 50%.")
        }
    }
}

@Composable
private fun ControlActions(
    currentIndex: Int,
    questionsCount: Int,
    enabled: Boolean,
    canRetrySubmission: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCheckWork: () -> Unit,
    onSubmit: () -> Unit,
    onRetrySubmission: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCheckWork,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Проверить работу")
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = currentIndex > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Назад")
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = currentIndex < questionsCount - 1,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Далее")
                }
            }
            Button(
                onClick = if (canRetrySubmission) onRetrySubmission else onSubmit,
                enabled = enabled || canRetrySubmission,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (canRetrySubmission) "Повторить отправку" else "Сдать контрольную")
            }
        }
    }
}

@Composable
private fun ControlResult(
    name: String,
    score: Double,
    total: Double,
    mark: String,
    details: List<EmulatorResultDetail>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Контрольная завершена")
                    Text(
                        "${formatPoints(score)} из ${formatPoints(total)}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    if (mark.isNotBlank()) Text("Оценка: $mark")
                }
            }
        }
        if (details.isNotEmpty()) {
            item {
                Text(
                    "Результаты заданий",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            itemsIndexed(
                items = details,
                key = { index, detail -> "${detail.questionId}-$index" }
            ) { index, detail ->
                val correct = detail.rightPercent >= 100
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (correct) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Задание ${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(if (correct) "Выполнено верно" else "Есть ошибки")
                        Text(
                            "Баллы: ${formatPoints(detail.score)} из " +
                                formatPoints(detail.points)
                        )
                        Text("Правильность: ${detail.rightPercent}%")
                        if (detail.totalSequences > 0) {
                            Text(
                                "Пройдено проверок: ${detail.passed} из ${detail.totalSequences}"
                            )
                        }
                        if (detail.feePercent > 0) {
                            Text("Штраф: ${detail.feePercent}%")
                        }
                    }
                }
            }
        } else {
            item { Text("Подробности по заданиям отсутствуют") }
        }
        item {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Вернуться к списку")
            }
        }
    }
}

private fun formatPoints(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)
