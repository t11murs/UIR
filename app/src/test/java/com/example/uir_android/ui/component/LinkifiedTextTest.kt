package com.example.uir_android.ui.component

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkifiedTextTest {
    @Test
    fun `detects url and email without trailing punctuation`() {
        val text = "Материалы: https://example.org/file.pdf. Сайт: www.example.org, почта: test@example.org"
        val result = buildLinkifiedText(text, Color.Blue)
        val links = result.getStringAnnotations(0, result.length).map { it.item }

        assertEquals(
            listOf(
                "https://example.org/file.pdf",
                "https://www.example.org",
                "mailto:test@example.org"
            ),
            links
        )
    }

    @Test
    fun `converts telegram handle to telegram link`() {
        val text = "Обратная связь: @t1murs"
        val result = buildLinkifiedText(text, Color.Blue)

        assertEquals(
            listOf("https://t.me/t1murs"),
            result.getStringAnnotations(0, result.length).map { it.item }
        )
    }
}
