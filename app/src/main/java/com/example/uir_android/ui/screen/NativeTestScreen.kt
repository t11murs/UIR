package com.example.uir_android.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionResult
import com.example.uir_android.core.util.openExternalUri
import com.example.uir_android.ui.component.LinkifiedText
import com.example.uir_android.ui.viewmodel.TestDetailViewModel
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.event.handle
import com.example.uir_android.ui.state.TestSessionStatus
import com.example.uir_android.domain.test.isAnswered
import com.example.uir_android.domain.test.orderedContentRows
import com.example.uir_android.domain.test.TestQuestionContentRow
import com.example.uir_android.ui.util.formatReviewAnswers
import com.example.uir_android.ui.util.formatRemainingDuration
import com.example.uir_android.ui.util.isQuestionHeadingText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeTestScreen(
    onBack: () -> Unit,
    lockNavigationWhileLoading: Boolean = true,
    onStarted: (Int, Int) -> Unit = { _, _ -> },
    onFinished: (Int) -> Unit = {},
    viewModel: TestDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val navigationLocked = when (state.status) {
        TestSessionStatus.LOADING -> lockNavigationWhileLoading
        TestSessionStatus.ACTIVE,
        TestSessionStatus.SUBMITTING,
        TestSessionStatus.EXPIRED -> true
        TestSessionStatus.FINISHED,
        TestSessionStatus.ERROR -> false
    }
    val requestBack: () -> Unit = {
        if (navigationLocked) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Сначала завершите текущий тест")
            }
        } else {
            onBack()
        }
        Unit
    }

    BackHandler(enabled = navigationLocked) {
        requestBack()
    }

    LaunchedEffect(state.result?.runId, state.test?.id) {
        if (state.result != null) state.test?.id?.let(onFinished)
    }

    LaunchedEffect(state.test?.id, state.test?.runId, state.status, state.result) {
        val test = state.test ?: return@LaunchedEffect
        if (
            lockNavigationWhileLoading &&
            state.result == null &&
            test.runId > 0 &&
            state.status in setOf(
                TestSessionStatus.ACTIVE,
                TestSessionStatus.SUBMITTING,
                TestSessionStatus.EXPIRED
            )
        ) {
            onStarted(test.id, test.runId)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { it.handle(snackbarHostState) }
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.saveDraft()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (state.showIncompleteConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::dismissIncompleteSubmit,
            title = { Text("Не все вопросы завершены") },
            text = {
                Text("Без ответа: ${state.unansweredCount}. Завершить тест всё равно?")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmIncompleteSubmit) {
                    Text("Завершить")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::goToFirstUnanswered) {
                    Text("Перейти к вопросу")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            val timerTitle = when {
                state.status == TestSessionStatus.EXPIRED -> "Время закончилось"
                state.status == TestSessionStatus.ACTIVE ||
                    state.status == TestSessionStatus.SUBMITTING -> state.remainingSeconds?.let {
                        "Осталось: ${formatRemainingDuration(it)}"
                    }.orEmpty()
                else -> ""
            }
            AppTopBar(
                title = timerTitle,
                onBack = if (navigationLocked) null else requestBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.status == TestSessionStatus.ERROR -> ErrorBlock(
                    message = state.errorMessage.orEmpty(),
                    onRetry = viewModel::load,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    val test = state.test ?: return@Box
                    val questions = test.questions
                    val currentIndex = state.currentQuestionIndex.coerceInQuestionRange(questions.size)
                    val currentQuestion = questions.getOrNull(currentIndex)
                    val remainingSeconds = state.remainingSeconds
                    val isTimeExpired = state.status == TestSessionStatus.EXPIRED
                    val doneQuestionIds = remember(questions, state.answers) {
                        questions.asSequence()
                            .filter { question ->
                                question.isAnswered(state.answers[question.id].orEmpty())
                            }
                            .map(TestQuestion::id)
                            .toSet()
                    }
                    val doneCount = doneQuestionIds.size
                    val resultDetails = state.result?.details.orEmpty()
                    val detailsByQuestion = remember(resultDetails) {
                        resultDetails.associateBy(TestQuestionResult::questionId)
                    }
                    val correctIds = remember(detailsByQuestion) {
                        detailsByQuestion.values.asSequence()
                            .filter { it.rightPercent >= 100 }
                            .map(TestQuestionResult::questionId)
                            .toSet()
                    }
                    val incorrectIds = remember(detailsByQuestion, correctIds) {
                        detailsByQuestion.keys - correctIds
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.status == TestSessionStatus.FINISHED && !state.isReviewingResult) {
                            state.result?.let { result ->
                                item {
                                    ResultCard(
                                        score = result.score,
                                        total = result.total,
                                        markRu = result.markRu,
                                        markEu = result.markEu,
                                        finishedAt = result.finishedAt,
                                        hasQuestionDetails = questions.isNotEmpty() && result.details.isNotEmpty(),
                                        onReview = viewModel::openResultReview
                                    )
                                }
                            }
                        } else if (state.status == TestSessionStatus.FINISHED) {
                            item {
                                ResultReviewHeader(
                                    currentIndex = currentIndex,
                                    totalQuestions = questions.size,
                                    onClose = viewModel::closeResultReview
                                )
                            }
                            item {
                                QuestionNavigator(
                                    questions = questions,
                                    currentIndex = currentIndex,
                                    doneQuestionIds = emptySet(),
                                    markedQuestionIds = emptySet(),
                                    correctQuestionIds = correctIds,
                                    incorrectQuestionIds = incorrectIds,
                                    onSelectQuestion = viewModel::selectQuestion
                                )
                            }
                            currentQuestion?.let { question ->
                                item {
                                    QuestionCard(
                                        question = question,
                                        answers = state.answers[question.id].orEmpty(),
                                        isMarked = false,
                                        enabled = false,
                                        result = detailsByQuestion[question.id],
                                        onToggleMark = {},
                                        onSingle = {},
                                        onText = {},
                                        onIndexed = { _, _ -> },
                                        onMulti = {},
                                        onTableCell = {}
                                    )
                                }
                            }
                        } else {
                            item {
                                TestHeader(
                                    currentIndex = currentIndex,
                                    totalQuestions = questions.size,
                                    doneCount = doneCount
                                )
                            }

                            if (isTimeExpired) {
                                item {
                                    TimeExpiredCard(
                                        errorMessage = state.errorMessage,
                                        onRetry = viewModel::retryExpiredSubmission
                                    )
                                }
                            } else {
                                item {
                                    QuestionNavigator(
                                        questions = questions,
                                        currentIndex = currentIndex,
                                        doneQuestionIds = doneQuestionIds,
                                        markedQuestionIds = state.markedQuestionIds,
                                        onSelectQuestion = viewModel::selectQuestion
                                    )
                                }

                                currentQuestion?.let { question ->
                                    item {
                                        QuestionCard(
                                            question = question,
                                            answers = state.answers[question.id].orEmpty(),
                                            isMarked = question.id in state.markedQuestionIds,
                                            enabled = state.canEdit,
                                            onToggleMark = { viewModel.toggleQuestionMark(question.id) },
                                            onSingle = { viewModel.setSingleAnswer(question.id, it) },
                                            onText = { viewModel.setTextAnswer(question.id, it) },
                                            onIndexed = { index, value -> viewModel.setIndexedAnswer(question.id, index, value) },
                                            onMulti = { viewModel.toggleMultiAnswer(question.id, it) },
                                            onTableCell = { viewModel.toggleTableCell(question.id, it) }
                                        )
                                    }
                                }

                                item {
                                    QuestionActions(
                                        currentIndex = currentIndex,
                                        totalQuestions = questions.size,
                                        isSubmitting = state.isSubmitting,
                                        canSubmit = state.canEdit,
                                        onPrevious = viewModel::previousQuestion,
                                        onNext = viewModel::nextQuestion,
                                        onSubmit = viewModel::submit
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

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onRetry,
            modifier = Modifier.widthIn(min = 160.dp)
        ) {
            Text("\u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c")
        }
    }
}

@Composable
private fun TestHeader(
    currentIndex: Int,
    totalQuestions: Int,
    doneCount: Int
) {
    val progress = if (totalQuestions == 0) 0f else doneCount.toFloat() / totalQuestions

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u0412\u043e\u043f\u0440\u043e\u0441 ${currentIndex + 1} \u0438\u0437 $totalQuestions",
                fontWeight = FontWeight.Bold
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(text = "\u0421\u0434\u0435\u043b\u0430\u043d\u043e: $doneCount \u0438\u0437 $totalQuestions")
        }
    }
}

@Composable
private fun TimeExpiredCard(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "\u0412\u0440\u0435\u043c\u044f \u0437\u0430\u043a\u043e\u043d\u0447\u0438\u043b\u043e\u0441\u044c",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ответы больше нельзя изменять. Сохранённый снимок можно отправить повторно.",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            if (!errorMessage.isNullOrBlank()) {
                Text(text = errorMessage, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text("Повторить отправку")
            }
        }
    }
}

@Composable
private fun QuestionNavigator(
    questions: List<TestQuestion>,
    currentIndex: Int,
    doneQuestionIds: Set<Int>,
    markedQuestionIds: Set<Int>,
    correctQuestionIds: Set<Int> = emptySet(),
    incorrectQuestionIds: Set<Int> = emptySet(),
    onSelectQuestion: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (questions.isNotEmpty()) {
            listState.animateScrollToItem(currentIndex.coerceInQuestionRange(questions.size))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "\u0421\u043f\u0438\u0441\u043e\u043a \u0432\u043e\u043f\u0440\u043e\u0441\u043e\u0432", style = MaterialTheme.typography.titleMedium)
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            itemsIndexed(
                questions,
                key = { index, question -> "$index-${question.id}" }
            ) { index, question ->
                QuestionNavItem(
                    number = index + 1,
                    isCurrent = index == currentIndex,
                    isDone = question.id in doneQuestionIds,
                    isMarked = question.id in markedQuestionIds,
                    isCorrect = question.id in correctQuestionIds,
                    isIncorrect = question.id in incorrectQuestionIds,
                    onClick = { onSelectQuestion(index) }
                )
            }
        }
    }
}

@Composable
private fun QuestionNavItem(
    number: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    isMarked: Boolean,
    isCorrect: Boolean,
    isIncorrect: Boolean,
    onClick: () -> Unit
) {
    val containerColor = when {
        isIncorrect -> MaterialTheme.colorScheme.errorContainer
        isCorrect -> MaterialTheme.colorScheme.tertiaryContainer
        isMarked -> MaterialTheme.colorScheme.secondaryContainer
        isDone -> MaterialTheme.colorScheme.tertiaryContainer
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isIncorrect -> MaterialTheme.colorScheme.onErrorContainer
        isCorrect || isDone -> MaterialTheme.colorScheme.onTertiaryContainer
        isMarked -> MaterialTheme.colorScheme.onSecondaryContainer
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        isMarked -> MaterialTheme.colorScheme.secondary
        isCorrect || isDone -> MaterialTheme.colorScheme.tertiary
        isIncorrect -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = Modifier
            .widthIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(if (isCurrent || isMarked) 2.dp else 1.dp, borderColor),
        tonalElevation = if (isCurrent) 5.dp else 1.dp
    ) {
        Text(
            text = number.toString(),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun QuestionCard(
    question: TestQuestion,
    answers: List<String>,
    isMarked: Boolean,
    enabled: Boolean,
    result: TestQuestionResult? = null,
    onToggleMark: () -> Unit,
    onSingle: (String) -> Unit,
    onText: (String) -> Unit,
    onIndexed: (Int, String) -> Unit,
    onMulti: (String) -> Unit,
    onTableCell: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                result == null -> MaterialTheme.colorScheme.surface
                result.rightPercent >= 100 -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column {
                    Text(
                        text = "\u0412\u043e\u043f\u0440\u043e\u0441 ${question.count}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(text = question.typeName, style = MaterialTheme.typography.bodyMedium)
                }
                if (result == null) {
                    OutlinedButton(
                        onClick = onToggleMark,
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (isMarked) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        border = BorderStroke(
                            width = if (isMarked) 2.dp else 1.dp,
                            color = if (isMarked) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(if (isMarked) "\u0421\u043d\u044f\u0442\u044c \u043e\u0442\u043c\u0435\u0442\u043a\u0443" else "\u041e\u0442\u043c\u0435\u0442\u0438\u0442\u044c")
                    }
                }
            }

            result?.let { questionResult ->
                val isCorrect = questionResult.rightPercent >= 100
                Text(
                    text = if (isCorrect) "Ответ верный" else "В ответе есть ошибка",
                    color = if (isCorrect) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Баллы: ${questionResult.score.formatPoints()} из ${questionResult.points.formatPoints()} " +
                        "(${questionResult.rightPercent}%)"
                )
                if (!isCorrect) {
                    CorrectAnswerCard(
                        answer = remember(question, questionResult.correctAnswers) {
                            question.formatReviewAnswers(questionResult.correctAnswers)
                        }
                    )
                }
            }

            if (question.typeCode != 4 && question.typeCode != 5) {
                QuestionContent(question)
            }
            HorizontalDivider()

            when (question.typeCode) {
                1 -> SingleChoiceOptions(question.variants, answers.firstOrNull(), enabled, onSingle)
                2 -> MultiChoiceOptions(question.variants, answers, enabled, onMulti)
                3 -> GapChoiceOptions(question.textParts, question.variantGroups, answers, enabled, onIndexed)
                4 -> AccordanceTableOptions(
                    question.orderedContentRows(),
                    question.variants,
                    answers,
                    enabled,
                    onTableCell
                )
                5 -> YesNoOptions(question.orderedContentRows(), answers, enabled, onIndexed)
                8 -> OutlinedTextField(
                    value = answers.firstOrNull().orEmpty(),
                    onValueChange = onText,
                    label = { Text("\u041e\u0442\u0432\u0435\u0442") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled
                )
                9 -> ThreeTextAnswers(answers, enabled, onIndexed)
                else -> Unit
            }
        }
    }
}

@Composable
private fun CorrectAnswerCard(answer: String) {
    if (answer.isBlank()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Правильный ответ",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = answer,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun QuestionContent(question: TestQuestion) {
    val rows = remember(question) { question.orderedContentRows() }
    var hasVisibleContent = false
    rows.forEach { row ->
        val visibleText = row.text.takeUnless(::isQuestionHeadingText).orEmpty()
        if (visibleText.isNotBlank() || row.imageUrls.isNotEmpty()) {
            hasVisibleContent = true
            QuestionContentRow(row.copy(text = visibleText))
        }
    }
    if (!hasVisibleContent) {
        Text(
            text = "\u0422\u0435\u043a\u0441\u0442 \u0432\u043e\u043f\u0440\u043e\u0441\u0430 \u043d\u0435 \u0443\u043a\u0430\u0437\u0430\u043d",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun QuestionContentRow(
    row: TestQuestionContentRow,
    showEmptyLabel: Boolean = false
) {
    val context = LocalContext.current
    if (row.text.isNotBlank()) {
        LinkifiedText(
            text = row.text,
            onOpenLink = { openExternalUri(context, it) },
            style = MaterialTheme.typography.bodyLarge
        )
    }
    row.imageUrls.forEach { imageUrl -> QuestionImage(imageUrl) }
    if (showEmptyLabel && row.text.isBlank() && row.imageUrls.isEmpty()) {
        Text("Строка ${row.index + 1}")
    }
}

@Composable
internal fun QuestionImage(source: String) {
    SubcomposeAsyncImage(
        model = source,
        contentDescription = "Изображение к вопросу",
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 480.dp)
            .clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Fit,
        loading = {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        },
        error = {
            Text(
                text = "Не удалось загрузить изображение",
                color = MaterialTheme.colorScheme.error
            )
        },
        success = { SubcomposeAsyncImageContent() }
    )
}

@Composable
private fun SingleChoiceOptions(
    variants: List<String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    ChoiceDropdown(
        label = "\u0412\u044b\u0431\u0435\u0440\u0438\u0442\u0435 \u043e\u0442\u0432\u0435\u0442",
        selected = selected.orEmpty(),
        options = variants,
        enabled = enabled,
        onSelect = onSelect
    )
}

@Composable
private fun MultiChoiceOptions(
    variants: List<String>,
    selected: List<String>,
    enabled: Boolean,
    onToggle: (String) -> Unit
) {
    val selectedValues = remember(selected) { selected.toHashSet() }
    variants.forEach { variant ->
        SelectableRow(onClick = { onToggle(variant) }, enabled = enabled) {
            Checkbox(
                checked = variant in selectedValues,
                onCheckedChange = if (enabled) { { onToggle(variant) } } else null
            )
            Text(text = variant, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun GapChoiceOptions(
    textParts: List<String>,
    groups: List<List<String>>,
    answers: List<String>,
    enabled: Boolean,
    onIndexed: (Int, String) -> Unit
) {
    if (textParts.isNotEmpty()) {
        Text(text = textParts.joinToString(" ___ "))
    }
    groups.forEachIndexed { index, variants ->
        ChoiceDropdown(
            label = "\u041f\u0440\u043e\u043f\u0443\u0441\u043a ${index + 1}",
            selected = answers.getOrNull(index).orEmpty(),
            options = variants,
            enabled = enabled,
            onSelect = { onIndexed(index, it) }
        )
    }
}

@Composable
private fun AccordanceTableOptions(
    rows: List<TestQuestionContentRow>,
    columns: List<String>,
    selected: List<String>,
    enabled: Boolean,
    onSelectCell: (String) -> Unit
) {
    if (rows.isEmpty() || columns.isEmpty()) {
        Text("Таблица вопроса не заполнена")
        return
    }

    val selectedCells = remember(selected) { selected.toHashSet() }
    rows.forEach { row ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuestionContentRow(row, showEmptyLabel = true)
                columns.forEachIndexed { columnIndex, column ->
                    val cell = (row.index * columns.size + columnIndex + 1).toString()
                    SelectableRow(enabled = enabled, onClick = { onSelectCell(cell) }) {
                        Checkbox(
                            checked = cell in selectedCells,
                            onCheckedChange = if (enabled) { { onSelectCell(cell) } } else null
                        )
                        Text(text = column, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun YesNoOptions(
    statements: List<TestQuestionContentRow>,
    answers: List<String>,
    enabled: Boolean,
    onIndexed: (Int, String) -> Unit
) {
    statements.forEach { statement ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QuestionContentRow(statement, showEmptyLabel = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SelectableRow(
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = { onIndexed(statement.index, "true") }
                ) {
                    RadioButton(
                        selected = answers.getOrNull(statement.index) == "true",
                        onClick = if (enabled) { { onIndexed(statement.index, "true") } } else null
                    )
                    Text("\u0414\u0430")
                }
                SelectableRow(
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onClick = { onIndexed(statement.index, "false") }
                ) {
                    RadioButton(
                        selected = answers.getOrNull(statement.index) == "false",
                        onClick = if (enabled) { { onIndexed(statement.index, "false") } } else null
                    )
                    Text("\u041d\u0435\u0442")
                }
            }
        }
    }
}

@Composable
private fun ThreeTextAnswers(
    answers: List<String>,
    enabled: Boolean,
    onIndexed: (Int, String) -> Unit
) {
    repeat(3) { index ->
        OutlinedTextField(
            value = answers.getOrNull(index).orEmpty(),
            onValueChange = { onIndexed(index, it) },
            label = { Text("\u041e\u0442\u0432\u0435\u0442 ${index + 1}") },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
    }
}

@Composable
private fun ChoiceDropdown(
    label: String,
    selected: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selected.ifBlank { label },
                modifier = Modifier.fillMaxWidth()
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
private fun QuestionActions(
    currentIndex: Int,
    totalQuestions: Int,
    isSubmitting: Boolean,
    canSubmit: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onPrevious,
                enabled = currentIndex > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("\u041d\u0430\u0437\u0430\u0434")
            }
            OutlinedButton(
                onClick = onNext,
                enabled = currentIndex < totalQuestions - 1,
                modifier = Modifier.weight(1f)
            ) {
                Text("\u0414\u0430\u043b\u0435\u0435")
            }
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit && !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSubmitting) "\u041e\u0442\u043f\u0440\u0430\u0432\u043a\u0430..." else "\u0417\u0430\u0432\u0435\u0440\u0448\u0438\u0442\u044c \u0442\u0435\u0441\u0442")
        }
    }
}

@Composable
private fun SelectableRow(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
private fun ResultCard(
    score: Double,
    total: Double,
    markRu: String,
    markEu: String,
    finishedAt: String,
    hasQuestionDetails: Boolean,
    onReview: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "\u0420\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442", style = MaterialTheme.typography.titleLarge)
            Text(text = "\u0411\u0430\u043b\u043b\u044b: ${score.formatPoints()} \u0438\u0437 ${total.formatPoints()}")
            val mark = listOf(markRu, markEu)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString(" / ")
            Text(text = "\u041e\u0446\u0435\u043d\u043a\u0430: ${mark.ifBlank { "\u043d\u0435 \u0443\u043a\u0430\u0437\u0430\u043d\u0430" }}")
            if (finishedAt.isNotBlank()) {
                Text(text = "\u0417\u0430\u0432\u0435\u0440\u0448\u0451\u043d: ${finishedAt.formatServerDateTime()}")
            }
            if (hasQuestionDetails) {
                Button(onClick = onReview, modifier = Modifier.fillMaxWidth()) {
                    Text("Просмотреть ответы")
                }
            }
        }
    }
}

@Composable
private fun ResultReviewHeader(
    currentIndex: Int,
    totalQuestions: Int,
    onClose: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Просмотр результата", style = MaterialTheme.typography.titleLarge)
            Text("Вопрос ${currentIndex + 1} из $totalQuestions")
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Text("К результату")
            }
        }
    }
}

private fun String.formatServerDateTime(): String {
    return try {
        val parsed = LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        parsed.format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm"))
    } catch (_: DateTimeParseException) {
        this
    } catch (_: IllegalArgumentException) {
        this
    }
}

private fun Int.coerceInQuestionRange(size: Int): Int {
    return if (size <= 0) 0 else coerceIn(0, size - 1)
}

private fun Double.formatPoints(): String {
    val longValue = toLong()
    return if (this == longValue.toDouble()) longValue.toString() else String.format("%.1f", this)
}
