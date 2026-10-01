package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.SingleFlightGate
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.TestDraftRepository
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
    private val authRepository: AuthRepository,
    private val testDraftRepository: TestDraftRepository
) : EventViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state = _state.asStateFlow()
    private val authOperationGate = SingleFlightGate()
    private val registrationGroupsGate = SingleFlightGate()

    init {
        viewModelScope.launch {
            authRepository.observeSession().collect { session ->
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
        viewModelScope.launch {
            authRepository.observeCaptchaTicket().collect { ticket ->
                _state.update { it.copy(captchaVerified = ticket.isNotBlank()) }
            }
        }
        viewModelScope.launch {
            authRepository.observeStorageState().collect { storage ->
                _state.update {
                    it.copy(
                        storageStatus = storage.status,
                        storageMessage = storage.message
                    )
                }
            }
        }
    }

    fun login(email: String, password: String) {
        val safeEmail = email.trim()
        if (safeEmail.isBlank() || password.isEmpty()) {
            viewModelScope.launch { emitSnackbar("Введите email и пароль") }
            return
        }
        if (!beginAuthOperation()) return

        viewModelScope.launch {
            try {
                when (val result = authRepository.login(safeEmail, password)) {
                    is AppResult.Error -> emitSnackbar(result.message)
                    is AppResult.Success -> emitSnackbar(result.message ?: "Вход выполнен")
                }
            } finally {
                endAuthOperation()
            }
        }
    }

    fun loadRegistrationGroups() {
        if (_state.value.registrationGroups.isNotEmpty() || _state.value.isGroupsLoading) {
            return
        }
        if (!registrationGroupsGate.tryAcquire()) return
        _state.update { it.copy(isGroupsLoading = true) }

        viewModelScope.launch {
            try {
                when (val result = authRepository.loadRegistrationGroups()) {
                    is AppResult.Error -> emitSnackbar(result.message)
                    is AppResult.Success -> {
                        _state.update { it.copy(registrationGroups = result.data) }
                    }
                }
            } finally {
                registrationGroupsGate.release()
                _state.update { it.copy(isGroupsLoading = false) }
            }
        }
    }

    fun register(
        firstName: String,
        lastName: String,
        groupText: String,
        email: String,
        password: String,
        passwordConfirmation: String
    ) {
        val groupId = groupText.trim().takeIf { it.isNotBlank() }?.toIntOrNull()
        if (groupText.isNotBlank() && groupId == null) {
            viewModelScope.launch { emitSnackbar("ID группы должен быть числом") }
            return
        }
        if (!beginAuthOperation()) return

        viewModelScope.launch {
            try {
                when (
                    val result = authRepository.register(
                        firstName = firstName,
                        lastName = lastName,
                        groupId = groupId,
                        email = email,
                        password = password,
                        passwordConfirmation = passwordConfirmation,
                        captchaTicket = authRepository.currentCaptchaTicket()
                    )
                ) {
                    is AppResult.Error -> emitSnackbar(result.message)
                    is AppResult.Success -> {
                        authRepository.clearCaptcha()
                        emitSnackbar(result.message ?: "Регистрация выполнена")
                    }
                }
            } finally {
                endAuthOperation()
            }
        }
    }

    fun clearCaptcha() {
        authRepository.clearCaptcha()
    }

    fun logout() {
        if (!beginAuthOperation()) return
        val ownerKey = _state.value.email
        viewModelScope.launch {
            try {
                when (val result = authRepository.logout()) {
                    is AppResult.Error -> emitSnackbar(result.message)
                    is AppResult.Success -> emitSnackbar(result.message ?: "Вы вышли из аккаунта")
                }
            } finally {
                try {
                    if (ownerKey.isNotBlank()) {
                        testDraftRepository.deleteAllForOwner(ownerKey)
                    }
                } finally {
                    endAuthOperation()
                }
            }
        }
    }

    private fun beginAuthOperation(): Boolean {
        if (!authOperationGate.tryAcquire()) return false
        _state.update { it.copy(isSubmitting = true) }
        return true
    }

    private fun endAuthOperation() {
        authOperationGate.release()
        _state.update { it.copy(isSubmitting = false) }
    }
}

