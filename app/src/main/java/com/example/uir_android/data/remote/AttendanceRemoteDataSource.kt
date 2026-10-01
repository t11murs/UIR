package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.domain.model.SeminarPointsLimitError
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AttendanceRemoteDataSource @Inject constructor(
    private val webClient: ServerWebClient
) {
    suspend fun loadLectureLimits(): AppResult<ServerLectureLimitsPageDto> =
        webClient.authenticatedRequest(
            jsonGet("attendance", "lecture-limits")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<LectureLimitsV1Envelope>(body)
            when (val result = academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить лимиты лекций: $code",
                payload = envelope.limits,
                payloadName = "limits",
                validate = ::validateLectureLimits
            )) {
                is AppResult.Error -> result
                is AppResult.Success -> AppResult.Success(ServerLectureLimitsPageDto(limits = result.data))
            }
        }

    suspend fun saveLectureLimits(updates: List<ServerLectureLimitUpdateDto>): AppResult<Unit> {
        if (updates.isEmpty()) return AppResult.Success(Unit)
        return executeVersionedOperation(
            endpoint = listOf("attendance", "lecture-limits"),
            jsonBody = serverJson.encodeToString(
                LectureLimitsUpdateV1Request(
                    operationId = UUID.randomUUID().toString(),
                    updates = updates
                )
            )
        )
    }

    suspend fun loadStewardLectureStatement(): AppResult<ServerLectureStatementDto> =
        loadLectureStatement(groupId = null)

    suspend fun loadManagedLectureStatement(groupId: Int): AppResult<ServerLectureStatementDto> =
        loadLectureStatement(groupId)

    suspend fun loadSeminarStatement(groupId: Int): AppResult<ServerSeminarStatementDto> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(
                    webClient.buildUrl(
                        "api", "mobile", "v1", "attendance", "seminars",
                        query = mapOf("groupId" to groupId.toString())
                    )
                )
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<SeminarStatementV1Envelope>(body)
            academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить ведомость семинаров: $code",
                payload = envelope.statement,
                payloadName = "statement",
                validate = ::validateSeminarStatement
            )
        }

    suspend fun setLectureAttendances(
        updates: List<ServerLectureAttendanceUpdateDto>
    ): AppResult<Unit> {
        if (updates.isEmpty()) return AppResult.Success(Unit)
        val request = LectureAttendanceBatchRequest(
            operationId = UUID.randomUUID().toString(),
            updates = updates
        )
        return executeAttendanceBatch(
            endpoint = "lectures",
            jsonBody = serverJson.encodeToString(request)
        )
    }

    suspend fun saveStewardLectureAttendance(
        lectureId: Int,
        studentIds: List<Int>
    ): AppResult<Unit> = executeVersionedOperation(
        endpoint = listOf("attendance", "lectures", "steward"),
        jsonBody = serverJson.encodeToString(
            StewardLectureAttendanceV1Request(
                operationId = UUID.randomUUID().toString(),
                lectureId = lectureId,
                studentIds = studentIds.distinct()
            )
        )
    )

    suspend fun saveSeminarAttendance(
        updates: List<ServerSeminarAttendanceUpdateDto>
    ): AppResult<Unit> {
        if (updates.isEmpty()) return AppResult.Success(Unit)
        val request = SeminarAttendanceBatchRequest(
            operationId = UUID.randomUUID().toString(),
            updates = updates
        )
        return executeAttendanceBatch(
            endpoint = "seminars",
            jsonBody = serverJson.encodeToString(request)
        )
    }

    private suspend fun loadLectureStatement(groupId: Int?): AppResult<ServerLectureStatementDto> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(
                    webClient.buildUrl(
                        "api", "mobile", "v1", "attendance", "lectures",
                        query = groupId?.let { mapOf("groupId" to it.toString()) }.orEmpty()
                    )
                )
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<LectureStatementV1Envelope>(body)
            academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить ведомость лекций: $code",
                payload = envelope.statement,
                payloadName = "statement",
                validate = ::validateLectureStatement
            )
        }

    private fun jsonGet(vararg endpoint: String): Request.Builder = Request.Builder()
        .url(webClient.buildUrl("api", "mobile", "v1", *endpoint))
        .get()
        .header("Accept", "application/json")

    private suspend fun executeVersionedOperation(
        endpoint: List<String>,
        jsonBody: String
    ): AppResult<Unit> = webClient.authenticatedRequest(
        Request.Builder()
            .url(webClient.buildUrl("api", "mobile", "v1", *endpoint.toTypedArray()))
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
    ) { code, body ->
        val envelope = serverJson.decodeFromString<AcademicBasicV1Envelope>(body)
        when {
            envelope.apiVersion != ACADEMIC_API_VERSION -> unsupportedAcademicApi(envelope.apiVersion)
            envelope.success -> AppResult.Success(Unit, envelope.message)
            else -> AppResult.Error(envelope.message ?: "Сервер отклонил сохранение: $code")
        }
    }

    private suspend fun executeAttendanceBatch(
        endpoint: String,
        jsonBody: String
    ): AppResult<Unit> = webClient.authenticatedRequestWithHttpErrorPayload(
        builder = Request.Builder()
            .url(webClient.buildUrl("api", "mobile", "attendance", endpoint, "batch"))
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json"),
        decodeHttpError = { code, body ->
            if (code != 422) {
                null
            } else {
                serverJson.decodeFromString<AttendanceBatchEnvelope>(body)
                    .toSeminarLimitErrorOrNull()
            }
        }
    ) { _, body ->
        val envelope = serverJson.decodeFromString<AttendanceBatchEnvelope>(body)
        when {
            !envelope.success -> envelope.toSeminarLimitErrorOrNull() ?: AppResult.Error(
                envelope.message ?: "Сервер отклонил сохранение ведомости",
                type = AppErrorType.DATA
            )
            envelope.revision.isNullOrBlank() -> AppResult.Error(
                "Сервер не подтвердил версию сохранённой ведомости"
            )
            else -> AppResult.Success(Unit, envelope.message)
        }
    }
}

internal fun AttendanceBatchEnvelope.toSeminarLimitErrorOrNull(): AppResult.Error? {
    if (errorCode != SEMINAR_POINTS_LIMIT_ERROR_CODE) return null
    val passId = seminarPassId?.takeIf { it > 0 } ?: return null
    val limit = maxWorkPoints?.takeIf(Double::isFinite) ?: return null
    return AppResult.Error(
        message = message ?: "Баллы превышают максимум раздела: $limit",
        type = AppErrorType.DATA,
        details = SeminarPointsLimitError(
            seminarPassId = passId,
            maxWorkPoints = limit,
            actualWorkPoints = actualWorkPoints?.takeIf(Double::isFinite)
        )
    )
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private const val SEMINAR_POINTS_LIMIT_ERROR_CODE = "SEMINAR_POINTS_LIMIT_EXCEEDED"
