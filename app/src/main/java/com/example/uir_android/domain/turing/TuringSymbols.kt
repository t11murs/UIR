package com.example.uir_android.domain.turing

import com.example.uir_android.core.common.AppResult

const val BLANK_SYMBOL: Char = '_'
const val DEFAULT_TRACE_LIMIT: Int = 25
const val DEFAULT_TAPE_RADIUS: Int = 10
const val BLANK_INPUT_TOKEN: String = "\\l"
const val OMEGA_INPUT_TOKEN: String = "\\o"
const val PARTIAL_INPUT_TOKEN: String = "\\d"
const val OMEGA_SYMBOL: Char = 'Ω'
const val PARTIAL_SYMBOL: Char = '∂'

fun sparseTapeFromInput(input: String): Map<Int, Char> = buildMap {
    input.forEachIndexed { index, symbol ->
        if (symbol != BLANK_SYMBOL) put(index, symbol)
    }
}

fun parseSingleSymbolToken(token: String): AppResult<Char> =
    when (val result = parseCompactSymbolSequence(token)) {
        is AppResult.Error -> result
        is AppResult.Success -> when (result.data.size) {
            1 -> AppResult.Success(result.data.first())
            0 -> AppResult.Error("Символ не задан")
            else -> AppResult.Error("Поле должно содержать ровно один символ")
        }
    }

fun parseCompactSymbolSequence(input: String): AppResult<List<Char>> {
    val value = input.trim()
    if (value.isEmpty()) return AppResult.Success(emptyList())

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
            current == OMEGA_SYMBOL -> {
                result += OMEGA_SYMBOL
                index += 1
            }
            current == PARTIAL_SYMBOL -> {
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
