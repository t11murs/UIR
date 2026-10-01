package com.example.uir_android.data.remote

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ServerTestSummaryContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `summary decodes compact latest result`() {
        val summary = json.decodeFromString<ServerTest>(
            """
            {
              "id": 17,
              "name": "Тест",
              "attempts": 2,
              "latestResult": {
                "runId": 91,
                "score": 8.5,
                "total": 10.0,
                "markRu": "4",
                "markEu": "B",
                "finishedAt": "2026-09-27 12:00:00"
              }
            }
            """.trimIndent()
        )

        assertNotNull(summary.latestResult)
        val result = requireNotNull(summary.latestResult)
        assertEquals(91, result.runId)
        assertEquals(8.5, result.score, 0.0)
        assertEquals("4", result.markRu)
    }
}
