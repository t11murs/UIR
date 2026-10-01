package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppResult
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicApiV1ContractTest {
    @Test
    fun `profile envelope decodes versioned academic data`() {
        val envelope = serverJson.decodeFromString<AcademicProfileV1Envelope>(
            """
            {
              "apiVersion": 1,
              "success": true,
              "profile": {
                "user": {
                  "id": 7,
                  "firstName": "Иван",
                  "lastName": "Иванов",
                  "email": "student@example.com",
                  "group": 12,
                  "role": "Студент"
                },
                "groupName": "Б22-524",
                "summary": {
                  "sectionsScore": "37",
                  "examScore": "43",
                  "totalScore": "80",
                  "mark": "C"
                },
                "results": [],
                "sections": [],
                "warnings": []
              }
            }
            """.trimIndent()
        )

        assertEquals(ACADEMIC_API_VERSION, envelope.apiVersion)
        assertEquals("Б22-524", envelope.profile?.groupName)
        assertEquals("80", envelope.profile?.summary?.totalScore)
    }

    @Test
    fun `lecture statement keeps nullable limits and student identity`() {
        val envelope = serverJson.decodeFromString<LectureStatementV1Envelope>(
            """
            {
              "apiVersion": 1,
              "success": true,
              "statement": {
                "groupId": 12,
                "coursePlanId": 3,
                "rows": [{
                  "student": {
                    "id": 7,
                    "first_name": "Иван",
                    "last_name": "Иванов",
                    "email": "student@example.com",
                    "group": 12,
                    "role": "Студент",
                    "group_name": "Б22-524"
                  },
                  "passes": [{
                    "lecturePlanId": null,
                    "lectureId": 5,
                    "present": true,
                    "title": "Лекция 5",
                    "limit": null
                  }]
                }],
                "csrfToken": ""
              }
            }
            """.trimIndent()
        )

        val statement = requireNotNull(envelope.statement)
        assertEquals("Иванов", statement.rows.single().student.lastName)
        assertTrue(statement.rows.single().passes.single().present)
        assertEquals(null, statement.rows.single().passes.single().limit)
    }

    @Test
    fun `unknown academic API version is rejected explicitly`() {
        val result = unsupportedAcademicApi(actualVersion = 2)

        assertTrue(result.message.contains("2"))
    }

    @Test
    fun `explicit empty list is a valid empty state`() {
        val envelope = serverJson.decodeFromString<AcademicGroupsV1Envelope>(
            """{"apiVersion":1,"success":true,"groups":[]}"""
        )

        val result = academicPayloadResult(
            apiVersion = envelope.apiVersion,
            success = envelope.success,
            message = envelope.message,
            payload = envelope.groups,
            payloadName = "groups",
            validate = ::validateAcademicGroups
        )

        assertTrue(result is AppResult.Success)
        assertEquals(emptyList<ServerAcademicGroupDto>(), (result as AppResult.Success).data)
    }

    @Test
    fun `missing successful payload is not converted to empty state`() {
        val envelope = serverJson.decodeFromString<AcademicGroupsV1Envelope>(
            """{"apiVersion":1,"success":true}"""
        )

        val result = academicPayloadResult(
            apiVersion = envelope.apiVersion,
            success = envelope.success,
            message = envelope.message,
            payload = envelope.groups,
            payloadName = "groups",
            validate = ::validateAcademicGroups
        )

        assertTrue(result is AppResult.Error)
        assertTrue((result as AppResult.Error).message.contains("groups"))
    }

    @Test
    fun `missing API version fails JSON contract decoding`() {
        val result = runCatching {
            serverJson.decodeFromString<AcademicGroupsV1Envelope>(
                """{"success":true,"groups":[]}"""
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun `group without stable identity is rejected`() {
        val error = validateAcademicGroups(
            listOf(ServerAcademicGroupDto(id = 0, name = "Б22-524"))
        )

        assertTrue(!error.isNullOrBlank())
    }
}
