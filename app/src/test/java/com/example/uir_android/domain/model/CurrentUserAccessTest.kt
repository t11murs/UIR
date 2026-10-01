package com.example.uir_android.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentUserAccessTest {
    @Test
    fun `only backend admin roles can edit statements`() {
        assertTrue(user("Админ").canEditStatements)
        assertTrue(user("Преподаватель").canEditStatements)
        assertFalse(user("Студент").canEditStatements)
        assertFalse(user("Староста").canEditStatements)
        assertFalse(user("Семинарист").canEditStatements)
    }

    @Test
    fun `only exact steward role has steward access`() {
        assertTrue(user("Староста").isSteward)
        assertFalse(user("Студент").isSteward)
        assertFalse(user("Преподаватель").isSteward)
    }

    private fun user(role: String) = CurrentUser(
        id = 1,
        firstName = "Иван",
        lastName = "Иванов",
        email = "user@example.com",
        groupId = 1,
        role = role
    )
}
