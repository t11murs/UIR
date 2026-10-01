package com.example.uir_android.domain.usecase

import javax.inject.Inject

data class LectureAttendanceValidation(
    val isValid: Boolean,
    val message: String
)

class ValidateLectureAttendanceSelectionUseCase @Inject constructor() {
    operator fun invoke(selectedCount: Int, requiredCount: Int?): LectureAttendanceValidation {
        return when {
            requiredCount == null -> LectureAttendanceValidation(
                isValid = false,
                message = "Преподаватель ещё не установил количество присутствующих"
            )
            requiredCount <= 0 -> LectureAttendanceValidation(
                isValid = false,
                message = "Отметка посещаемости для этой лекции недоступна"
            )
            selectedCount < requiredCount -> LectureAttendanceValidation(
                isValid = false,
                message = "Выберите ещё ${requiredCount - selectedCount}"
            )
            selectedCount > requiredCount -> LectureAttendanceValidation(
                isValid = false,
                message = "Выбрано больше установленного количества"
            )
            else -> LectureAttendanceValidation(
                isValid = true,
                message = "Количество выбрано верно"
            )
        }
    }
}
