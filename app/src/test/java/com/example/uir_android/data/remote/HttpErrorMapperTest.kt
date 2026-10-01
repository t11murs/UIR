package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class HttpErrorMapperTest {
    private val mapper = HttpErrorMapper(mock(ServerSessionGuard::class.java))

    @Test
    fun `http status table is shared by all remote clients`() {
        val expected = mapOf(
            401 to AppErrorType.AUTHENTICATION,
            403 to AppErrorType.AUTHORIZATION,
            409 to AppErrorType.DATA,
            422 to AppErrorType.DATA,
            429 to AppErrorType.SERVER,
            500 to AppErrorType.SERVER,
            502 to AppErrorType.SERVER
        )

        expected.forEach { (status, type) ->
            assertEquals(type, mapper.http(status).type)
        }
    }

    @Test
    fun `network and payload failures have stable types`() {
        assertEquals(AppErrorType.NETWORK, mapper.network(IOException("offline")).type)
        assertEquals(AppErrorType.DATA, mapper.invalidPayload("broken").type)
        assertEquals(
            AppErrorType.SERVER,
            mapper.invalidPayload("ambiguous", ambiguousMutation = true).type
        )
    }

    @Test
    fun `payload decoder preserves cancellation independent data errors`() {
        val empty = mapper.decodePayload(
            body = "",
            statusCode = 200,
            context = "JSON"
        ) { "unused" }
        val malformed = mapper.decodePayload(
            body = "not-json",
            statusCode = 200,
            context = "JSON"
        ) { error("decode failed") }

        assertTrue(empty is AppResult.Error)
        assertEquals(AppErrorType.DATA, (empty as AppResult.Error).type)
        assertTrue(malformed is AppResult.Error)
        assertEquals(AppErrorType.DATA, (malformed as AppResult.Error).type)
    }
}
