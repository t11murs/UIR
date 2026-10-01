package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import okhttp3.Request

@Singleton
class AcademicRemoteDataSource @Inject constructor(
    private val webClient: ServerWebClient
) {
    suspend fun loadCurrentUser(): AppResult<ServerCurrentUserDto> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(webClient.buildUrl("api", "mobile", "me"))
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<CurrentUserEnvelope>(body)
            val user = envelope.user
            if (envelope.success && user != null) {
                AppResult.Success(user)
            } else {
                AppResult.Error(envelope.message ?: "Не удалось получить пользователя: $code")
            }
        }

    suspend fun loadProfile(): AppResult<ServerProfileDto> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(webClient.buildUrl("api", "mobile", "v1", "profile"))
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<AcademicProfileV1Envelope>(body)
            academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить профиль: $code",
                payload = envelope.profile,
                payloadName = "profile",
                validate = ::validateProfile
            )
        }

    suspend fun loadGroups(): AppResult<List<ServerAcademicGroupDto>> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(webClient.buildUrl("api", "mobile", "v1", "academic-groups"))
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<AcademicGroupsV1Envelope>(body)
            academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить группы: $code",
                payload = envelope.groups,
                payloadName = "groups",
                validate = ::validateAcademicGroups
            )
        }

    suspend fun loadStudents(groupId: Int): AppResult<List<ServerStudentDto>> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(
                    webClient.buildUrl(
                        "api",
                        "mobile",
                        "v1",
                        "academic-groups",
                        groupId.toString(),
                        "students"
                    )
                )
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<AcademicStudentsV1Envelope>(body)
            academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить студентов: $code",
                payload = envelope.students,
                payloadName = "students",
                validate = { validateStudents(it, expectedGroupId = groupId) }
            )
        }
}

internal const val ACADEMIC_API_VERSION = 1

internal fun unsupportedAcademicApi(actualVersion: Int): AppResult.Error = AppResult.Error(
    "Версия учебного API не поддерживается: $actualVersion"
)
