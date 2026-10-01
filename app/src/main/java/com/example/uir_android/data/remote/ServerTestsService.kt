package com.example.uir_android.data.remote

import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.data.local.AccountScopedCache
import com.example.uir_android.data.local.normalizeAccountOwnerKey
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.core.network.ServerBaseUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class ServerTest(
    val id: Int = 0,
    val name: String = "",
    val course: String = "",
    val type: String = "",
    val timeMinutes: Int = 0,
    val totalPoints: Double = 0.0,
    val maxPoints: Double = 0.0,
    val questionsCount: Int = 0,
    val attempts: Int = 0,
    val available: Boolean = false,
    val isAdaptive: Boolean = false,
    val isTuringControl: Boolean = false,
    val hasCurrentRun: Boolean = false,
    val currentResultId: Int? = null,
    val latestResult: ServerTestResult? = null,
    val startPath: String = "",
    val startUrl: String = ""
)

@Serializable
data class ServerTestQuestionContentItem(
    val index: Int = 0,
    val type: String = "",
    val text: String = "",
    val imageUrl: String = ""
)

@Serializable
data class ServerTestQuestion(
    val id: Int = 0,
    val count: Int = 0,
    val typeCode: Int = 0,
    val typeName: String = "",
    val text: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val textParts: List<String> = emptyList(),
    val variants: List<String> = emptyList(),
    val variantGroups: List<List<String>> = emptyList(),
    val supported: Boolean = false,
    val contentItems: List<ServerTestQuestionContentItem> = emptyList()
)

@Serializable
data class ServerTestDetail(
    val id: Int = 0,
    val name: String = "",
    val type: String = "",
    val timeMinutes: Int = 0,
    val totalPoints: Double = 0.0,
    val runId: Int = 0,
    val endsAt: String = "",
    val remainingSeconds: Long? = null,
    val questions: List<ServerTestQuestion> = emptyList()
)

@Serializable
data class ServerTestAnswer(
    val questionId: Int,
    val values: List<String>
)

@Serializable
data class ServerTestResult(
    val runId: Int = 0,
    val score: Double = 0.0,
    val total: Double = 0.0,
    val markRu: String = "",
    val markEu: String = "",
    val finishedAt: String = "",
    val details: List<ServerTestQuestionResult> = emptyList()
)

@Serializable
data class ServerTestQuestionResult(
    val questionId: Int,
    val score: Double = 0.0,
    val points: Double = 0.0,
    val rightPercent: Int = 0,
    val answers: List<String> = emptyList(),
    val correctAnswers: List<String> = emptyList()
)

data class ServerCompletedTestReview(
    val test: ServerTestDetail,
    val result: ServerTestResult
)

@Singleton
class ServerTestsService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsStore: AppSettingsStore,
    private val errorMapper: HttpErrorMapper,
    private val dispatchers: AppDispatchers,
    @ServerBaseUrl private val serverBaseUrl: HttpUrl,
    @ApplicationScope applicationScope: CoroutineScope
) {
    private val testSummaries = AccountScopedCache<Int, ServerTest>()

    init {
        applicationScope.launch {
            settingsStore.authSessionFlow.collect { session ->
                testSummaries.selectOwner(
                    session.email.takeIf { session.isLoggedIn }.orEmpty()
                )
            }
        }
    }

    fun cachedTest(ownerKey: String, testId: Int): ServerTest? =
        testSummaries.get(ownerKey, testId)

    fun cachedTests(ownerKey: String): List<ServerTest> = testSummaries.values(ownerKey)

    suspend fun loadTests(): AppResult<List<ServerTest>> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        if (session.cookieHeader.isBlank()) {
            return@withContext errorMapper.missingSession()
        }
        val ownerKey = normalizeAccountOwnerKey(session.email)
        testSummaries.selectOwner(ownerKey)

        try {
            httpClient.newCall(
                Request.Builder()
                    .url(buildUrl("api", "mobile", "tests"))
                    .get()
                    .header("Accept", "application/json")
                    .withCookieHeader(cookieHeader = session.cookieHeader)
                    .build()
            ).awaitResponse().use { response ->
                val body = response.body?.string().orEmpty()
                errorMapper.accessError(
                    response.code,
                    response.request.url.encodedPath,
                    body
                )?.let { return@withContext it }
                if (!response.isSuccessful) {
                    return@withContext errorMapper.http(
                        response.code,
                        serverErrorMessage(body) ?: "Ошибка загрузки тестов: ${response.code}"
                    )
                }
                val envelope = when (val decoded = errorMapper.decodePayload(
                    body = body,
                    statusCode = response.code,
                    context = "JSON списка тестов"
                ) { testsJson.decodeFromString<MobileTestsEnvelope>(body) }) {
                    is AppResult.Error -> return@withContext decoded
                    is AppResult.Success -> decoded.data
                }
                if (!envelope.success) {
                    return@withContext errorMapper.api(
                        envelope.message ?: "Не удалось загрузить тесты"
                    )
                }
                testSummaries.replaceIfCurrent(
                    ownerKey,
                    envelope.tests.associateBy(ServerTest::id)
                )
                AppResult.Success(envelope.tests)
            }
        } catch (error: IOException) {
            errorMapper.network(error)
        }
    }

    suspend fun loadTest(testId: Int): AppResult<ServerTestDetail> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        if (session.cookieHeader.isBlank()) {
            return@withContext errorMapper.missingSession()
        }

        try {
            httpClient.newCall(
                Request.Builder()
                    .url(buildUrl("api", "mobile", "tests", testId.toString()))
                    .get()
                    .header("Accept", "application/json")
                    .withCookieHeader(cookieHeader = session.cookieHeader)
                    .build()
            ).awaitResponse().use { response ->
                val body = response.body?.string().orEmpty()
                errorMapper.accessError(
                    response.code,
                    response.request.url.encodedPath,
                    body
                )?.let { return@withContext it }
                if (!response.isSuccessful) {
                    return@withContext errorMapper.http(
                        response.code,
                        serverErrorMessage(body) ?: "Ошибка загрузки теста: ${response.code}"
                    )
                }
                val envelope = when (val decoded = errorMapper.decodePayload(
                    body = body,
                    statusCode = response.code,
                    context = "JSON теста"
                ) { testsJson.decodeFromString<MobileTestDetailEnvelope>(body) }) {
                    is AppResult.Error -> return@withContext decoded
                    is AppResult.Success -> decoded.data
                }
                val test = envelope.test
                if (!envelope.success || test == null) {
                    return@withContext errorMapper.api(
                        envelope.message ?: "Не удалось загрузить тест"
                    )
                }
                AppResult.Success(test)
            }
        } catch (error: IOException) {
            errorMapper.network(error)
        }
    }

    suspend fun submitTest(
        testId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<ServerTestAnswer>
    ): AppResult<ServerTestResult> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        if (session.cookieHeader.isBlank()) {
            return@withContext errorMapper.missingSession()
        }

        val body = testsJson.encodeToString(
            MobileSubmitRequest(
                runId = runId,
                timedOut = timedOut,
                answers = answers
            )
        )
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        try {
            httpClient.newCall(
                Request.Builder()
                    .url(buildUrl("api", "mobile", "tests", testId.toString(), "submit"))
                    .post(body)
                    .header("Accept", "application/json")
                    .withCookieHeader(cookieHeader = session.cookieHeader)
                    .build()
            ).awaitResponse().use { response ->
                val responseBody = response.body?.string().orEmpty()
                errorMapper.accessError(
                    response.code,
                    response.request.url.encodedPath,
                    responseBody
                )?.let { return@withContext it }
                if (!response.isSuccessful) {
                    return@withContext errorMapper.http(
                        response.code,
                        serverErrorMessage(responseBody) ?: "Ошибка отправки теста: ${response.code}"
                    )
                }
                val envelope = when (val decoded = errorMapper.decodePayload(
                    body = responseBody,
                    statusCode = response.code,
                    context = "JSON отправки теста",
                    ambiguousMutation = true
                ) { testsJson.decodeFromString<MobileSubmitEnvelope>(responseBody) }) {
                    is AppResult.Error -> return@withContext decoded
                    is AppResult.Success -> decoded.data
                }
                val result = envelope.result
                if (!envelope.success || result == null) {
                    return@withContext errorMapper.api(
                        envelope.message ?: "Не удалось отправить тест"
                    )
                }
                AppResult.Success(result, envelope.message.orEmpty())
            }
        } catch (error: IOException) {
            errorMapper.network(error)
        }
    }

    suspend fun loadTestResult(
        testId: Int,
        runId: Int? = null
    ): AppResult<ServerCompletedTestReview> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        if (session.cookieHeader.isBlank()) {
            return@withContext errorMapper.missingSession()
        }

        try {
            val resultUrl = buildUrl("api", "mobile", "tests", testId.toString(), "result")
                .newBuilder()
                .apply {
                    runId?.takeIf { it > 0 }?.let { addQueryParameter("runId", it.toString()) }
                }
                .build()
            httpClient.newCall(
                Request.Builder()
                    .url(resultUrl)
                    .get()
                    .header("Accept", "application/json")
                    .withCookieHeader(cookieHeader = session.cookieHeader)
                    .build()
            ).awaitResponse().use { response ->
                val responseBody = response.body?.string().orEmpty()
                errorMapper.accessError(
                    response.code,
                    response.request.url.encodedPath,
                    responseBody
                )?.let { return@withContext it }
                if (!response.isSuccessful) {
                    return@withContext errorMapper.http(
                        response.code,
                        serverErrorMessage(responseBody)
                            ?: "Ошибка загрузки результата теста: ${response.code}"
                    )
                }
                val envelope = when (val decoded = errorMapper.decodePayload(
                    body = responseBody,
                    statusCode = response.code,
                    context = "JSON результата теста"
                ) { testsJson.decodeFromString<MobileResultEnvelope>(responseBody) }) {
                    is AppResult.Error -> return@withContext decoded
                    is AppResult.Success -> decoded.data
                }
                val test = envelope.test
                val result = envelope.result
                if (!envelope.success || test == null || result == null) {
                    return@withContext errorMapper.api(
                        envelope.message ?: "Не удалось загрузить результат теста"
                    )
                }
                AppResult.Success(ServerCompletedTestReview(test, result))
            }
        } catch (error: IOException) {
            errorMapper.network(error)
        }
    }

    private fun buildUrl(vararg segments: String): HttpUrl {
        return serverBaseUrl.newBuilder().apply {
            segments.forEach(::addPathSegment)
        }.build()
    }
}

@Serializable
private data class MobileTestsEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val tests: List<ServerTest> = emptyList()
)

@Serializable
private data class MobileTestDetailEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val test: ServerTestDetail? = null
)

@Serializable
private data class MobileSubmitRequest(
    val runId: Int,
    val timedOut: Boolean,
    val answers: List<ServerTestAnswer>
)

@Serializable
private data class MobileSubmitEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val result: ServerTestResult? = null
)

@Serializable
private data class MobileResultEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val test: ServerTestDetail? = null,
    val result: ServerTestResult? = null
)

private val testsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun serverErrorMessage(body: String): String? = runCatching {
    testsJson.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
}.getOrNull()

private fun Request.Builder.withCookieHeader(cookieHeader: String): Request.Builder {
    if (cookieHeader.isNotBlank()) {
        header("Cookie", cookieHeader)
    }
    return this
}
