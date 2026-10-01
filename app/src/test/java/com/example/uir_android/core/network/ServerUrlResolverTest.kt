package com.example.uir_android.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerUrlResolverTest {
    private val externalBaseUrl = "https://mephi22.ru/"

    @Test
    fun `relative path is resolved against external server`() {
        assertEquals(
            "https://mephi22.ru/download/news/report.pdf",
            resolveServerUrl("download/news/report.pdf", externalBaseUrl)
        )
    }

    @Test
    fun `localhost url is rewritten to external server`() {
        assertEquals(
            "https://mephi22.ru/download/news/report.pdf?version=2",
            resolveServerUrl(
                "http://localhost:8080/download/news/report.pdf?version=2",
                externalBaseUrl
            )
        )
    }

    @Test
    fun `emulator host is rewritten to external server`() {
        assertEquals(
            "https://mephi22.ru/files/lecture.pdf",
            resolveServerUrl("http://10.0.2.2:8080/files/lecture.pdf", externalBaseUrl)
        )
    }

    @Test
    fun `same server with internal port is canonicalized`() {
        assertEquals(
            "https://mephi22.ru/download/file.docx",
            resolveServerUrl("http://mephi22.ru:8080/download/file.docx", externalBaseUrl)
        )
    }

    @Test
    fun `unrelated external link is unchanged`() {
        assertEquals(
            "https://example.org/material.pdf",
            resolveServerUrl("https://example.org/material.pdf", externalBaseUrl)
        )
    }

    @Test
    fun `mailto link is not rewritten as server url`() {
        assertEquals(
            "mailto:algorithms.theory@yandex.ru",
            resolveServerUrl("mailto:algorithms.theory@yandex.ru", externalBaseUrl)
        )
    }
}
