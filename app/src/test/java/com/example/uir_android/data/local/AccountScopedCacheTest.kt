package com.example.uir_android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountScopedCacheTest {
    @Test
    fun `cache is isolated and cleared when owner changes`() {
        val cache = AccountScopedCache<Int, String>()
        cache.selectOwner(" First@Example.com ")
        assertTrue(cache.putIfCurrent("first@example.com", 1, "first-user-test"))

        assertEquals("first-user-test", cache.get("FIRST@example.com", 1))
        assertNull(cache.get("second@example.com", 1))

        cache.selectOwner("second@example.com")

        assertNull(cache.get("first@example.com", 1))
        assertTrue(cache.values("second@example.com").isEmpty())
    }

    @Test
    fun `late response from previous owner cannot repopulate cache`() {
        val cache = AccountScopedCache<Int, String>()
        cache.selectOwner("first@example.com")
        cache.selectOwner("second@example.com")

        assertFalse(
            cache.replaceIfCurrent("first@example.com", mapOf(1 to "late-response"))
        )
        assertTrue(cache.values("second@example.com").isEmpty())
    }
}
