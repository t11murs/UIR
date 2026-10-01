package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.data.local.AppSettingsStore
import javax.inject.Inject
import javax.inject.Singleton

internal enum class ServerAccessFailure {
    SESSION_EXPIRED,
    ACCESS_DENIED
}

internal fun classifyServerAccessFailure(
    code: Int,
    finalPath: String,
    body: String
): ServerAccessFailure? {
    val normalizedPath = finalPath.trimEnd('/')
    val isLoginPage = normalizedPath == "/auth/login" ||
        body.contains("id=\"login-form\"", ignoreCase = true)
    if (code == 401 || isLoginPage) return ServerAccessFailure.SESSION_EXPIRED
    if (code == 403) return ServerAccessFailure.ACCESS_DENIED

    val isNoAccessPage = normalizedPath == "/no-access" ||
        body.contains("full-no_access", ignoreCase = true)
    if (!isNoAccessPage) return null

    return if (body.contains("Требуется авторизация", ignoreCase = true)) {
        ServerAccessFailure.SESSION_EXPIRED
    } else {
        ServerAccessFailure.ACCESS_DENIED
    }
}

@Singleton
class ServerSessionGuard @Inject constructor(
    private val settingsStore: AppSettingsStore
) {
    suspend fun responseError(code: Int, finalPath: String, body: String): AppResult.Error? =
        when (classifyServerAccessFailure(code, finalPath, body)) {
            ServerAccessFailure.SESSION_EXPIRED -> {
                settingsStore.clearServerSession()
                AppResult.Error(
                    message = "Сессия истекла. Войдите в аккаунт снова",
                    type = AppErrorType.AUTHENTICATION
                )
            }

            ServerAccessFailure.ACCESS_DENIED ->
                AppResult.Error(
                    message = "Недостаточно прав для выполнения операции",
                    type = AppErrorType.AUTHORIZATION
                )

            null -> null
        }
}
