package com.example.uir_android.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.uir_android.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "tm_settings")

data class LocalAccountSession(
    val email: String = "",
    val hasAccount: Boolean = false,
    val isLoggedIn: Boolean = false,
    val cookieHeader: String = ""
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val maxRunSteps = intPreferencesKey("max_run_steps")
        val runDelayMs = longPreferencesKey("run_delay_ms")
        val debugEnabled = booleanPreferencesKey("debug_enabled")
        val authEmail = stringPreferencesKey("auth_email")
        val authCookieHeader = stringPreferencesKey("auth_cookie_header")
        val loggedIn = booleanPreferencesKey("logged_in")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            AppSettings(
                maxRunSteps = preferences[Keys.maxRunSteps] ?: 200,
                runDelayMs = preferences[Keys.runDelayMs] ?: 150L,
                debugEnabled = preferences[Keys.debugEnabled] ?: false
            )
        }

    val authSessionFlow: Flow<LocalAccountSession> = context.settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences ->
            val email = preferences[Keys.authEmail].orEmpty()
            val cookieHeader = preferences[Keys.authCookieHeader].orEmpty()
            LocalAccountSession(
                email = email,
                hasAccount = email.isNotBlank(),
                isLoggedIn = (preferences[Keys.loggedIn] ?: false) && cookieHeader.isNotBlank(),
                cookieHeader = cookieHeader
            )
        }

    suspend fun currentSettings(): AppSettings = settingsFlow.first()

    suspend fun currentAuthSession(): LocalAccountSession = authSessionFlow.first()

    suspend fun setMaxRunSteps(value: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.maxRunSteps] = value.coerceAtLeast(1)
        }
    }

    suspend fun setRunDelayMs(value: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.runDelayMs] = value.coerceAtLeast(0L)
        }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.debugEnabled] = enabled
        }
    }

    suspend fun saveServerSession(email: String, cookieHeader: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.authEmail] = email.trim()
            preferences[Keys.authCookieHeader] = cookieHeader
            preferences[Keys.loggedIn] = true
        }
    }

    suspend fun clearServerSession() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.authEmail)
            preferences.remove(Keys.authCookieHeader)
            preferences[Keys.loggedIn] = false
        }
    }
}
