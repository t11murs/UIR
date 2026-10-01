package com.example.uir_android.core.network

import com.example.uir_android.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun resolveServerUrl(rawUrl: String): String =
    resolveServerUrl(rawUrl, BuildConfig.SERVER_BASE_URL)

internal fun resolveServerUrl(rawUrl: String, serverBaseUrl: String): String {
    val value = rawUrl.trim().replace("&amp;", "&")
    if (value.isBlank()) return value
    if (value.hasNonHttpScheme()) return value

    val base = serverBaseUrl.toHttpUrlOrNull() ?: return value
    val resolved = when {
        value.startsWith("//") -> "${base.scheme}:$value".toHttpUrlOrNull()
        else -> value.toHttpUrlOrNull() ?: base.resolve(value)
    } ?: return value

    return if (resolved.host.equals(base.host, ignoreCase = true) || resolved.isInternalHost()) {
        resolved.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
            .toString()
    } else {
        resolved.toString()
    }
}

private fun String.hasNonHttpScheme(): Boolean {
    val scheme = substringBefore(':', missingDelimiterValue = "")
    return scheme.isNotBlank() &&
        !scheme.equals("http", ignoreCase = true) &&
        !scheme.equals("https", ignoreCase = true)
}

private fun HttpUrl.isInternalHost(): Boolean {
    val normalizedHost = host.lowercase()
    return normalizedHost == "localhost" ||
        normalizedHost == "0.0.0.0" ||
        normalizedHost == "::1" ||
        normalizedHost.startsWith("127.") ||
        normalizedHost == "10.0.2.2" ||
        normalizedHost == "host.docker.internal" ||
        normalizedHost in setOf("app", "nginx", "lms_app", "lms_nginx")
}
