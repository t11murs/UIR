package com.example.uir_android.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttachmentResumeTest {
    @Test
    fun `partial response starts at requested offset and exposes total size`() {
        val contentRange = "bytes 1024-2047/4096"

        assertEquals(1024L, contentRangeStart(contentRange))
        assertEquals(
            4096L,
            responseTotalBytes(
                responseCode = 206,
                existingBytes = 1024L,
                bodyLength = 1024L,
                contentRange = contentRange
            )
        )
    }

    @Test
    fun `partial response without total falls back to offset plus body size`() {
        assertEquals(
            1536L,
            responseTotalBytes(
                responseCode = 206,
                existingBytes = 1024L,
                bodyLength = 512L,
                contentRange = "bytes 1024-1535/*"
            )
        )
    }

    @Test
    fun `unknown body size stays unknown`() {
        assertNull(
            responseTotalBytes(
                responseCode = 200,
                existingBytes = 0L,
                bodyLength = -1L,
                contentRange = null
            )
        )
    }

    @Test
    fun `unsatisfied range exposes completed server size`() {
        assertEquals(4096L, completedRangeSize("bytes */4096"))
    }
}
