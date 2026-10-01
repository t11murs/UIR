package com.example.uir_android.ui.util

import com.example.uir_android.domain.turing.BLANK_INPUT_TOKEN
import com.example.uir_android.domain.turing.BLANK_SYMBOL
import com.example.uir_android.domain.turing.OMEGA_INPUT_TOKEN
import com.example.uir_android.domain.turing.OMEGA_SYMBOL
import com.example.uir_android.domain.turing.PARTIAL_INPUT_TOKEN
import com.example.uir_android.domain.turing.PARTIAL_SYMBOL

fun normalizeSymbolSequenceForDisplay(value: String): String = value
    .filterNot(Char::isWhitespace)
    .replace(BLANK_INPUT_TOKEN, "λ")
    .replace(OMEGA_INPUT_TOKEN, "Ω")
    .replace(PARTIAL_INPUT_TOKEN, "∂")

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
