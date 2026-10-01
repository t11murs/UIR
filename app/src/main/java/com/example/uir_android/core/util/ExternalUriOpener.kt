package com.example.uir_android.core.util

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.example.uir_android.core.network.resolveServerUrl

fun openExternalUri(context: Context, rawUri: String): Boolean = runCatching {
    val value = rawUri.trim()
    require(value.isNotBlank())
    val target = if (value.startsWith("mailto:", ignoreCase = true)) {
        value
    } else {
        resolveServerUrl(value)
    }
    context.startActivity(
        Intent(Intent.ACTION_VIEW, target.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}.isSuccess
