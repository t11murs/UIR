package com.example.uir_android.ui.navigation

import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.ui.state.ActiveAssessmentLock
import com.example.uir_android.ui.state.ActiveAssessmentType
import com.example.uir_android.ui.state.TestsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationTest {
    @Test
    fun `logged in navigation stays blocked until assessment reconciliation completes`() {
        val locallyLoadedState = TestsUiState(
            hasLoaded = true,
            assessmentGuardReady = false,
            localDraftTestIds = setOf(31)
        )

        assertTrue(shouldBlockAssessmentNavigation(isLoggedIn = true, locallyLoadedState))
        assertFalse(
            shouldBlockAssessmentNavigation(
                isLoggedIn = true,
                locallyLoadedState.copy(assessmentGuardReady = true)
            )
        )
        assertFalse(shouldBlockAssessmentNavigation(isLoggedIn = false, TestsUiState()))
    }

    @Test
    fun `no active attempt does not lock navigation`() {
        val state = TestsUiState(
            hasLoaded = true,
            tests = listOf(TestSummary(id = 10, attempts = 1, hasCurrentRun = false))
        )

        assertNull(state.activeAssessmentRoute())
    }

    @Test
    fun `active test restores test route`() {
        val state = TestsUiState(
            hasLoaded = true,
            tests = listOf(
                TestSummary(id = 15, hasCurrentRun = true, currentResultId = 101)
            )
        )

        assertEquals(
            AppDestination.TestDetail.createRoute(15),
            state.activeAssessmentRoute()
        )
    }

    @Test
    fun `immediately registered attempt locks navigation before list refresh`() {
        val state = TestsUiState(
            hasLoaded = true,
            testsRequestSucceeded = true,
            activeAssessmentLock = ActiveAssessmentLock(
                type = ActiveAssessmentType.EMULATOR_CONTROL,
                assessmentId = 27,
                runId = 301
            )
        )

        assertEquals(
            AppDestination.EmulatorControlDetail.createRoute(27),
            state.activeAssessmentRoute()
        )
    }

    @Test
    fun `latest active attempt wins when legacy data contains several runs`() {
        val state = TestsUiState(
            hasLoaded = true,
            tests = listOf(
                TestSummary(id = 15, hasCurrentRun = true, currentResultId = 101)
            ),
            emulatorControls = listOf(
                TestSummary(id = 22, hasCurrentRun = true, currentResultId = 105)
            )
        )

        assertEquals(
            AppDestination.EmulatorControlDetail.createRoute(22),
            state.activeAssessmentRoute()
        )
    }

    @Test
    fun `local test draft restores attempt while server is unavailable`() {
        val state = TestsUiState(
            hasLoaded = true,
            localDraftTestIds = setOf(31)
        )

        assertEquals(
            AppDestination.TestDetail.createRoute(31),
            state.activeAssessmentRoute()
        )
    }

    @Test
    fun `successful server response ignores stale local test draft`() {
        val state = TestsUiState(
            hasLoaded = true,
            testsRequestSucceeded = true,
            localDraftTestIds = setOf(31)
        )

        assertNull(state.activeAssessmentRoute())
    }

    @Test
    fun `local emulator control draft restores control while server is unavailable`() {
        val state = TestsUiState(
            hasLoaded = true,
            localDraftControlIds = setOf(42)
        )

        assertEquals(
            AppDestination.EmulatorControlDetail.createRoute(42),
            state.activeAssessmentRoute()
        )
    }
}
