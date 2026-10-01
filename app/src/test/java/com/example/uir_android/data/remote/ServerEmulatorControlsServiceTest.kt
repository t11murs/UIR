package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.data.local.LocalAccountSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ServerEmulatorControlsServiceTest {
    @Test
    fun `empty successful submit response is an ambiguous server failure`() = runTest {
        val result = createService("").submit(
            controlId = 5,
            runId = 101,
            timedOut = false,
            answers = emptyList()
        )

        assertTrue(result is AppResult.Error)
        assertEquals(AppErrorType.SERVER, (result as AppResult.Error).type)
    }

    @Test
    fun `malformed successful submit response is an ambiguous server failure`() = runTest {
        val result = createService("{not-json").submit(
            controlId = 5,
            runId = 101,
            timedOut = false,
            answers = emptyList()
        )

        assertTrue(result is AppResult.Error)
        assertEquals(AppErrorType.SERVER, (result as AppResult.Error).type)
    }

    private suspend fun createService(responseBody: String): ServerEmulatorControlsService {
        val settingsStore = mock(AppSettingsStore::class.java)
        `when`(settingsStore.currentAuthSession()).thenReturn(
            LocalAccountSession(
                email = "student@test.ru",
                hasAccount = true,
                isLoggedIn = true,
                cookieHeader = "laravel_session=test"
            )
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseBody.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return ServerEmulatorControlsService(
            httpClient = client,
            settingsStore = settingsStore,
            errorMapper = HttpErrorMapper(ServerSessionGuard(settingsStore)),
            serverBaseUrl = "https://mephi22.ru/".toHttpUrl(),
            dispatchers = AppDispatchers(
                io = Dispatchers.IO,
                default = Dispatchers.Default,
                main = Dispatchers.Unconfined
            )
        )
    }
}
