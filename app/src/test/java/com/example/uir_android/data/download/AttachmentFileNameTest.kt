package com.example.uir_android.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentFileNameTest {
    @Test
    fun `unsafe characters are replaced`() {
        assertEquals(
            "report_final_.pdf",
            sanitizeAttachmentFileName("report:final?.pdf", "http://server/file")
        )
    }

    @Test
    fun `file name falls back to decoded url segment`() {
        assertEquals(
            "Отчёт.pdf",
            sanitizeAttachmentFileName("", "http://server/download/%D0%9E%D1%82%D1%87%D1%91%D1%82.pdf")
        )
    }
}
