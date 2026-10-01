package com.example.uir_android.core.common

sealed interface AppResult<out T> {
    data class Success<T>(
        val data: T,
        val message: String? = null
    ) : AppResult<T>

    data class Error(
        val message: String,
        val cause: Throwable? = null,
        val type: AppErrorType = AppErrorType.UNKNOWN,
        val details: AppErrorDetails? = null
    ) : AppResult<Nothing>
}

interface AppErrorDetails

enum class AppErrorType {
    NETWORK,
    AUTHENTICATION,
    AUTHORIZATION,
    SERVER,
    DATA,
    UNKNOWN
}
