package com.example.uir_android.data.remote

import com.example.uir_android.BuildConfig
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.data.local.AppSettingsStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class ServerWebClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val settingsStore: AppSettingsStore,
    private val errorMapper: HttpErrorMapper,
    private val dispatchers: AppDispatchers
) {
    suspend fun <T> authenticatedRequest(
        builder: Request.Builder,
        decode: suspend (code: Int, body: String) -> AppResult<T>
    ): AppResult<T> = authenticatedRequestDetailed(builder, decodeHttpError = null) { code, _, _, body ->
        decode(code, body)
    }

    suspend fun <T> authenticatedRequestWithHttpErrorPayload(
        builder: Request.Builder,
        decodeHttpError: suspend (code: Int, body: String) -> AppResult<T>?,
        decode: suspend (code: Int, body: String) -> AppResult<T>
    ): AppResult<T> = authenticatedRequestDetailed(
        builder = builder,
        decodeHttpError = { code, _, _, body -> decodeHttpError(code, body) }
    ) { code, _, _, body ->
        decode(code, body)
    }

    private suspend fun <T> authenticatedRequestDetailed(
        builder: Request.Builder,
        decodeHttpError: (suspend (
            code: Int,
            finalPath: String,
            contentType: String?,
            body: String
        ) -> AppResult<T>?)?,
        decode: suspend (
            code: Int,
            finalPath: String,
            contentType: String?,
            body: String
        ) -> AppResult<T>
    ): AppResult<T> {
        val session = settingsStore.currentAuthSession()
        if (session.cookieHeader.isBlank()) {
            return errorMapper.missingSession()
        }
        return execute(
            request = builder.header("Cookie", session.cookieHeader).build(),
            decodeHttpError = decodeHttpError,
            decode = decode
        )
    }

    suspend fun <T> execute(
        request: Request,
        decodeHttpError: (suspend (
            code: Int,
            finalPath: String,
            contentType: String?,
            body: String
        ) -> AppResult<T>?)? = null,
        decode: suspend (
            code: Int,
            finalPath: String,
            contentType: String?,
            body: String
        ) -> AppResult<T>
    ): AppResult<T> = withContext(dispatchers.io) {
        try {
            httpClient.newCall(request).awaitResponse().use { response ->
                val body = response.body?.string().orEmpty()
                errorMapper.accessError(
                    statusCode = response.code,
                    finalPath = response.request.url.encodedPath,
                    body = body
                )?.let { return@use it }
                if (!response.isSuccessful) {
                    val decodedError = try {
                        decodeHttpError?.invoke(
                            response.code,
                            response.request.url.encodedPath,
                            response.header("Content-Type"),
                            body
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        null
                    }
                    if (decodedError != null) return@use decodedError
                    return@use errorMapper.http(
                        statusCode = response.code,
                        message = serverMessage(body) ?: "Ошибка сервера: ${response.code}"
                    )
                }
                try {
                    decode(
                        response.code,
                        response.request.url.encodedPath,
                        response.header("Content-Type"),
                        body
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMapper.invalidPayload(
                        message = "Некорректный ответ сервера: ${response.code}",
                        cause = error
                    )
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            errorMapper.network(error)
        }
    }

    fun buildUrl(vararg segments: String, query: Map<String, String> = emptyMap()): HttpUrl =
        BuildConfig.SERVER_BASE_URL.toHttpUrl().newBuilder().apply {
            segments.filter(String::isNotBlank).forEach(::addPathSegment)
            query.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()

    private fun serverMessage(body: String): String? {
        val trimmed = body.trim()
        if (!trimmed.startsWith("{")) return null
        return runCatching {
            serverJson.decodeFromString<AcademicBasicEnvelope>(trimmed).let {
                it.message ?: it.error.firstOrNull()
            }
        }.getOrNull()
    }
}

internal val serverJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    coerceInputValues = true
}
