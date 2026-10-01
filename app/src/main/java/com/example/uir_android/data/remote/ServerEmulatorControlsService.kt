package com.example.uir_android.data.remote

import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.network.ServerBaseUrl
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class ServerTuringState(
    val state: String,
    val expressions: Map<String, String>
)

@Serializable
data class ServerTuringTask(
    val alphabet: List<String>,
    val automaton: List<ServerTuringState>
)

@Serializable
data class ServerTuringFees(
    val debugPercent: Int = 0,
    val syntaxPercent: Int = 0,
    val runPercent: Int = 0,
    val maxPercent: Int = 50
)

@Serializable
data class ServerEmulatorQuestion(
    val id: Int,
    val count: Int,
    val text: List<String> = emptyList(),
    val imageUrls: List<String> = emptyList(),
    val points: Double = 0.0,
    val debugCounter: Int = 0,
    val syntaxCounter: Int = 0,
    val runCounter: Int = 0,
    val feePercent: Int = 0,
    val task: ServerTuringTask
)

@Serializable
data class ServerEmulatorControl(
    val id: Int,
    val name: String,
    val type: String = "",
    val timeMinutes: Int = 0,
    val totalPoints: Double = 0.0,
    val maxPoints: Double = 0.0,
    val questionsCount: Int = 0,
    val attempts: Int = 0,
    val available: Boolean = false,
    val hasCurrentRun: Boolean = false,
    val currentResultId: Int? = null,
    val runId: Int = 0,
    val endsAt: String = "",
    val remainingSeconds: Long? = null,
    val fees: ServerTuringFees = ServerTuringFees(),
    val questions: List<ServerEmulatorQuestion> = emptyList()
)

@Serializable
data class ServerEmulatorActionResult(
    val questionId: Int,
    val action: String,
    val syntaxValid: Boolean = false,
    val syntaxErrors: List<String> = emptyList(),
    val debugCounter: Int = 0,
    val syntaxCounter: Int = 0,
    val runCounter: Int = 0,
    val feePercent: Int = 0,
    val passed: Int? = null,
    val total: Int? = null,
    val rawRightPercent: Int? = null,
    val score: Double? = null
)

@Serializable
data class ServerEmulatorResultDetail(
    val questionId: Int,
    val score: Double = 0.0,
    val points: Double = 0.0,
    val rightPercent: Int = 0,
    val passed: Int = 0,
    val totalSequences: Int = 0,
    val feePercent: Int = 0
)

@Serializable
data class ServerEmulatorSubmitResult(
    val runId: Int,
    val score: Double = 0.0,
    val total: Double = 0.0,
    val markRu: String = "",
    val markEu: String = "",
    val finishedAt: String = "",
    val details: List<ServerEmulatorResultDetail> = emptyList()
)

@Serializable
data class ServerEmulatorResultLookup(
    val name: String = "",
    val completed: Boolean,
    val result: ServerEmulatorSubmitResult? = null,
    val status: String = if (completed) "COMPLETED" else "ACTIVE",
    val activeRunId: Int? = null
)

@Serializable
data class ServerEmulatorAnswer(
    val questionId: Int,
    val task: ServerTuringTask
)

@Singleton
class ServerEmulatorControlsService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsStore: AppSettingsStore,
    private val errorMapper: HttpErrorMapper,
    private val dispatchers: AppDispatchers,
    @ServerBaseUrl private val serverBaseUrl: HttpUrl
) {
    suspend fun loadControls(): AppResult<List<ServerTest>> = request(
        request = Request.Builder()
            .url(buildUrl("api", "mobile", "emulator-controls"))
            .get(),
        decode = { body ->
            val envelope = controlsJson.decodeFromString<ControlsEnvelope>(body)
            if (envelope.success) AppResult.Success(envelope.controls)
            else AppResult.Error(envelope.message ?: "Не удалось загрузить контрольные")
        }
    )

    suspend fun loadControl(
        controlId: Int,
        runId: Int? = null
    ): AppResult<ServerEmulatorControl> = request(
        request = Request.Builder()
            .url(
                buildUrl("api", "mobile", "emulator-controls", controlId.toString())
                    .newBuilder()
                    .apply {
                        runId?.takeIf { it > 0 }?.let {
                            addQueryParameter("runId", it.toString())
                        }
                    }
                    .build()
            )
            .get(),
        decode = { body ->
            val envelope = controlsJson.decodeFromString<ControlEnvelope>(body)
            val control = envelope.control
            if (envelope.success && control != null) AppResult.Success(control)
            else AppResult.Error(envelope.message ?: "Не удалось открыть контрольную")
        }
    )

    suspend fun loadResult(
        controlId: Int,
        runId: Int
    ): AppResult<ServerEmulatorResultLookup> = request(
        request = Request.Builder()
            .url(
                buildUrl("api", "mobile", "emulator-controls", controlId.toString(), "result")
                    .newBuilder()
                    .addQueryParameter("runId", runId.toString())
                    .build()
            )
            .get(),
        decode = { body ->
            val envelope = controlsJson.decodeFromString<ResultEnvelope>(body)
            if (envelope.success) {
                AppResult.Success(
                    ServerEmulatorResultLookup(
                        name = envelope.name,
                        completed = envelope.completed,
                        result = envelope.result,
                        status = envelope.status.ifBlank {
                            if (envelope.completed) "COMPLETED" else "ACTIVE"
                        },
                        activeRunId = envelope.activeRunId
                    )
                )
            } else {
                AppResult.Error(envelope.message ?: "Не удалось проверить результат контрольной")
            }
        }
    )

    suspend fun saveDraft(
        controlId: Int,
        questionId: Int,
        task: ServerTuringTask
    ): AppResult<Unit> {
        val body = controlsJson.encodeToString(DraftRequest(questionId, task))
        return request(
            request = Request.Builder()
                .url(buildUrl("api", "mobile", "emulator-controls", controlId.toString(), "draft"))
                .post(body.toJsonBody()),
            decode = { responseBody ->
                val envelope = controlsJson.decodeFromString<BasicEnvelope>(responseBody)
                if (envelope.success) AppResult.Success(Unit)
                else AppResult.Error(envelope.message ?: "Не удалось сохранить алгоритм")
            }
        )
    }

    suspend fun performAction(
        controlId: Int,
        questionId: Int,
        action: String,
        operationId: String,
        task: ServerTuringTask
    ): AppResult<ServerEmulatorActionResult> {
        val body = controlsJson.encodeToString(ActionRequest(questionId, action, operationId, task))
        return request(
            request = Request.Builder()
                .url(buildUrl("api", "mobile", "emulator-controls", controlId.toString(), "action"))
                .post(body.toJsonBody()),
            decode = { responseBody ->
                val envelope = controlsJson.decodeFromString<ActionEnvelope>(responseBody)
                val result = envelope.result
                if (envelope.success && result != null) AppResult.Success(result)
                else AppResult.Error(envelope.message ?: "Действие не выполнено")
            }
        )
    }

    suspend fun loadActionReceipt(
        controlId: Int,
        runId: Int,
        operationId: String
    ): AppResult<ServerEmulatorActionResult?> = request(
        request = Request.Builder()
            .url(
                buildUrl(
                    "api",
                    "mobile",
                    "emulator-controls",
                    controlId.toString(),
                    "actions",
                    operationId
                ).newBuilder()
                    .addQueryParameter("runId", runId.toString())
                    .build()
            )
            .get(),
        decode = { responseBody ->
            val envelope = controlsJson.decodeFromString<ActionStatusEnvelope>(responseBody)
            when {
                !envelope.success -> AppResult.Error(
                    envelope.message ?: "Не удалось проверить предыдущее действие"
                )
                envelope.status == "APPLIED" && envelope.result != null ->
                    AppResult.Success(envelope.result)
                envelope.status == "NOT_FOUND" -> AppResult.Success(null)
                else -> AppResult.Error("Сервер вернул некорректный статус действия")
            }
        }
    )

    suspend fun submit(
        controlId: Int,
        runId: Int,
        timedOut: Boolean,
        answers: List<ServerEmulatorAnswer>,
        invalidQuestionIds: List<Int> = emptyList()
    ): AppResult<ServerEmulatorSubmitResult> {
        val body = controlsJson.encodeToString(
            SubmitRequest(
                runId = runId,
                timedOut = timedOut,
                answers = answers,
                invalidQuestionIds = invalidQuestionIds
            )
        )
        return request(
            request = Request.Builder()
                .url(buildUrl("api", "mobile", "emulator-controls", controlId.toString(), "submit"))
                .post(body.toJsonBody()),
            ambiguousSuccessfulResponse = true,
            decode = { responseBody ->
                val envelope = controlsJson.decodeFromString<SubmitEnvelope>(responseBody)
                val result = envelope.result
                if (envelope.success && result != null) {
                    AppResult.Success(result, envelope.message)
                } else {
                    AppResult.Error(envelope.message ?: "Не удалось завершить контрольную")
                }
            }
        )
    }

    private suspend fun <T> request(
        request: Request.Builder,
        ambiguousSuccessfulResponse: Boolean = false,
        decode: (String) -> AppResult<T>
    ): AppResult<T> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        if (session.cookieHeader.isBlank()) {
            return@withContext errorMapper.missingSession()
        }

        try {
            httpClient.newCall(
                request
                    .header("Accept", "application/json")
                    .header("Cookie", session.cookieHeader)
                    .build()
            ).awaitResponse().use { response ->
                val body = response.body?.string().orEmpty()
                errorMapper.accessError(
                    response.code,
                    response.request.url.encodedPath,
                    body
                )?.let { return@withContext it }
                if (!response.isSuccessful) {
                    val message = if (body.isBlank()) {
                        null
                    } else {
                        try {
                            (decode(body) as? AppResult.Error)?.message
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                    return@withContext errorMapper.http(response.code, message)
                }
                if (body.isBlank()) {
                    return@withContext errorMapper.invalidPayload(
                        message = "Пустой ответ сервера: ${response.code}",
                        ambiguousMutation = ambiguousSuccessfulResponse
                    )
                }
                val decoded = try {
                    decode(body)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMapper.invalidPayload(
                        message = "Некорректный JSON от сервера: ${response.code}",
                        cause = error,
                        ambiguousMutation = ambiguousSuccessfulResponse
                    )
                }
                when (decoded) {
                    is AppResult.Error -> if (decoded.type == AppErrorType.UNKNOWN) {
                        errorMapper.api(decoded.message, decoded.cause)
                    } else {
                        decoded
                    }
                    is AppResult.Success -> decoded
                }
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
private data class ControlsEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val controls: List<ServerTest> = emptyList()
)

@Serializable
private data class ControlEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val control: ServerEmulatorControl? = null
)

@Serializable
private data class BasicEnvelope(
    val success: Boolean = false,
    val message: String? = null
)

@Serializable
private data class ActionEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val result: ServerEmulatorActionResult? = null
)

@Serializable
private data class ActionStatusEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val status: String = "",
    val result: ServerEmulatorActionResult? = null
)

@Serializable
private data class SubmitEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val result: ServerEmulatorSubmitResult? = null
)

@Serializable
private data class ResultEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val name: String = "",
    val completed: Boolean = false,
    val result: ServerEmulatorSubmitResult? = null,
    val status: String = "",
    val activeRunId: Int? = null
)

@Serializable
private data class DraftRequest(
    val questionId: Int,
    val task: ServerTuringTask
)

@Serializable
private data class ActionRequest(
    val questionId: Int,
    val action: String,
    val operationId: String,
    val task: ServerTuringTask
)

@Serializable
private data class SubmitRequest(
    val runId: Int,
    val timedOut: Boolean,
    val answers: List<ServerEmulatorAnswer>,
    val invalidQuestionIds: List<Int> = emptyList()
)

private val controlsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private fun String.toJsonBody() =
    toRequestBody("application/json; charset=utf-8".toMediaType())
