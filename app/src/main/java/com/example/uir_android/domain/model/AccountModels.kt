package com.example.uir_android.domain.model

data class AccountSession(
    val email: String = "",
    val hasAccount: Boolean = false,
    val isLoggedIn: Boolean = false
)

enum class LocalStorageStatus {
    HEALTHY,
    RECOVERING,
    CORRUPTION_RECOVERED,
    FAILED
}

data class LocalStorageState(
    val status: LocalStorageStatus = LocalStorageStatus.HEALTHY,
    val message: String? = null
)

data class RegistrationGroup(
    val id: Int,
    val name: String
)
