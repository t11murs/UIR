package com.example.uir_android.core.network

import com.example.uir_android.BuildConfig
import com.example.uir_android.core.settings.AppSettingsStore
import com.example.uir_android.core.util.AppDispatchers
import com.example.uir_android.core.util.AppResult
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

private val csrfInputRegex = Regex("name=[\"']_token[\"'][^>]*value=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val csrfMetaRegex = Regex("name=[\"']csrf[_-]?token[\"'][^>]*content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
private val loginFormRegex = Regex("id=[\"']login-form[\"']|/auth/login", RegexOption.IGNORE_CASE)

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()
}

@Singleton
class ServerAuthService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsStore: AppSettingsStore,
    private val dispatchers: AppDispatchers
) {
    suspend fun login(email: String, password: String): AppResult<Unit> = withContext(dispatchers.io) {
        val safeEmail = email.trim()
        val safePassword = password.trim()
        if (safeEmail.isBlank() || safePassword.isBlank()) {
            return@withContext AppResult.Error("Введите email и пароль")
        }

        val homeUrl = buildUrl("home")
        val loginUrl = buildUrl("auth", "login")
        val origin = buildOrigin()
        val cookieJar = MemoryCookieJar()
        val loginClient = httpClient.newBuilder()
            .cookieJar(cookieJar)
            .build()

        val preflightHtml = try {
            loginClient.newCall(
                Request.Builder()
                    .url(homeUrl)
                    .get()
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error("Сервер вернул ошибку ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (error: IOException) {
            return@withContext AppResult.Error("Не удалось подключиться к серверу", error)
        }

        val csrfToken = extractCsrfToken(preflightHtml)
            ?: return@withContext AppResult.Error("Не удалось получить CSRF токен с сервера")

        val resultHtml = try {
            loginClient.newCall(
                Request.Builder()
                    .url(loginUrl)
                    .post(
                        FormBody.Builder()
                            .add("_token", csrfToken)
                            .add("email", safeEmail)
                            .add("password", safePassword)
                            .build()
                    )
                    .header("Origin", origin)
                    .header("Referer", homeUrl.toString())
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.Error("Ошибка авторизации: ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (error: IOException) {
            return@withContext AppResult.Error("Не удалось отправить данные авторизации", error)
        }

        if (loginFormRegex.containsMatchIn(resultHtml)) {
            return@withContext AppResult.Error("Неверный email или пароль")
        }

        val cookieHeader = cookieJar.toCookieHeader()
        if (cookieHeader.isBlank()) {
            return@withContext AppResult.Error("Сервер не вернул cookie сессии")
        }

        settingsStore.saveServerSession(safeEmail, cookieHeader)
        AppResult.Success(Unit, "Вход выполнен")
    }

    suspend fun acceptWebSession(cookieHeader: String, email: String = ""): AppResult<Unit> = withContext(dispatchers.io) {
        if (cookieHeader.isBlank()) {
            return@withContext AppResult.Error("Не удалось получить cookies из web-формы")
        }
        acceptVerifiedSession(cookieHeader = cookieHeader, email = email.trim(), successMessage = "Регистрация завершена")
    }

    suspend fun logout(): AppResult<Unit> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        val cookieHeader = session.cookieHeader

        val message = if (cookieHeader.isNotBlank()) {
            try {
                httpClient.newCall(
                    Request.Builder()
                        .url(buildUrl("auth", "logout"))
                        .get()
                        .withCookieHeader(cookieHeader)
                        .build()
                ).execute().use { response ->
                    if (response.code in 200..399) {
                        "Вы вышли из аккаунта"
                    } else {
                        "Локальная сессия очищена"
                    }
                }
            } catch (_: IOException) {
                "Локальная сессия очищена"
            }
        } else {
            "Локальная сессия очищена"
        }

        settingsStore.clearServerSession()
        AppResult.Success(Unit, message)
    }

    private suspend fun acceptVerifiedSession(
        cookieHeader: String,
        email: String,
        successMessage: String
    ): AppResult<Unit> {
        val verificationHtml = try {
            httpClient.newCall(
                Request.Builder()
                    .url(buildUrl("home"))
                    .get()
                    .withCookieHeader(cookieHeader)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    return AppResult.Error("Не удалось подтвердить сессию: ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (error: IOException) {
            return AppResult.Error("Не удалось проверить созданную сессию", error)
        }

        if (loginFormRegex.containsMatchIn(verificationHtml)) {
            return AppResult.Error("Сессия не создана или ещё не завершена")
        }

        val currentSession = settingsStore.currentAuthSession()
        val savedEmail = email.ifBlank { currentSession.email }
        settingsStore.saveServerSession(savedEmail, cookieHeader)
        return AppResult.Success(Unit, successMessage)
    }

    private fun buildUrl(vararg segments: String): HttpUrl {
        val base = BuildConfig.SERVER_BASE_URL.toHttpUrl()
        return base.newBuilder().apply {
            segments.forEach(::addPathSegment)
        }.build()
    }

    private fun buildOrigin(): String {
        val base = BuildConfig.SERVER_BASE_URL.toHttpUrl()
        val portPart = when {
            base.port == 80 && base.scheme == "http" -> ""
            base.port == 443 && base.scheme == "https" -> ""
            else -> ":${base.port}"
        }
        return "${base.scheme}://${base.host}$portPart"
    }

    private fun extractCsrfToken(html: String): String? {
        return csrfInputRegex.find(html)?.groupValues?.getOrNull(1)
            ?: csrfMetaRegex.find(html)?.groupValues?.getOrNull(1)
    }
}

private class MemoryCookieJar : CookieJar {
    private val cookies = linkedMapOf<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            this.cookies[cookie.name] = cookie
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookies.values.filter { it.matches(url) }
    }

    fun toCookieHeader(): String {
        return cookies.values.joinToString(separator = "; ") { cookie -> "${cookie.name}=${cookie.value}" }
    }
}

private fun Headers.toCookieHeader(): String {
    val cookies = linkedMapOf<String, String>()
    values("Set-Cookie").forEach { setCookie ->
        val pair = setCookie.substringBefore(';')
        val separatorIndex = pair.indexOf('=')
        if (separatorIndex > 0) {
            val name = pair.substring(0, separatorIndex).trim()
            val value = pair.substring(separatorIndex + 1).trim()
            if (name.isNotBlank()) {
                cookies[name] = value
            }
        }
    }
    return cookies.entries.joinToString(separator = "; ") { (name, value) -> "$name=$value" }
}

private fun Request.Builder.withCookieHeader(cookieHeader: String): Request.Builder {
    if (cookieHeader.isNotBlank()) {
        header("Cookie", cookieHeader)
    }
    return this
}
