package com.example.uir_android.domain.usecase

import javax.inject.Inject

class ExtractNewsLinksUseCase @Inject constructor() {
    operator fun invoke(text: String): List<String> = URL_REGEX
        .findAll(text)
        .map { match -> match.value.trimEnd('.', ',', ';', ':', ')', ']') }
        .distinct()
        .toList()

    private companion object {
        val URL_REGEX = Regex("""https?://[^\s<>]+""", RegexOption.IGNORE_CASE)
    }
}
