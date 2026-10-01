package com.example.uir_android.domain.usecase

import javax.inject.Inject

data class SeminarPointsValidation(
    val isValid: Boolean,
    val value: Double? = null,
    val message: String = ""
)

class ValidateSeminarPointsUseCase @Inject constructor() {
    operator fun invoke(input: String): SeminarPointsValidation {
        val value = input.trim().replace(',', '.').toDoubleOrNull()
            ?: return SeminarPointsValidation(false, message = "Введите число")
        return when {
            !value.isFinite() -> SeminarPointsValidation(false, message = "Введите конечное число")
            value < MIN_POINTS -> SeminarPointsValidation(
                false,
                message = "Минимальное значение: ${MIN_POINTS.toInt()}"
            )
            value > MAX_POINTS -> SeminarPointsValidation(
                false,
                message = "Максимальное значение: ${MAX_POINTS.toInt()}"
            )
            else -> SeminarPointsValidation(true, value = value)
        }
    }

    companion object {
        const val MIN_POINTS = -50.0
        const val MAX_POINTS = 100.0
    }
}
