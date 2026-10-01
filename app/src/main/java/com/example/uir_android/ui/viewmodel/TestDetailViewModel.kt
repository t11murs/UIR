package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.core.common.MonotonicClock
import com.example.uir_android.domain.model.TestAnswer
import com.example.uir_android.domain.model.TestDetail
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.TestRepository
import com.example.uir_android.domain.model.ActiveTestDraft
import com.example.uir_android.domain.repository.TestDraftRepository
import com.example.uir_android.ui.state.TestDetailUiState
import com.example.uir_android.ui.state.TestSessionStatus
import com.example.uir_android.domain.test.answersForSubmission
import com.example.uir_android.domain.test.isAnswered
import com.example.uir_android.domain.test.unansweredQuestionCount
import com.example.uir_android.domain.test.TestQuestionIdValidation
import com.example.uir_android.domain.test.validateTestQuestionIds
import com.example.uir_android.domain.test.validateTestQuestionContent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltViewModel
class TestDetailViewModel @Inject constructor(
    private val testRepository: TestRepository,
    private val authRepository: AuthRepository,
    private val testDraftRepository: TestDraftRepository,
    private val monotonicClock: MonotonicClock,
    @ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle
) : EventViewModel() {
    private val testId: Int = checkNotNull(savedStateHandle["testId"])
    private val reviewRequested: Boolean = savedStateHandle["review"] ?: false
    private val _state = MutableStateFlow(TestDetailUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    private var timerJob: Job? = null
    private var deadlineElapsedRealtime: Long? = null
    private var metadataDraftSaveJob: Job? = null
    private var exitSaveJob: Job? = null
    private var draftOwnerKey: String = ""
    private val draftErrorReported = AtomicBoolean(false)
    private val draftWriter = TestDraftWriteActor(
        repository = testDraftRepository,
        scope = applicationScope,
        onFailure = ::reportDraftStorageFailure
    )

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        timerJob?.cancel()
        deadlineElapsedRealtime = null
        metadataDraftSaveJob?.cancel()
        loadJob = viewModelScope.launch {
            draftErrorReported.set(false)
            draftOwnerKey = ""
            _state.update {
                it.copy(
                    status = TestSessionStatus.LOADING,
                    errorMessage = null,
                    result = null,
                    remainingSeconds = null,
                    showIncompleteConfirmation = false,
                    isReviewingResult = false
                )
            }
            draftOwnerKey = try {
                authRepository.currentSession().email.trim().lowercase()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val message = "Не удалось прочитать данные сессии. Повторите попытку"
                _state.update {
                    it.copy(status = TestSessionStatus.ERROR, errorMessage = message)
                }
                emitSnackbar(message)
                return@launch
            }
            val testSummary = testRepository.cachedTest(testId) ?: when (
                val testsResult = testRepository.loadTests()
            ) {
                is AppResult.Success -> testsResult.data.firstOrNull { it.id == testId }
                is AppResult.Error -> {
                    showCompletedResultError(testsResult.message)
                    return@launch
                }
            }
            if (testSummary?.isAdaptive == true) {
                showCompletedResultError(
                    "Адаптивные тесты пока недоступны в Android-приложении"
                )
                return@launch
            }
            val shouldLoadCompletedResult = testSummary?.shouldReviewResult(reviewRequested)
                ?: reviewRequested
            if (shouldLoadCompletedResult) {
                when (val review = testRepository.loadTestResult(testId)) {
                    is AppResult.Success -> {
                        val reviewedTest = review.data.test
                        val contractError = reviewedTest.questions.questionContractError()
                        if (contractError != null) {
                            showCompletedResultError(contractError)
                            return@launch
                        }
                        _state.update {
                            it.copy(
                                status = TestSessionStatus.FINISHED,
                                test = reviewedTest,
                                result = review.data.result,
                                answers = review.data.result.details.associate { detail ->
                                    detail.questionId to detail.answers
                                },
                                remainingSeconds = null
                            )
                        }
                        deleteDraft()
                        return@launch
                    }
                    is AppResult.Error -> {
                        showCompletedResultError(review.message)
                        return@launch
                    }
                }
            }
            when (val result = testRepository.loadTest(testId)) {
                is AppResult.Error -> {
                    _state.update {
                        it.copy(status = TestSessionStatus.ERROR, errorMessage = result.message)
                    }
                    emitSnackbar(result.message)
                }
                is AppResult.Success -> {
                    val test = result.data
                    val contractError = test.questions.questionContractError()
                    if (contractError != null) {
                        showCompletedResultError(contractError)
                        return@launch
                    }
                    val remainingSeconds = test.remainingSeconds?.coerceAtLeast(0L)
                        ?: parseTestDeadlineMillis(test.endsAt)?.let {
                            remainingTestSeconds(it, System.currentTimeMillis())
                        }
                    val initialStatus = if (remainingSeconds == 0L) {
                        TestSessionStatus.EXPIRED
                    } else {
                        TestSessionStatus.ACTIVE
                    }
                    val storedDraft = if (draftOwnerKey.isBlank()) {
                        null
                    } else {
                        loadDraftSafely()
                    }
                    val compatibleDraft = storedDraft?.takeIf {
                        it.matches(test.runId, test.endsAt)
                    }
                    if (storedDraft != null && compatibleDraft == null) {
                        deleteDraftAndAwait(draftOwnerKey)
                    }
                    _state.update { current ->
                        val currentTest = current.test
                        val sameInMemoryRun = currentTest != null && currentTest.runId == test.runId &&
                            currentTest.endsAt == test.endsAt
                        current.copy(
                            status = initialStatus,
                            test = test,
                            currentQuestionIndex = (compatibleDraft?.currentQuestionIndex
                                ?: current.currentQuestionIndex)
                                .coerceInQuestionRange(test.questions.size),
                            answers = compatibleDraft?.answers
                                ?: current.answers.takeIf { sameInMemoryRun }
                                ?: emptyMap(),
                            markedQuestionIds = compatibleDraft?.markedQuestionIds
                                ?: current.markedQuestionIds.takeIf { sameInMemoryRun }
                                ?: emptySet(),
                            errorMessage = null,
                            remainingSeconds = remainingSeconds
                        )
                    }
                    if (initialStatus == TestSessionStatus.EXPIRED) {
                        performSubmit(dueToTimeout = true)
                    } else {
                        scheduleMetadataDraftSave()
                        if (remainingSeconds != null) startTimer(remainingSeconds)
                    }
                }
            }
        }
    }

    fun selectQuestion(index: Int) {
        _state.update { current ->
            current.copy(
                currentQuestionIndex = index.coerceInQuestionRange(
                    current.test?.questions.orEmpty().size
                )
            )
        }
        scheduleMetadataDraftSave()
    }

    fun previousQuestion() = selectQuestion(_state.value.currentQuestionIndex - 1)

    fun nextQuestion() = selectQuestion(_state.value.currentQuestionIndex + 1)

    fun openResultReview() {
        val snapshot = _state.value
        if (snapshot.status != TestSessionStatus.FINISHED || snapshot.test?.questions.isNullOrEmpty()) return
        _state.update { it.copy(isReviewingResult = true) }
    }

    fun closeResultReview() {
        _state.update { it.copy(isReviewingResult = false) }
    }

    fun toggleQuestionMark(questionId: Int) {
        if (!_state.value.canEdit) return
        _state.update { current ->
            val marked = current.markedQuestionIds.toMutableSet()
            if (!marked.add(questionId)) marked.remove(questionId)
            current.copy(markedQuestionIds = marked)
        }
        scheduleMetadataDraftSave()
    }

    fun setSingleAnswer(questionId: Int, value: String) = setAnswer(questionId, listOf(value))

    fun setTextAnswer(questionId: Int, value: String) = setAnswer(questionId, listOf(value))

    fun setIndexedAnswer(questionId: Int, index: Int, value: String) {
        if (!_state.value.canEdit) return
        val current = _state.value.answers[questionId].orEmpty().toMutableList()
        while (current.size <= index) current.add("")
        current[index] = value
        setAnswer(questionId, current)
    }

    fun toggleMultiAnswer(questionId: Int, value: String) {
        if (!_state.value.canEdit) return
        val current = _state.value.answers[questionId].orEmpty().toMutableList()
        if (value in current) current.remove(value) else current.add(value)
        setAnswer(questionId, current)
    }

    fun toggleTableCell(questionId: Int, value: String) = toggleMultiAnswer(questionId, value)

    fun submit() {
        val snapshot = _state.value
        if (!snapshot.canEdit) return
        val unanswered = unansweredQuestionCount(
            snapshot.test?.questions.orEmpty(),
            snapshot.answers
        )
        if (unanswered > 0) {
            _state.update {
                it.copy(showIncompleteConfirmation = true, unansweredCount = unanswered)
            }
            return
        }
        performSubmit()
    }

    fun confirmIncompleteSubmit() {
        if (!_state.value.canEdit) return
        _state.update { it.copy(showIncompleteConfirmation = false) }
        performSubmit()
    }

    fun dismissIncompleteSubmit() {
        _state.update { it.copy(showIncompleteConfirmation = false) }
    }

    fun retryExpiredSubmission() {
        if (_state.value.status == TestSessionStatus.EXPIRED) {
            performSubmit(dueToTimeout = true)
        }
    }

    fun goToFirstUnanswered() {
        val snapshot = _state.value
        val index = snapshot.test?.questions.orEmpty().indexOfFirst { question ->
            !question.isAnswered(snapshot.answers[question.id].orEmpty())
        }
        _state.update {
            it.copy(
                showIncompleteConfirmation = false,
                currentQuestionIndex = if (index >= 0) index else it.currentQuestionIndex
            )
        }
        scheduleMetadataDraftSave()
    }

    private fun performSubmit(dueToTimeout: Boolean = false) {
        val snapshot = _state.value
        val test = snapshot.test ?: return
        val canSubmit = snapshot.canEdit ||
            (dueToTimeout && snapshot.status == TestSessionStatus.EXPIRED)
        if (!canSubmit || snapshot.status == TestSessionStatus.SUBMITTING) return
        metadataDraftSaveJob?.cancel()
        _state.update {
            it.copy(
                status = TestSessionStatus.SUBMITTING,
                errorMessage = null,
                showIncompleteConfirmation = false,
                remainingSeconds = if (dueToTimeout) 0L else it.remainingSeconds
            )
        }
        val answers = test.questions.map { question ->
            TestAnswer(
                questionId = question.id,
                values = question.answersForSubmission(snapshot.answers[question.id].orEmpty())
            )
        }

        viewModelScope.launch {
            saveDraftAndAwait(
                snapshot = snapshot,
                errorMessage = "Не удалось сохранить локальный черновик. Отправка продолжается"
            )
            when (val result = testRepository.submitTest(
                testId = testId,
                runId = test.runId,
                timedOut = dueToTimeout,
                answers = answers
            )) {
                is AppResult.Error -> {
                    val deadlineReached = dueToTimeout || hasDeadlineReached()
                    val retryAsTimedOut = _state.value.let { current ->
                        current.status == TestSessionStatus.SUBMITTING &&
                            shouldRetrySubmissionAsTimedOut(
                                dueToTimeout = dueToTimeout,
                                deadlineReached = deadlineReached,
                                errorType = result.type
                            )
                    }
                    _state.update { current ->
                        if (current.status == TestSessionStatus.SUBMITTING) {
                            current.copy(
                                status = if (deadlineReached) {
                                    TestSessionStatus.EXPIRED
                                } else {
                                    TestSessionStatus.ACTIVE
                                },
                                errorMessage = result.message
                            )
                        } else {
                            current
                        }
                    }
                    if (retryAsTimedOut) {
                        emitSnackbar("Время закончилось. Повторная отправка ответов")
                        performSubmit(dueToTimeout = true)
                    } else {
                        emitSnackbar(result.message)
                    }
                }
                is AppResult.Success -> {
                    timerJob?.cancel()
                    deadlineElapsedRealtime = null
                    _state.update {
                        it.copy(
                            status = TestSessionStatus.FINISHED,
                            result = result.data,
                            answers = result.data.details.associate { detail ->
                                detail.questionId to detail.answers
                            }.ifEmpty { it.answers },
                            isReviewingResult = false
                        )
                    }
                    deleteDraft()
                    emitSnackbar(result.message.orEmpty().ifBlank { "Тест завершён" })
                }
            }
        }
    }

    private fun setAnswer(questionId: Int, values: List<String>) {
        if (!_state.value.canEdit) return
        _state.update { current ->
            current.copy(answers = current.answers + (questionId to values))
        }
        enqueueDraftSave(_state.value)
    }

    fun saveDraftAndThen(action: () -> Unit) {
        if (exitSaveJob?.isActive == true) return
        val snapshot = _state.value
        if (!snapshot.canEdit || snapshot.test == null || draftOwnerKey.isBlank()) {
            action()
            return
        }
        metadataDraftSaveJob?.cancel()
        val draft = createDraftSnapshot(snapshot)
        if (draft == null) {
            action()
            return
        }
        val completion = draftWriter.requestSave(
            draft = draft,
            errorMessage = "Не удалось сохранить ответы на устройстве"
        )
        exitSaveJob = viewModelScope.launch {
            completion.await()
            action()
        }
    }

    fun saveDraft() {
        val snapshot = _state.value
        if (!snapshot.canEdit || snapshot.test == null || draftOwnerKey.isBlank()) return
        metadataDraftSaveJob?.cancel()
        enqueueDraftSave(snapshot)
    }

    private fun startTimer(initialRemainingSeconds: Long) {
        timerJob?.cancel()
        deadlineElapsedRealtime = monotonicDeadlineMillis(
            startedAtElapsedRealtime = monotonicClock.elapsedRealtimeMillis(),
            remainingSeconds = initialRemainingSeconds
        )
        timerJob = viewModelScope.launch {
            while (isActive) {
                val deadline = deadlineElapsedRealtime ?: break
                val remaining = remainingTestSeconds(
                    deadline,
                    monotonicClock.elapsedRealtimeMillis()
                )
                _state.update { it.copy(remainingSeconds = remaining) }
                if (shouldAutoSubmitTimedAttempt(remaining)) {
                    emitSnackbar("Время закончилось. Ответы отправляются автоматически")
                    performSubmit(dueToTimeout = true)
                    break
                }
                delay(1_000L)
            }
        }
    }

    private fun hasDeadlineReached(): Boolean {
        if (_state.value.remainingSeconds?.let(::shouldAutoSubmitTimedAttempt) == true) {
            return true
        }
        return deadlineElapsedRealtime?.let { deadline ->
            monotonicClock.elapsedRealtimeMillis() >= deadline
        } == true
    }

    private fun scheduleMetadataDraftSave() {
        val snapshot = _state.value
        snapshot.test ?: return
        if (!snapshot.canEdit || draftOwnerKey.isBlank()) return
        metadataDraftSaveJob?.cancel()
        metadataDraftSaveJob = viewModelScope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MS)
            enqueueDraftSave(_state.value)
        }
    }

    private suspend fun loadDraftSafely(): ActiveTestDraft? = try {
        testDraftRepository.get(draftOwnerKey, testId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        reportDraftStorageFailure("Не удалось загрузить сохранённые ответы")
        null
    }

    private fun enqueueDraftSave(
        snapshot: TestDetailUiState,
        errorMessage: String = "Не удалось сохранить ответы на устройстве"
    ) {
        val draft = createDraftSnapshot(snapshot) ?: return
        if (!draftWriter.enqueueSave(draft, errorMessage)) {
            reportDraftStorageFailure(errorMessage)
        }
    }

    private suspend fun saveDraftAndAwait(
        snapshot: TestDetailUiState,
        errorMessage: String = "Не удалось сохранить ответы на устройстве"
    ): Boolean {
        val draft = createDraftSnapshot(snapshot) ?: return true
        return draftWriter.saveAndAwait(draft, errorMessage)
    }

    private fun createDraftSnapshot(snapshot: TestDetailUiState): ActiveTestDraft? {
        val test = snapshot.test ?: return null
        val ownerKey = draftOwnerKey.takeIf(String::isNotBlank) ?: return null
        return ActiveTestDraft(
            ownerKey = ownerKey,
            testId = test.id,
            runId = test.runId,
            endsAt = test.endsAt,
            currentQuestionIndex = snapshot.currentQuestionIndex,
            answers = snapshot.answers.mapValues { (_, values) -> values.toList() },
            markedQuestionIds = snapshot.markedQuestionIds.toSet(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun deleteDraft() {
        metadataDraftSaveJob?.cancel()
        val ownerKey = draftOwnerKey
        if (ownerKey.isBlank()) return
        if (!draftWriter.enqueueDelete(ownerKey, testId, "Не удалось удалить локальный черновик")) {
            reportDraftStorageFailure("Не удалось удалить локальный черновик")
        }
    }

    private suspend fun deleteDraftAndAwait(ownerKey: String): Boolean {
        return draftWriter.deleteAndAwait(
            ownerKey = ownerKey,
            testId = testId,
            errorMessage = "Не удалось удалить локальный черновик"
        )
    }

    private fun reportDraftStorageFailure(message: String) {
        if (!draftErrorReported.compareAndSet(false, true)) return
        emitSnackbar(message)
    }

    override fun onCleared() {
        metadataDraftSaveJob?.cancel()
        val snapshot = _state.value
        if (snapshot.canEdit) enqueueDraftSave(snapshot)
        draftWriter.close()
        super.onCleared()
    }

    private suspend fun showCompletedResultError(message: String) {
        _state.update {
            it.copy(
                status = TestSessionStatus.ERROR,
                test = null,
                result = null,
                errorMessage = message,
                remainingSeconds = null
            )
        }
        emitSnackbar(message)
    }
}

internal fun TestSummary.shouldReviewResult(reviewRequested: Boolean): Boolean =
    reviewRequested || isCompleted()

internal fun parseTestDeadlineMillis(value: String): Long? = runCatching {
    LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        .atZone(ZoneId.of("Europe/Moscow"))
        .toInstant()
        .toEpochMilli()
}.getOrNull()

internal fun remainingTestSeconds(deadlineMillis: Long, nowMillis: Long): Long {
    val difference = deadlineMillis - nowMillis
    return if (difference <= 0L) 0L else (difference + 999L) / 1_000L
}

internal fun monotonicDeadlineMillis(
    startedAtElapsedRealtime: Long,
    remainingSeconds: Long
): Long {
    val safeSeconds = remainingSeconds.coerceAtLeast(0L).coerceAtMost(
        (Long.MAX_VALUE - startedAtElapsedRealtime) / 1_000L
    )
    return startedAtElapsedRealtime + safeSeconds * 1_000L
}

private fun TestQuestionIdValidation.toUserMessage(): String {
    val details = buildList {
        if (nonPositivePositions.isNotEmpty()) {
            add("нет корректного ID у вопросов: ${nonPositivePositions.joinToString()}")
        }
        if (duplicateIds.isNotEmpty()) {
            add("повторяются ID: ${duplicateIds.joinToString()}")
        }
    }.joinToString("; ")
    return "Сервер вернул некорректные данные теста ($details). " +
        "Попытка заблокирована, чтобы ответы не были потеряны. Обновите экран или обратитесь к преподавателю."
}

private fun List<TestQuestion>.questionContractError(): String? {
    val idValidation = validateTestQuestionIds(this)
    if (!idValidation.isValid) return idValidation.toUserMessage()

    val contentValidation = validateTestQuestionContent(this)
    if (!contentValidation.isValid) {
        return "Сервер нарушил порядок строк в вопросах: " +
            "${contentValidation.invalidQuestionNumbers.joinToString()}. " +
            "Попытка заблокирована, чтобы ответы не сместились. Обновите экран или обратитесь к преподавателю."
    }
    return null
}

internal fun shouldAutoSubmitTimedAttempt(remainingSeconds: Long): Boolean =
    remainingSeconds <= 0L

internal fun shouldRetrySubmissionAsTimedOut(
    dueToTimeout: Boolean,
    deadlineReached: Boolean,
    errorType: AppErrorType
): Boolean = !dueToTimeout &&
    deadlineReached &&
    errorType != AppErrorType.AUTHENTICATION &&
    errorType != AppErrorType.AUTHORIZATION

private const val DRAFT_SAVE_DEBOUNCE_MS = 150L

private fun Int.coerceInQuestionRange(size: Int): Int {
    return if (size <= 0) 0 else coerceIn(0, size - 1)
}
