package com.example.uir_android.domain.repository

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AccountSession
import com.example.uir_android.domain.model.LocalStorageState
import com.example.uir_android.domain.model.CompletedTestReview
import com.example.uir_android.domain.model.EmulatorActionResult
import com.example.uir_android.domain.model.EmulatorAnswer
import com.example.uir_android.domain.model.EmulatorControl
import com.example.uir_android.domain.model.EmulatorResultLookup
import com.example.uir_android.domain.model.EmulatorSubmitResult
import com.example.uir_android.domain.model.RegistrationGroup
import com.example.uir_android.domain.model.TestAnswer
import com.example.uir_android.domain.model.TestDetail
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.domain.model.TuringTaskData
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeSession(): Flow<AccountSession>
    fun observeStorageState(): Flow<LocalStorageState>
    fun observeCaptchaTicket(): Flow<String>
    suspend fun currentSession(): AccountSession
    fun acceptCaptcha(ticket: String)
    fun clearCaptcha()
    fun currentCaptchaTicket(): String
    suspend fun login(email: String, password: String): AppResult<Unit>
    suspend fun loadRegistrationGroups(): AppResult<List<RegistrationGroup>>
    suspend fun register(
        firstName: String,
        lastName: String,
        groupId: Int?,
        email: String,
        password: String,
        passwordConfirmation: String,
        captchaTicket: String
    ): AppResult<Unit>
    suspend fun logout(): AppResult<Unit>
}

interface TestRepository {
    suspend fun cachedTest(testId: Int): TestSummary?
    suspend fun cachedTests(): List<TestSummary>
    suspend fun loadTests(): AppResult<List<TestSummary>>
    suspend fun loadTest(testId: Int): AppResult<TestDetail>
    suspend fun submitTest(
        testId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<TestAnswer>
    ): AppResult<TestResult>
    suspend fun loadTestResult(testId: Int): AppResult<CompletedTestReview>
}

interface EmulatorControlRepository {
    suspend fun loadControls(): AppResult<List<TestSummary>>
    suspend fun loadControl(controlId: Int, runId: Int? = null): AppResult<EmulatorControl>
    suspend fun loadResult(controlId: Int, runId: Int): AppResult<EmulatorResultLookup>
    suspend fun saveDraft(controlId: Int, questionId: Int, task: TuringTaskData): AppResult<Unit>
    suspend fun performAction(
        controlId: Int,
        questionId: Int,
        action: String,
        operationId: String,
        task: TuringTaskData
    ): AppResult<EmulatorActionResult>
    suspend fun loadActionReceipt(
        controlId: Int,
        runId: Int,
        operationId: String
    ): AppResult<EmulatorActionResult?>
    suspend fun submit(
        controlId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<EmulatorAnswer>,
        invalidQuestionIds: List<Int> = emptyList()
    ): AppResult<EmulatorSubmitResult>
}
