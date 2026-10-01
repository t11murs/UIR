package com.example.uir_android.ui.state

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TestDetailUiStateTest {
    @Test
    fun `only active test can be edited`() {
        assertTrue(TestDetailUiState(status = TestSessionStatus.ACTIVE).canEdit)
        assertFalse(TestDetailUiState(status = TestSessionStatus.SUBMITTING).canEdit)
        assertFalse(TestDetailUiState(status = TestSessionStatus.FINISHED).canEdit)
        assertFalse(TestDetailUiState(status = TestSessionStatus.EXPIRED).canEdit)
    }

    @Test
    fun `loading and submitting flags derive from status`() {
        assertTrue(TestDetailUiState(status = TestSessionStatus.LOADING).isLoading)
        assertTrue(TestDetailUiState(status = TestSessionStatus.SUBMITTING).isSubmitting)
    }
}
