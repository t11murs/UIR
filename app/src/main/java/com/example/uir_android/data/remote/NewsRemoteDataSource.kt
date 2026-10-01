package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import okhttp3.Request

@Singleton
class NewsRemoteDataSource @Inject constructor(
    private val webClient: ServerWebClient
) {
    suspend fun loadNews(): AppResult<List<ServerNewsDto>> =
        webClient.authenticatedRequest(
            Request.Builder()
                .url(webClient.buildUrl("api", "mobile", "v1", "news"))
                .get()
                .header("Accept", "application/json")
        ) { code, body ->
            val envelope = serverJson.decodeFromString<NewsV1Envelope>(body)
            academicPayloadResult(
                apiVersion = envelope.apiVersion,
                success = envelope.success,
                message = envelope.message ?: "Не удалось загрузить новости: $code",
                payload = envelope.news,
                payloadName = "news",
                validate = ::validateNews
            )
        }
}
