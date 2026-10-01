package com.example.uir_android.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractNewsLinksUseCaseTest {
    private val useCase = ExtractNewsLinksUseCase()

    @Test
    fun `extracts unique links and removes trailing punctuation`() {
        val result = useCase(
            "Материалы: https://example.org/doc.pdf. Повтор: https://example.org/doc.pdf " +
                "и http://server.local/page)."
        )

        assertEquals(
            listOf("https://example.org/doc.pdf", "http://server.local/page"),
            result
        )
    }
}
