package com.example.uir_android.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun `stored values are restored`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue("SYSTEM"))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.fromStoredValue("LIGHT"))
        assertEquals(AppThemeMode.DARK, AppThemeMode.fromStoredValue("DARK"))
    }

    @Test
    fun `unknown stored value falls back to system theme`() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromStoredValue("UNKNOWN"))
    }
}
