package com.example.uir_android.domain.usecase

import com.example.uir_android.domain.model.CurrentUser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateLectureAttendanceSelectionUseCaseTest {
    private val useCase = ValidateLectureAttendanceSelectionUseCase()

    @Test
    fun `exact number of students is valid`() {
        assertTrue(useCase(selectedCount = 15, requiredCount = 15).isValid)
    }

    @Test
    fun `fewer students than required is invalid`() {
        val result = useCase(selectedCount = 14, requiredCount = 15)

        assertFalse(result.isValid)
        assertTrue(result.message.contains("ещё 1"))
    }

    @Test
    fun `more students than required is invalid`() {
        assertFalse(useCase(selectedCount = 16, requiredCount = 15).isValid)
    }

    @Test
    fun `missing or zero limit disables attendance`() {
        assertFalse(useCase(selectedCount = 0, requiredCount = null).isValid)
        assertFalse(useCase(selectedCount = 0, requiredCount = 0).isValid)
    }

    @Test
    fun `statement rights match backend middleware`() {
        assertTrue(userWithRole("Админ").canEditStatements)
        assertTrue(userWithRole("Преподаватель").canEditStatements)
        assertFalse(userWithRole("Старший преподаватель").canEditStatements)
        assertTrue(userWithRole("Староста").isSteward)
    }

    private fun userWithRole(role: String) = CurrentUser(
        id = 1,
        firstName = "Иван",
        lastName = "Иванов",
        email = "user@example.com",
        groupId = 1,
        role = role
    )
}
