package com.example.uir_android.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

internal data class DecodedSessionCookie(
    val value: String,
    val requiresMigration: Boolean = false
)

@Singleton
class SessionCookieCipher @Inject constructor() {
    fun encrypt(value: String): String {
        if (value.isBlank()) return ""

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(ASSOCIATED_DATA)
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        ).joinToString(SEPARATOR)
    }

    internal fun decrypt(storedValue: String): DecodedSessionCookie {
        if (storedValue.isBlank()) return DecodedSessionCookie("")
        if (!storedValue.startsWith("$FORMAT_VERSION$SEPARATOR")) {
            val isLegacyCookie = isPlausibleCookieHeader(storedValue)
            return DecodedSessionCookie(
                value = storedValue.takeIf { isLegacyCookie }.orEmpty(),
                requiresMigration = isLegacyCookie
            )
        }

        val parts = storedValue.split(SEPARATOR, limit = 3)
        if (parts.size != 3) return DecodedSessionCookie("")

        return try {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            cipher.updateAAD(ASSOCIATED_DATA)
            DecodedSessionCookie(
                value = String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
                    .takeIf(::isPlausibleCookieHeader)
                    .orEmpty()
            )
        } catch (_: GeneralSecurityException) {
            DecodedSessionCookie("")
        } catch (_: IOException) {
            DecodedSessionCookie("")
        } catch (_: IllegalArgumentException) {
            DecodedSessionCookie("")
        }
    }

    private fun getOrCreateKey(): SecretKey = synchronized(keyLock) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "uir_session_cookie_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "ks1"
        const val SEPARATOR = ":"
        const val GCM_TAG_LENGTH_BITS = 128
        val ASSOCIATED_DATA = "uir_android_session_cookie_v1"
            .toByteArray(StandardCharsets.UTF_8)
        val keyLock = Any()

        fun isPlausibleCookieHeader(value: String): Boolean =
            value.split(';').any { part ->
                val separatorIndex = part.indexOf('=')
                separatorIndex > 0 && separatorIndex < part.lastIndex
            }
    }
}
