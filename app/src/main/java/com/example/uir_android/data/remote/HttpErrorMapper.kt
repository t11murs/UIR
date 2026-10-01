package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class HttpErrorMapper @Inject constructor(
    private val sessionGuard: ServerSessionGuard
) {
    fun missingSession(message: String = "Требуется вход в аккаунт") = AppResult.Error(
        message = message,
        type = AppErrorType.AUTHENTICATION
    )

    suspend fun accessError(
        statusCode: Int,
        finalPath: String,
        body: String
    ): AppResult.Error? = sessionGuard.responseError(statusCode, finalPath, body)

    fun http(
        statusCode: Int,
        message: String? = null,
        cause: Throwable? = null
    ) = AppResult.Error(
        message = message ?: "Ошибка сервера: $statusCode",
        cause = cause,
        type = classifyHttpErrorType(statusCode)
    )

    fun network(
        error: IOException,
        message: String = "Не удалось подключиться к серверу"
    ) = AppResult.Error(
        message = message,
        cause = error,
        type = AppErrorType.NETWORK
    )

    fun invalidPayload(
        message: String,
        cause: Throwable? = null,
        ambiguousMutation: Boolean = false
    ) = AppResult.Error(
        message = message,
        cause = cause,
        type = if (ambiguousMutation) AppErrorType.SERVER else AppErrorType.DATA
    )

    fun api(message: String, cause: Throwable? = null) = AppResult.Error(
        message = message,
        cause = cause,
        type = AppErrorType.DATA
    )

    inline fun <T> decodePayload(
        body: String,
        statusCode: Int,
        context: String = "ответ сервера",
        ambiguousMutation: Boolean = false,
        decode: () -> T
    ): AppResult<T> {
        if (body.isBlank()) {
            return invalidPayload(
                message = "Пустой $context: $statusCode",
                ambiguousMutation = ambiguousMutation
            )
        }
        return try {
            AppResult.Success(decode())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            invalidPayload(
                message = "Некорректный $context: $statusCode",
                cause = error,
                ambiguousMutation = ambiguousMutation
            )
        }
    }
}

internal fun classifyHttpErrorType(statusCode: Int): AppErrorType = when (statusCode) {
    401 -> AppErrorType.AUTHENTICATION
    403 -> AppErrorType.AUTHORIZATION
    408, 425, 429 -> AppErrorType.SERVER
    in 400..499 -> AppErrorType.DATA
    else -> AppErrorType.SERVER
}
