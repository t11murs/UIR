package com.example.uir_android.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateSeminarPointsUseCaseTest {
    private val useCase = ValidateSeminarPointsUseCase()

    @Test
    fun `backend boundary values are valid`() {
        assertTrue(useCase("-50").isValid)
        assertTrue(useCase("100").isValid)
    }

    @Test
    fun `values outside backend boundaries are invalid`() {
        assertFalse(useCase("-50.1").isValid)
        assertFalse(useCase("100.1").isValid)
    }

    @Test
    fun `decimal comma is accepted`() {
        val result = useCase("2,5")

        assertTrue(result.isValid)
        assertEquals(2.5, result.value ?: 0.0, 0.0)
    }

    @Test
    fun `empty and non numeric values are invalid`() {
        assertFalse(useCase("").isValid)
        assertFalse(useCase("балл").isValid)
    }
}
