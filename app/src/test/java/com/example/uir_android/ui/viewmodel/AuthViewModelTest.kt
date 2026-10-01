package com.example.uir_android.ui.viewmodel

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AccountSession
import com.example.uir_android.domain.model.LocalStorageState
import com.example.uir_android.domain.model.LocalStorageStatus
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.domain.repository.TestDraftRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rapid login taps send one request`() = runTest(dispatcher) {
        val authRepository = mock(AuthRepository::class.java)
        val draftRepository = mock(TestDraftRepository::class.java)
        `when`(authRepository.observeSession()).thenReturn(flowOf(AccountSession()))
        `when`(authRepository.observeStorageState()).thenReturn(flowOf(LocalStorageState()))
        `when`(authRepository.observeCaptchaTicket()).thenReturn(flowOf(""))
        `when`(authRepository.login("student@test.ru", "password")).thenReturn(
            AppResult.Success(Unit)
        )
        val viewModel = AuthViewModel(authRepository, draftRepository)

        viewModel.login("student@test.ru", "password")
        viewModel.login("student@test.ru", "password")

        assertTrue(viewModel.state.value.isSubmitting)
        advanceUntilIdle()
        verify(authRepository, times(1)).login("student@test.ru", "password")
    }

    @Test
    fun `storage recovery warning does not clear authenticated session`() = runTest(dispatcher) {
        val authRepository = mock(AuthRepository::class.java)
        val draftRepository = mock(TestDraftRepository::class.java)
        `when`(authRepository.observeSession()).thenReturn(
            flowOf(
                AccountSession(
                    email = "student@test.ru",
                    hasAccount = true,
                    isLoggedIn = true
                )
            )
        )
        `when`(authRepository.observeCaptchaTicket()).thenReturn(flowOf(""))
        `when`(authRepository.observeStorageState()).thenReturn(
            flowOf(
                LocalStorageState(
                    status = LocalStorageStatus.RECOVERING,
                    message = "Повторяем чтение"
                )
            )
        )

        val viewModel = AuthViewModel(authRepository, draftRepository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isLoggedIn)
        assertEquals(LocalStorageStatus.RECOVERING, viewModel.state.value.storageStatus)
        assertEquals("Повторяем чтение", viewModel.state.value.storageMessage)
    }
}
