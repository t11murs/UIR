package com.example.uir_android.data.remote

import com.example.uir_android.domain.model.SeminarPointsLimitError
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceBatchContractTest {
    @Test
    fun `lecture batch contains one operation and every update`() {
        val request = LectureAttendanceBatchRequest(
            operationId = "lecture-operation-0001",
            updates = listOf(
                ServerLectureAttendanceUpdateDto(10, 2, true),
                ServerLectureAttendanceUpdateDto(11, 2, false)
            )
        )

        val json = serverJson.encodeToString(request)

        assertTrue(json.contains("\"operationId\":\"lecture-operation-0001\""))
        assertTrue(json.contains("\"studentId\":10"))
        assertTrue(json.contains("\"studentId\":11"))
    }

    @Test
    fun `seminar batch preserves presence and points changes together`() {
        val request = SeminarAttendanceBatchRequest(
            operationId = "seminar-operation-0001",
            updates = listOf(
                ServerSeminarAttendanceUpdateDto(
                    seminarPassId = 42,
                    coursePlanId = 7,
                    sectionNumber = 2,
                    present = true,
                    presenceChanged = true,
                    workPoints = 3.5,
                    pointsChanged = true
                )
            )
        )

        val json = serverJson.encodeToString(request)

        assertTrue(json.contains("\"seminarPassId\":42"))
        assertTrue(json.contains("\"presenceChanged\":true"))
        assertTrue(json.contains("\"workPoints\":3.5"))
        assertTrue(json.contains("\"pointsChanged\":true"))
    }

    @Test
    fun `batch response carries server revision`() {
        val envelope = serverJson.decodeFromString<AttendanceBatchEnvelope>(
            """{"success":true,"revision":"revision-42"}"""
        )

        assertTrue(envelope.success)
        assertEquals("revision-42", envelope.revision)
    }

    @Test
    fun `seminar limit rejection identifies exact row and limit`() {
        val envelope = serverJson.decodeFromString<AttendanceBatchEnvelope>(
            """
            {
              "success": false,
              "message": "Баллы превышают максимум раздела: 5",
              "errorCode": "SEMINAR_POINTS_LIMIT_EXCEEDED",
              "seminarPassId": 42,
              "maxWorkPoints": 5,
              "actualWorkPoints": 8
            }
            """.trimIndent()
        )

        val error = envelope.toSeminarLimitErrorOrNull()
        val details = error?.details as SeminarPointsLimitError

        assertEquals(42, details.seminarPassId)
        assertEquals(5.0, details.maxWorkPoints, 0.0)
        assertEquals(8.0, details.actualWorkPoints ?: 0.0, 0.0)
    }
}
