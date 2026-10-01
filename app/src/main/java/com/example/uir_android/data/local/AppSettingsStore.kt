package com.example.uir_android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.domain.model.AppSettings
import com.example.uir_android.domain.model.AppThemeMode
import com.example.uir_android.domain.model.LocalStorageState
import com.example.uir_android.domain.model.LocalStorageStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalAccountSession(
    val email: String = "",
    val hasAccount: Boolean = false,
    val isLoggedIn: Boolean = false,
    val cookieHeader: String = ""
)

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionCookieCipher: SessionCookieCipher,
    private val dispatchers: AppDispatchers,
    @ApplicationScope applicationScope: CoroutineScope
) {
    private val _storageState = MutableStateFlow(LocalStorageState())
    val storageState = _storageState.asStateFlow()

    private val settingsDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler {
            _storageState.value = LocalStorageState(
                status = LocalStorageStatus.CORRUPTION_RECOVERED,
                message = "Локальные настройки были повреждены и восстановлены. Войдите в аккаунт снова."
            )
            emptyPreferences()
        },
        scope = applicationScope,
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    private val preferencesFlow: Flow<Preferences> = settingsDataStore.data
        .retryWhen { cause, attempt ->
            if (cause !is IOException) return@retryWhen false

            _storageState.value = LocalStorageState(
                status = LocalStorageStatus.RECOVERING,
                message = "Не удалось прочитать локальные данные. Повторяем попытку…"
            )
            delay(retryDelayMillis(attempt))
            true
        }
        .onEach {
            if (_storageState.value.status == LocalStorageStatus.RECOVERING) {
                _storageState.value = LocalStorageState()
            }
        }
        .catch { cause ->
            _storageState.value = LocalStorageState(
                status = LocalStorageStatus.FAILED,
                message = "Критическая ошибка локального хранилища: ${cause.javaClass.simpleName}"
            )
        }

    private object Keys {
        val maxRunSteps = intPreferencesKey("max_run_steps")
        val runDelayMs = longPreferencesKey("run_delay_ms")
        val debugEnabled = booleanPreferencesKey("debug_enabled")
        val themeMode = stringPreferencesKey("theme_mode")
        val authEmail = stringPreferencesKey("auth_email")
        val authCookieHeader = stringPreferencesKey("auth_cookie_header")
        val loggedIn = booleanPreferencesKey("logged_in")
    }

    val settingsFlow: Flow<AppSettings> = preferencesFlow
        .map { preferences ->
            AppSettings(
                maxRunSteps = preferences[Keys.maxRunSteps] ?: 200,
                runDelayMs = preferences[Keys.runDelayMs] ?: 150L,
                debugEnabled = preferences[Keys.debugEnabled] ?: false,
                themeMode = AppThemeMode.fromStoredValue(preferences[Keys.themeMode])
            )
        }

    val authSessionFlow: Flow<LocalAccountSession> = preferencesFlow
        .map { preferences ->
            val email = preferences[Keys.authEmail].orEmpty()
            val cookieHeader = sessionCookieCipher.decrypt(
                preferences[Keys.authCookieHeader].orEmpty()
            ).value
            LocalAccountSession(
                email = email,
                hasAccount = email.isNotBlank(),
                isLoggedIn = (preferences[Keys.loggedIn] ?: false) && cookieHeader.isNotBlank(),
                cookieHeader = cookieHeader
            )
        }
        .flowOn(dispatchers.io)

    init {
        applicationScope.launch {
            migratePlaintextSessionCookie()
        }
    }

    suspend fun currentSettings(): AppSettings = settingsFlow.first()

    suspend fun currentAuthSession(): LocalAccountSession = authSessionFlow.first()

    suspend fun setMaxRunSteps(value: Int) {
        settingsDataStore.edit { preferences ->
            preferences[Keys.maxRunSteps] = value.coerceAtLeast(1)
        }
    }

    suspend fun setRunDelayMs(value: Long) {
        settingsDataStore.edit { preferences ->
            preferences[Keys.runDelayMs] = value.coerceAtLeast(0L)
        }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        settingsDataStore.edit { preferences ->
            preferences[Keys.debugEnabled] = enabled
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        settingsDataStore.edit { preferences ->
            preferences[Keys.themeMode] = mode.name
        }
    }

    suspend fun saveServerSession(email: String, cookieHeader: String) {
        withContext(dispatchers.io) {
            val encryptedCookie = sessionCookieCipher.encrypt(cookieHeader)
            settingsDataStore.edit { preferences ->
                preferences[Keys.authEmail] = email.trim()
                preferences[Keys.authCookieHeader] = encryptedCookie
                preferences[Keys.loggedIn] = encryptedCookie.isNotBlank()
            }
            _storageState.value = LocalStorageState()
        }
    }

    suspend fun clearServerSession() {
        settingsDataStore.edit { preferences ->
            preferences.remove(Keys.authEmail)
            preferences.remove(Keys.authCookieHeader)
            preferences[Keys.loggedIn] = false
        }
    }

    private suspend fun migratePlaintextSessionCookie() {
        val preferences = preferencesFlow.first()
        val storedCookie = preferences[Keys.authCookieHeader].orEmpty()
        val decoded = sessionCookieCipher.decrypt(storedCookie)
        if (!decoded.requiresMigration || decoded.value.isBlank()) return

        val encryptedCookie = sessionCookieCipher.encrypt(decoded.value)
        settingsDataStore.edit { current ->
            if (current[Keys.authCookieHeader] == storedCookie) {
                current[Keys.authCookieHeader] = encryptedCookie
            }
        }
    }

    private companion object {
        const val DATASTORE_NAME = "tm_settings"

        fun retryDelayMillis(attempt: Long): Long {
            val exponent = attempt.coerceAtMost(5).toInt()
            return 1_000L * (1L shl exponent)
        }
    }
}
