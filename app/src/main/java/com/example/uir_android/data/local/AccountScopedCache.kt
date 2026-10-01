package com.example.uir_android.data.local

import java.util.Locale

internal class AccountScopedCache<K, V> {
    private val lock = Any()
    private var ownerKey: String = ""
    private var values: Map<K, V> = emptyMap()

    fun selectOwner(rawOwnerKey: String) {
        val normalizedOwner = normalizeAccountOwnerKey(rawOwnerKey)
        synchronized(lock) {
            if (ownerKey != normalizedOwner) {
                ownerKey = normalizedOwner
                values = emptyMap()
            }
        }
    }

    fun get(rawOwnerKey: String, key: K): V? {
        val normalizedOwner = normalizeAccountOwnerKey(rawOwnerKey)
        return synchronized(lock) {
            values[key].takeIf { normalizedOwner.isNotBlank() && ownerKey == normalizedOwner }
        }
    }

    fun values(rawOwnerKey: String): List<V> {
        val normalizedOwner = normalizeAccountOwnerKey(rawOwnerKey)
        return synchronized(lock) {
            if (normalizedOwner.isNotBlank() && ownerKey == normalizedOwner) {
                values.values.toList()
            } else {
                emptyList()
            }
        }
    }

    fun replaceIfCurrent(rawOwnerKey: String, newValues: Map<K, V>): Boolean {
        val normalizedOwner = normalizeAccountOwnerKey(rawOwnerKey)
        return synchronized(lock) {
            if (normalizedOwner.isBlank() || ownerKey != normalizedOwner) {
                false
            } else {
                values = newValues.toMap()
                true
            }
        }
    }

    fun putIfCurrent(rawOwnerKey: String, key: K, value: V): Boolean {
        val normalizedOwner = normalizeAccountOwnerKey(rawOwnerKey)
        return synchronized(lock) {
            if (normalizedOwner.isBlank() || ownerKey != normalizedOwner) {
                false
            } else {
                values = values + (key to value)
                true
            }
        }
    }
}

internal fun normalizeAccountOwnerKey(email: String): String =
    email.trim().lowercase(Locale.ROOT)
