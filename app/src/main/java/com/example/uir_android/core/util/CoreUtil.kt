package com.example.uir_android.core.util

import kotlinx.coroutines.CoroutineDispatcher
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val BLANK_SYMBOL: Char = '_'
const val DEFAULT_TRACE_LIMIT: Int = 25
const val DEFAULT_TAPE_RADIUS: Int = 10
const val BLANK_INPUT_TOKEN: String = "\\l"
const val OMEGA_INPUT_TOKEN: String = "\\o"
const val PARTIAL_INPUT_TOKEN: String = "\\d"
const val OMEGA_SYMBOL: Char = 'Ω'
const val PARTIAL_SYMBOL: Char = '∂'

sealed interface AppResult<out T> {
    data object Loading : AppResult<Nothing>

    data class Success<T>(
        val data: T,
        val message: String? = null
    ) : AppResult<T>

    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : AppResult<Nothing>
}

data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher
)

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

fun formatTimestamp(timestamp: Long): String = runCatching {
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormatter)
}.getOrElse { timestamp.toString() }

fun sparseTapeFromInput(input: String): Map<Int, Char> = buildMap {
    input.forEachIndexed { index, symbol ->
        if (symbol != BLANK_SYMBOL) {
            put(index, symbol)
        }
    }
}

fun parseIntOrNull(value: String): Int? = value.trim().toIntOrNull()

fun parseLongOrNull(value: String): Long? = value.trim().toLongOrNull()

fun formatDisplayName(email: String): String {
    val localPart = email.substringBefore('@').trim()
    if (localPart.isBlank()) {
        return "Тимур"
    }

    val lowered = localPart.lowercase(Locale.getDefault())
    when {
        lowered.startsWith("timur") || lowered.startsWith("tima") || lowered.startsWith("tim") -> return "Тимур"
    }

    val rawName = localPart
        .split('.', '_', '-')
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

    if (rawName.isBlank()) {
        return "Тимур"
    }

    return rawName.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    }
}

fun normalizeSymbolSequenceForDisplay(value: String): String {
    return value
        .filterNot(Char::isWhitespace)
        .replace(BLANK_INPUT_TOKEN, "λ")
        .replace(OMEGA_INPUT_TOKEN, "Ω")
        .replace(PARTIAL_INPUT_TOKEN, "∂")
}

fun normalizeSingleSymbolForDisplay(value: String): String {
    return normalizeSymbolSequenceForDisplay(value).take(1)
}

fun formatSymbolForUi(symbol: Char): String = when (symbol) {
    BLANK_SYMBOL -> "λ"
    else -> symbol.toString()
}

fun encodeSymbolToken(symbol: Char): String = when (symbol) {
    BLANK_SYMBOL -> BLANK_INPUT_TOKEN
    OMEGA_SYMBOL -> OMEGA_INPUT_TOKEN
    PARTIAL_SYMBOL -> PARTIAL_INPUT_TOKEN
    else -> symbol.toString()
}

fun parseSingleSymbolToken(token: String): AppResult<Char> {
    return when (val result = parseCompactSymbolSequence(token)) {
        is AppResult.Error -> result
        AppResult.Loading -> AppResult.Error("Не удалось прочитать символ")
        is AppResult.Success -> {
            when (result.data.size) {
                1 -> AppResult.Success(result.data.first())
                0 -> AppResult.Error("Символ не задан")
                else -> AppResult.Error("Поле должно содержать ровно один символ")
            }
        }
    }
}

fun parseCompactSymbolSequence(input: String): AppResult<List<Char>> {
    val value = input.trim()
    if (value.isEmpty()) {
        return AppResult.Success(emptyList())
    }

    val result = mutableListOf<Char>()
    var index = 0

    while (index < value.length) {
        val current = value[index]
        when {
            current.isWhitespace() -> return AppResult.Error("Символы вводятся без пробелов")
            current == '_' -> return AppResult.Error("Используйте $BLANK_INPUT_TOKEN вместо _")
            current == '\\' -> {
                if (index == value.lastIndex) {
                    return AppResult.Error("Незавершённая escape-последовательность")
                }
                when (value[index + 1].lowercaseChar()) {
                    'l' -> result += BLANK_SYMBOL
                    'o' -> result += OMEGA_SYMBOL
                    'd' -> result += PARTIAL_SYMBOL
                    else -> return AppResult.Error("Неизвестная escape-последовательность \\${value[index + 1]}")
                }
                index += 2
            }
            current == 'λ' -> {
                result += BLANK_SYMBOL
                index += 1
            }
            current == 'Ω' -> {
                result += OMEGA_SYMBOL
                index += 1
            }
            current == '∂' -> {
                result += PARTIAL_SYMBOL
                index += 1
            }
            else -> {
                result += current
                index += 1
            }
        }
    }

    return AppResult.Success(result)
}
