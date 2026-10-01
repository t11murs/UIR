package com.example.uir_android.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionCookieCipherTest {
    @Test
    fun encryptedCookieRoundTripsThroughAndroidKeystore() {
        val cipher = SessionCookieCipher()
        val cookie = "XSRF-TOKEN=token-value; laravel_session=session-value"

        val encrypted = cipher.encrypt(cookie)
        val decoded = cipher.decrypt(encrypted)

        assertTrue(encrypted.startsWith("ks1:"))
        assertFalse(encrypted.contains("session-value"))
        assertEquals(cookie, decoded.value)
        assertFalse(decoded.requiresMigration)
    }
}
