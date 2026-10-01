package com.example.uir_android.data.remote

import com.example.uir_android.BuildConfig
import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val mobileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

@Serializable
data class ServerGroup(
    val id: Int = 0,
    val name: String = ""
)

@Singleton
class ServerAuthService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsStore: AppSettingsStore,
    private val errorMapper: HttpErrorMapper,
    private val dispatchers: AppDispatchers
) {
    suspend fun login(email: String, password: String): AppResult<Unit> = withContext(dispatchers.io) {
        val safeEmail = email.trim()
        if (safeEmail.isBlank() || password.isEmpty()) {
            return@withContext errorMapper.api("Введите email и пароль")
        }

        val body = mobileJson.encodeToString(
            MobileLoginRequest(email = safeEmail, password = password)
        ).toRequestBody(jsonMediaType)

        val response = when (val result = postJson("api", "mobile", "login", body = body)) {
            is AppResult.Error -> return@withContext result
            is AppResult.Success -> result.data
        }

        val envelope = response.envelope
        if (!envelope.success) {
            return@withContext errorMapper.api(
                envelope.message ?: "Ошибка авторизации: ${response.code}"
            )
        }

        if (response.cookieHeader.isBlank()) {
            return@withContext errorMapper.invalidPayload("Сервер не вернул cookie сессии")
        }

        settingsStore.saveServerSession(
            email = envelope.user?.email?.ifBlank { safeEmail } ?: safeEmail,
            cookieHeader = response.cookieHeader
        )
        AppResult.Success(Unit, envelope.message ?: "Вход выполнен")
    }

    suspend fun register(
        firstName: String,
        lastName: String,
        groupId: Int?,
        email: String,
        password: String,
        passwordConfirmation: String,
        captchaTicket: String
    ): AppResult<Unit> = withContext(dispatchers.io) {
        val safeFirstName = firstName.trim()
        val safeLastName = lastName.trim()
        val safeEmail = email.trim()
        if (safeFirstName.isBlank() || safeLastName.isBlank() || safeEmail.isBlank()) {
            return@withContext errorMapper.api("Заполните имя, фамилию и email")
        }
        if (password.length < 6) {
            return@withContext errorMapper.api("Пароль должен быть не короче 6 символов")
        }
        if (password != passwordConfirmation) {
            return@withContext errorMapper.api("Пароли не совпадают")
        }

        if (captchaTicket.isBlank()) {
            return@withContext errorMapper.api(
                "\u0421\u043d\u0430\u0447\u0430\u043b\u0430 \u043f\u0440\u043e\u0439\u0434\u0438\u0442\u0435 CAPTCHA"
            )
        }

        val body = mobileJson.encodeToString(
            MobileRegisterRequest(
                firstName = safeFirstName,
                lastName = safeLastName,
                email = safeEmail,
                group = groupId,
                password = password,
                passwordConfirmation = passwordConfirmation,
                captchaTicket = captchaTicket
            )
        ).toRequestBody(jsonMediaType)

        val response = when (val result = postJson("api", "mobile", "register", body = body)) {
            is AppResult.Error -> return@withContext result
            is AppResult.Success -> result.data
        }

        val envelope = response.envelope
        if (!envelope.success) {
            return@withContext errorMapper.api(
                envelope.message ?: "Ошибка регистрации: ${response.code}"
            )
        }

        if (response.cookieHeader.isBlank()) {
            return@withContext errorMapper.invalidPayload(
                "Регистрация выполнена, но сервер не вернул cookie сессии"
            )
        }

        settingsStore.saveServerSession(
            email = envelope.user?.email?.ifBlank { safeEmail } ?: safeEmail,
            cookieHeader = response.cookieHeader
        )
        AppResult.Success(Unit, envelope.message ?: "Регистрация выполнена")
    }

    suspend fun loadGroups(): AppResult<List<ServerGroup>> = withContext(dispatchers.io) {
        val response = when (val result = getJson("api", "mobile", "groups", cookieHeader = "")) {
            is AppResult.Error -> return@withContext result
            is AppResult.Success -> result.data
        }

        val envelope = response.envelope
        if (!envelope.success) {
            return@withContext errorMapper.api(
                envelope.message
                    ?: "Ошибка загрузки групп: ${response.code}"
            )
        }

        AppResult.Success(envelope.groups)
    }

    suspend fun logout(): AppResult<Unit> = withContext(dispatchers.io) {
        val session = settingsStore.currentAuthSession()
        val cookieHeader = session.cookieHeader
        settingsStore.clearServerSession()

        if (cookieHeader.isNotBlank()) {
            try {
                val logoutClient = httpClient.newBuilder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .writeTimeout(2, TimeUnit.SECONDS)
                    .build()
                logoutClient.newCall(
                    Request.Builder()
                        .url(buildUrl("api", "mobile", "logout"))
                        .post("{}".toRequestBody(jsonMediaType))
                        .withCookieHeader(cookieHeader)
                        .build()
                ).awaitResponse().close()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Local session is already cleared; remote logout is best effort.
            }
        }

        AppResult.Success(Unit, "Вы вышли из аккаунта")
    }

    private suspend fun postJson(
        vararg segments: String,
        body: okhttp3.RequestBody
    ): AppResult<MobileHttpResponse> = executeJson(
        Request.Builder()
            .url(buildUrl(*segments))
            .post(body)
            .header("Accept", "application/json")
            .build()
    )

    private suspend fun getJson(
        vararg segments: String,
        cookieHeader: String
    ): AppResult<MobileHttpResponse> = executeJson(
        Request.Builder()
            .url(buildUrl(*segments))
            .get()
            .header("Accept", "application/json")
            .withCookieHeader(cookieHeader)
            .build()
    )

    private suspend fun executeJson(request: Request): AppResult<MobileHttpResponse> = try {
        httpClient.newCall(request).awaitResponse().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val decoded = errorMapper.decodePayload(
                body = responseBody,
                statusCode = response.code,
                context = "JSON авторизации"
            ) { mobileJson.decodeFromString<MobileApiEnvelope>(responseBody) }
            if (!response.isSuccessful) {
                val message = (decoded as? AppResult.Success)?.data?.message
                return@use errorMapper.http(response.code, message)
            }
            when (decoded) {
                is AppResult.Error -> decoded
                is AppResult.Success -> AppResult.Success(
                    MobileHttpResponse(
                        code = response.code,
                        cookieHeader = response.headers.toCookieHeader(),
                        envelope = decoded.data
                    )
                )
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        errorMapper.network(error)
    }

    private fun buildUrl(vararg segments: String): HttpUrl {
        val base = BuildConfig.SERVER_BASE_URL.toHttpUrl()
        return base.newBuilder().apply {
            segments.forEach(::addPathSegment)
        }.build()
    }
}

private data class MobileHttpResponse(
    val code: Int,
    val cookieHeader: String,
    val envelope: MobileApiEnvelope
)

@Serializable
private data class MobileLoginRequest(
    val email: String,
    val password: String
)

@Serializable
private data class MobileRegisterRequest(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val email: String,
    val group: Int?,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
    @SerialName("captcha_ticket") val captchaTicket: String
)

@Serializable
private data class MobileApiEnvelope(
    val success: Boolean = false,
    val message: String? = null,
    val user: MobileUserDto? = null,
    val groups: List<ServerGroup> = emptyList()
)

@Serializable
private data class MobileUserDto(
    val id: Int = 0,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val group: Int = 0,
    val role: String = ""
)

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
