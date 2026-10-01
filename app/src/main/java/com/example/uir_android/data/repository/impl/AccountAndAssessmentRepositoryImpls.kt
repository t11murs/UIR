package com.example.uir_android.data.repository.impl

import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.data.local.AccountScopedCache
import com.example.uir_android.data.local.CaptchaSessionStore
import com.example.uir_android.data.local.CompletedTestReviewStore
import com.example.uir_android.data.local.normalizeAccountOwnerKey
import com.example.uir_android.data.remote.ServerAuthService
import com.example.uir_android.data.remote.ServerCompletedTestReview
import com.example.uir_android.data.remote.ServerEmulatorActionResult
import com.example.uir_android.data.remote.ServerEmulatorAnswer
import com.example.uir_android.data.remote.ServerEmulatorControl
import com.example.uir_android.data.remote.ServerEmulatorControlsService
import com.example.uir_android.data.remote.ServerEmulatorQuestion
import com.example.uir_android.data.remote.ServerEmulatorResultDetail
import com.example.uir_android.data.remote.ServerEmulatorResultLookup
import com.example.uir_android.data.remote.ServerEmulatorSubmitResult
import com.example.uir_android.data.remote.ServerGroup
import com.example.uir_android.data.remote.ServerTest
import com.example.uir_android.data.remote.ServerTestAnswer
import com.example.uir_android.data.remote.ServerTestDetail
import com.example.uir_android.data.remote.ServerTestQuestion
import com.example.uir_android.data.remote.ServerTestQuestionResult
import com.example.uir_android.data.remote.ServerTestResult
import com.example.uir_android.data.remote.ServerTestsService
import com.example.uir_android.data.remote.ServerTuringFees
import com.example.uir_android.data.remote.ServerTuringState
import com.example.uir_android.data.remote.ServerTuringTask
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.domain.model.AccountSession
import com.example.uir_android.domain.model.CompletedTestReview
import com.example.uir_android.domain.model.EmulatorActionResult
import com.example.uir_android.domain.model.EmulatorAnswer
import com.example.uir_android.domain.model.EmulatorControl
import com.example.uir_android.domain.model.EmulatorQuestion
import com.example.uir_android.domain.model.EmulatorResultDetail
import com.example.uir_android.domain.model.EmulatorResultLookup
import com.example.uir_android.domain.model.EmulatorRunStatus
import com.example.uir_android.domain.model.EmulatorSubmitResult
import com.example.uir_android.domain.model.RegistrationGroup
import com.example.uir_android.domain.model.TestAnswer
import com.example.uir_android.domain.model.TestDetail
import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionResult
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.model.TuringFees
import com.example.uir_android.domain.model.TuringStateData
import com.example.uir_android.domain.model.TuringTaskData
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.EmulatorControlRepository
import com.example.uir_android.domain.repository.TestRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val service: ServerAuthService,
    private val settingsStore: AppSettingsStore,
    private val captchaSessionStore: CaptchaSessionStore
) : AuthRepository {
    override fun observeSession() = settingsStore.authSessionFlow.map { session ->
        AccountSession(session.email, session.hasAccount, session.isLoggedIn)
    }

    override fun observeStorageState() = settingsStore.storageState

    override suspend fun currentSession(): AccountSession = settingsStore.currentAuthSession().let {
        AccountSession(it.email, it.hasAccount, it.isLoggedIn)
    }

    override fun observeCaptchaTicket() = captchaSessionStore.ticket
    override fun acceptCaptcha(ticket: String) = captchaSessionStore.accept(ticket)
    override fun clearCaptcha() = captchaSessionStore.clear()
    override fun currentCaptchaTicket() = captchaSessionStore.currentTicket()

    override suspend fun login(email: String, password: String) = service.login(email, password)

    override suspend fun loadRegistrationGroups() = service.loadGroups().mapData { groups ->
        groups.map(ServerGroup::toDomain)
    }

    override suspend fun register(
        firstName: String,
        lastName: String,
        groupId: Int?,
        email: String,
        password: String,
        passwordConfirmation: String,
        captchaTicket: String
    ) = service.register(
        firstName,
        lastName,
        groupId,
        email,
        password,
        passwordConfirmation,
        captchaTicket
    )

    override suspend fun logout() = service.logout()
}

@Singleton
class TestRepositoryImpl @Inject constructor(
    private val service: ServerTestsService,
    private val completedReviewStore: CompletedTestReviewStore,
    private val settingsStore: AppSettingsStore,
    @ApplicationScope applicationScope: CoroutineScope
) : TestRepository {
    private val loadedTests = AccountScopedCache<Int, TestDetail>()

    init {
        applicationScope.launch {
            settingsStore.authSessionFlow.collect { session ->
                loadedTests.selectOwner(
                    session.email.takeIf { session.isLoggedIn }.orEmpty()
                )
            }
        }
    }

    override suspend fun cachedTest(testId: Int): TestSummary? {
        val ownerKey = selectCurrentOwner()
        return service.cachedTest(ownerKey, testId)?.toDomain()
    }

    override suspend fun cachedTests(): List<TestSummary> {
        val ownerKey = selectCurrentOwner()
        return service.cachedTests(ownerKey).map(ServerTest::toDomain)
    }

    override suspend fun loadTests() = service.loadTests().mapData { it.map(ServerTest::toDomain) }

    override suspend fun loadTest(testId: Int): AppResult<TestDetail> {
        val ownerKey = selectCurrentOwner()
        val result = service.loadTest(testId).mapData(ServerTestDetail::toDomain)
        if (result is AppResult.Success) {
            loadedTests.putIfCurrent(ownerKey, testId, result.data)
            clearCachedReviewSafely(ownerKey, testId)
        }
        return result
    }

    override suspend fun submitTest(
        testId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<TestAnswer>
    ): AppResult<TestResult> {
        val ownerKey = selectCurrentOwner()
        val result = service.submitTest(
            testId,
            runId,
            timedOut,
            answers.map { ServerTestAnswer(it.questionId, it.values) }
        ).mapData(ServerTestResult::toDomain)
        if (result is AppResult.Success) {
            loadedTests.get(ownerKey, testId)?.let { test ->
                saveReviewSafely(ownerKey, CompletedTestReview(test, result.data))
            }
            return result
        }
        if (result !is AppResult.Error || !result.canReconcileSubmission()) return result

        // The server or proxy may fail after the transaction has already been committed.
        // Reconcile only the exact run so an older completed attempt cannot be accepted.
        return when (val remoteReview = service.loadTestResult(testId, runId)) {
            is AppResult.Success -> {
                val review = remoteReview.data.toDomain()
                if (review.result.runId != runId || review.test.runId != runId) {
                    result
                } else {
                    loadedTests.putIfCurrent(ownerKey, testId, review.test)
                    saveReviewSafely(ownerKey, review)
                    AppResult.Success(
                        data = review.result,
                        message = "Тест сохранён на сервере"
                    )
                }
            }
            is AppResult.Error -> when (remoteReview.type) {
                AppErrorType.AUTHENTICATION, AppErrorType.AUTHORIZATION -> remoteReview
                else -> result
            }
        }
    }

    override suspend fun loadTestResult(testId: Int): AppResult<CompletedTestReview> {
        val ownerKey = selectCurrentOwner()
        val remote = service.loadTestResult(testId).mapData(ServerCompletedTestReview::toDomain)
        if (remote is AppResult.Success) {
            loadedTests.putIfCurrent(ownerKey, testId, remote.data.test)
            saveReviewSafely(ownerKey, remote.data)
            return remote
        }
        if (remote !is AppResult.Error || !remote.canUseCompletedReviewCache()) return remote
        return loadCachedReviewSafely(ownerKey, testId)?.let { cached ->
            loadedTests.putIfCurrent(ownerKey, testId, cached.test)
            AppResult.Success(cached, "Показан сохранённый результат")
        } ?: remote
    }

    private suspend fun saveReviewSafely(ownerKey: String, review: CompletedTestReview) {
        try {
            completedReviewStore.save(ownerKey, review)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A cache failure must not turn an already submitted test into an error.
        }
    }

    private suspend fun loadCachedReviewSafely(
        ownerKey: String,
        testId: Int
    ): CompletedTestReview? = try {
        completedReviewStore.getLatest(ownerKey, testId)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun clearCachedReviewSafely(ownerKey: String, testId: Int) {
        try {
            completedReviewStore.clearTest(ownerKey, testId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Cache invalidation must not prevent opening a server-backed attempt.
        }
    }

    private suspend fun selectCurrentOwner(): String {
        val session = settingsStore.currentAuthSession()
        val ownerKey = normalizeAccountOwnerKey(
            session.email.takeIf { session.isLoggedIn }.orEmpty()
        )
        loadedTests.selectOwner(ownerKey)
        return ownerKey
    }
}

internal fun AppResult.Error.canUseCompletedReviewCache(): Boolean =
    type == AppErrorType.NETWORK

internal fun AppResult.Error.canReconcileSubmission(): Boolean =
    type == AppErrorType.NETWORK || type == AppErrorType.SERVER

@Singleton
class EmulatorControlRepositoryImpl @Inject constructor(
    private val service: ServerEmulatorControlsService
) : EmulatorControlRepository {
    override suspend fun loadControls() = service.loadControls().mapData { it.map(ServerTest::toDomain) }
    override suspend fun loadControl(controlId: Int, runId: Int?) = service.loadControl(controlId, runId)
        .mapData(ServerEmulatorControl::toDomain)
    override suspend fun loadResult(controlId: Int, runId: Int) =
        service.loadResult(controlId, runId).mapData(ServerEmulatorResultLookup::toDomain)
    override suspend fun saveDraft(controlId: Int, questionId: Int, task: TuringTaskData) =
        service.saveDraft(controlId, questionId, task.toRemote())
    override suspend fun performAction(
        controlId: Int,
        questionId: Int,
        action: String,
        operationId: String,
        task: TuringTaskData
    ) = service.performAction(controlId, questionId, action, operationId, task.toRemote())
        .mapData(ServerEmulatorActionResult::toDomain)
    override suspend fun loadActionReceipt(
        controlId: Int,
        runId: Int,
        operationId: String
    ) = service.loadActionReceipt(controlId, runId, operationId)
        .mapData { it?.toDomain() }
    override suspend fun submit(
        controlId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<EmulatorAnswer>,
        invalidQuestionIds: List<Int>
    ): AppResult<EmulatorSubmitResult> {
        val result = service.submit(
            controlId,
            runId,
            timedOut,
            answers.map { ServerEmulatorAnswer(it.questionId, it.task.toRemote()) },
            invalidQuestionIds
        ).mapData(ServerEmulatorSubmitResult::toDomain)
        if (result !is AppResult.Error || !result.canReconcileSubmission()) return result

        return when (val lookup = loadResult(controlId, runId)) {
            is AppResult.Success -> lookup.data
                .takeIf { it.completed }
                ?.result
                ?.takeIf { it.runId == runId }
                ?.let { completed ->
                AppResult.Success(completed, "Контрольная сохранена на сервере")
            } ?: result
            is AppResult.Error -> when (lookup.type) {
                AppErrorType.AUTHENTICATION, AppErrorType.AUTHORIZATION -> lookup
                else -> result
            }
        }
    }
}

private inline fun <T, R> AppResult<T>.mapData(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Error -> this
    is AppResult.Success -> AppResult.Success(transform(data), message)
}

private fun ServerGroup.toDomain() = RegistrationGroup(id, name)
private fun ServerTest.toDomain() = TestSummary(
    id = id,
    name = name,
    course = course,
    type = type,
    timeMinutes = timeMinutes,
    totalPoints = totalPoints,
    maxPoints = maxPoints,
    questionsCount = questionsCount,
    attempts = attempts,
    available = available,
    isAdaptive = isAdaptive,
    hasCurrentRun = hasCurrentRun,
    currentResultId = currentResultId,
    startPath = startPath,
    startUrl = startUrl,
    isTuringControl = isTuringControl,
    latestResult = latestResult?.toDomain()
)
private fun ServerTestQuestion.toDomain() = TestQuestion(
    id = id,
    count = count,
    typeCode = typeCode,
    typeName = typeName,
    text = text,
    imageUrls = imageUrls,
    textParts = textParts,
    variants = variants,
    variantGroups = variantGroups,
    supported = supported,
    contentItems = contentItems.map { item ->
        com.example.uir_android.domain.model.TestQuestionContentItem(
            index = item.index,
            type = item.type,
            text = item.text,
            imageUrl = item.imageUrl
        )
    }
)
private fun ServerTestDetail.toDomain() = TestDetail(
    id = id,
    name = name,
    type = type,
    timeMinutes = timeMinutes,
    totalPoints = totalPoints,
    runId = runId,
    endsAt = endsAt,
    remainingSeconds = remainingSeconds,
    questions = questions.map(ServerTestQuestion::toDomain)
)
private fun ServerTestQuestionResult.toDomain() = TestQuestionResult(
    questionId, score, points, rightPercent, answers, correctAnswers
)
private fun ServerTestResult.toDomain() = TestResult(
    runId, score, total, markRu, markEu, finishedAt, details.map(ServerTestQuestionResult::toDomain)
)
private fun ServerCompletedTestReview.toDomain() = CompletedTestReview(test.toDomain(), result.toDomain())
private fun ServerTuringState.toDomain() = TuringStateData(state, expressions)
private fun ServerTuringTask.toDomain() = TuringTaskData(alphabet, automaton.map(ServerTuringState::toDomain))
private fun TuringTaskData.toRemote() = ServerTuringTask(
    alphabet,
    automaton.map { ServerTuringState(it.state, it.expressions) }
)
private fun ServerTuringFees.toDomain() = TuringFees(debugPercent, syntaxPercent, runPercent, maxPercent)
private fun ServerEmulatorQuestion.toDomain() = EmulatorQuestion(
    id, count, text, imageUrls, points, debugCounter, syntaxCounter, runCounter, feePercent,
    task.toDomain()
)
private fun ServerEmulatorControl.toDomain() = EmulatorControl(
    id = id,
    name = name,
    type = type,
    timeMinutes = timeMinutes,
    totalPoints = totalPoints,
    maxPoints = maxPoints,
    questionsCount = questionsCount,
    attempts = attempts,
    available = available,
    hasCurrentRun = hasCurrentRun,
    currentResultId = currentResultId,
    runId = runId,
    endsAt = endsAt,
    remainingSeconds = remainingSeconds,
    fees = fees.toDomain(),
    questions = questions.map(ServerEmulatorQuestion::toDomain)
)
private fun ServerEmulatorActionResult.toDomain() = EmulatorActionResult(
    questionId, action, syntaxValid, syntaxErrors, debugCounter, syntaxCounter, runCounter,
    feePercent, passed, total, rawRightPercent, score
)
private fun ServerEmulatorResultDetail.toDomain() = EmulatorResultDetail(
    questionId, score, points, rightPercent, passed, totalSequences, feePercent
)
private fun ServerEmulatorSubmitResult.toDomain() = EmulatorSubmitResult(
    runId, score, total, markRu, markEu, finishedAt,
    details.map(ServerEmulatorResultDetail::toDomain)
)
private fun ServerEmulatorResultLookup.toDomain() = EmulatorResultLookup(
    name = name,
    completed = completed,
    result = result?.toDomain(),
    status = when (status.uppercase()) {
        "COMPLETED" -> EmulatorRunStatus.COMPLETED
        "NOT_FOUND" -> EmulatorRunStatus.NOT_FOUND
        else -> EmulatorRunStatus.ACTIVE
    },
    activeRunId = activeRunId
)
