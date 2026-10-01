package com.example.uir_android.ui.util

fun formatRemainingDuration(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0L) {
        "$hours ч ${minutes.toString().padStart(2, '0')} мин ${seconds.toString().padStart(2, '0')} сек"
    } else {
        "$minutes мин ${seconds.toString().padStart(2, '0')} сек"
    }
}
