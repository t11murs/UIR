package com.example.uir_android.data.remote

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerTestQuestionContentContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ordered content items preserve image-only answer row index`() {
        val question = json.decodeFromString<ServerTestQuestion>(
            """
            {
              "id": 23,
              "count": 4,
              "typeCode": 5,
              "contentItems": [
                {"index": 0, "type": "text", "text": "Первая строка", "imageUrl": ""},
                {"index": 1, "type": "image", "text": "", "imageUrl": "https://example.test/row.jpg"},
                {"index": 2, "type": "text", "text": "Третья строка", "imageUrl": ""}
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf(0, 1, 2), question.contentItems.map { it.index })
        assertEquals("https://example.test/row.jpg", question.contentItems[1].imageUrl)
    }
}
