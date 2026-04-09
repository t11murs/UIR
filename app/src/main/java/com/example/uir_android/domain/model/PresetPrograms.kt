package com.example.uir_android.domain.model

fun defaultTmPrograms(now: Long = System.currentTimeMillis()): List<TmProgram> = listOf(
    TmProgram(
        name = "Инверсия слова",
        description = "Заменяет a на b, b на a и завершает работу на пустом символе.",
        sourceText = """
            S0 \\d -> \\d R S0
            S0 a -> b R S0
            S0 b -> a R S0
            S0 \\l -> \\l S HALT
        """.trimIndent(),
        createdAt = now,
        updatedAt = now
    )
)
