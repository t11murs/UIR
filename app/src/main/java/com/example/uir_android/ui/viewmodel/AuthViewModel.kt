package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.network.ServerAuthService
import com.example.uir_android.core.settings.AppSettingsStore
import com.example.uir_android.core.util.AppResult
import com.example.uir_android.ui.state.AuthUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val settingsStore: AppSettingsStore,
    private val serverAuthService: ServerAuthService
) : EventViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()

    @Volatile
    private var pendingRegistrationEmail: String = ""

    init {
        viewModelScope.launch {
            settingsStore.authSessionFlow.collect { session ->
                _state.update {
                    it.copy(
                        isReady = true,
                        hasAccount = session.hasAccount,
                        isLoggedIn = session.isLoggedIn,
                        email = session.email
                    )
                }
            }
        }
    }

    fun login(email: String, password: String) {
        val safeEmail = email.trim()
        val safePassword = password.trim()
        if (safeEmail.isBlank() || safePassword.isBlank()) {
            viewModelScope.launch { messageChannel.send("Введите email и пароль") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            try {
                when (val result = serverAuthService.login(safeEmail, safePassword)) {
                    AppResult.Loading -> Unit
                    is AppResult.Error -> messageChannel.send(result.message)
                    is AppResult.Success -> messageChannel.send(result.message ?: "Вход выполнен")
                }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }

    fun rememberRegistrationEmail(email: String) {
        pendingRegistrationEmail = email.trim()
    }

    fun completeWebRegistration(cookieHeader: String) {
        viewModelScope.launch {
            when (val result = serverAuthService.acceptWebSession(cookieHeader, pendingRegistrationEmail)) {
                AppResult.Loading -> Unit
                is AppResult.Error -> messageChannel.send(result.message)
                is AppResult.Success -> {
                    pendingRegistrationEmail = ""
                    messageChannel.send(result.message ?: "Регистрация завершена")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            try {
                when (val result = serverAuthService.logout()) {
                    AppResult.Loading -> Unit
                    is AppResult.Error -> messageChannel.send(result.message)
                    is AppResult.Success -> messageChannel.send(result.message ?: "Вы вышли из аккаунта")
                }
            } finally {
                _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}

