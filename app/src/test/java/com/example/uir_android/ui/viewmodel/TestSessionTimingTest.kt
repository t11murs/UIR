package com.example.uir_android.ui.viewmodel

import com.example.uir_android.core.common.AppErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestSessionTimingTest {
    @Test
    fun `timed attempt is submitted only at server deadline`() {
        assertFalse(shouldAutoSubmitTimedAttempt(2L))
        assertFalse(shouldAutoSubmitTimedAttempt(1L))
        assertTrue(shouldAutoSubmitTimedAttempt(0L))
        assertTrue(shouldAutoSubmitTimedAttempt(-1L))
    }

    @Test
    fun `failed regular submit is retried as timeout after deadline`() {
        assertTrue(
            shouldRetrySubmissionAsTimedOut(
                dueToTimeout = false,
                deadlineReached = true,
                errorType = AppErrorType.DATA
            )
        )
        assertFalse(
            shouldRetrySubmissionAsTimedOut(
                dueToTimeout = true,
                deadlineReached = true,
                errorType = AppErrorType.SERVER
            )
        )
        assertFalse(
            shouldRetrySubmissionAsTimedOut(
                dueToTimeout = false,
                deadlineReached = false,
                errorType = AppErrorType.SERVER
            )
        )
    }

    @Test
    fun `authentication errors are not retried after deadline`() {
        assertFalse(
            shouldRetrySubmissionAsTimedOut(
                dueToTimeout = false,
                deadlineReached = true,
                errorType = AppErrorType.AUTHENTICATION
            )
        )
        assertFalse(
            shouldRetrySubmissionAsTimedOut(
                dueToTimeout = false,
                deadlineReached = true,
                errorType = AppErrorType.AUTHORIZATION
            )
        )
    }

    @Test
    fun `remaining seconds do not expire before deadline`() {
        assertEquals(1L, remainingTestSeconds(deadlineMillis = 1_001L, nowMillis = 1L))
        assertEquals(1L, remainingTestSeconds(deadlineMillis = 1_001L, nowMillis = 1_000L))
        assertEquals(0L, remainingTestSeconds(deadlineMillis = 1_001L, nowMillis = 1_001L))
    }

    @Test
    fun `monotonic deadline is based only on server remaining time`() {
        val deadline = monotonicDeadlineMillis(
            startedAtElapsedRealtime = 50_000L,
            remainingSeconds = 120L
        )

        assertEquals(170_000L, deadline)
        assertEquals(120L, remainingTestSeconds(deadline, 50_000L))
        assertEquals(60L, remainingTestSeconds(deadline, 110_000L))
        assertEquals(0L, remainingTestSeconds(deadline, 170_000L))
    }

    @Test
    fun `negative server remaining time expires immediately`() {
        assertEquals(
            10_000L,
            monotonicDeadlineMillis(
                startedAtElapsedRealtime = 10_000L,
                remainingSeconds = -1L
            )
        )
    }

    @Test
    fun `control timer cannot exceed configured duration`() {
        assertEquals(
            45L * 60L,
            boundedAttemptRemainingSeconds(
                reportedRemainingSeconds = 100L * 60L,
                configuredMinutes = 45
            )
        )
        assertEquals(
            20L * 60L,
            boundedAttemptRemainingSeconds(
                reportedRemainingSeconds = 20L * 60L,
                configuredMinutes = 45
            )
        )
    }

    @Test
    fun `server deadline is parsed in moscow timezone`() {
        assertNotNull(parseTestDeadlineMillis("2026-09-07 14:30:00"))
    }

}
