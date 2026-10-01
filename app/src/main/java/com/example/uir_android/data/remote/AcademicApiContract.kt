package com.example.uir_android.data.remote

import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult

internal inline fun <T> academicPayloadResult(
    apiVersion: Int,
    success: Boolean,
    message: String?,
    payload: T?,
    payloadName: String,
    validate: (T) -> String?
): AppResult<T> {
    if (apiVersion != ACADEMIC_API_VERSION) return unsupportedAcademicApi(apiVersion)
    if (!success) {
        return AppResult.Error(
            message ?: "Сервер отклонил запрос: $payloadName",
            type = AppErrorType.DATA
        )
    }
    if (payload == null) {
        return invalidAcademicPayload("В ответе отсутствует обязательное поле «$payloadName»")
    }
    val validationError = validate(payload)
    return if (validationError == null) {
        AppResult.Success(payload)
    } else {
        invalidAcademicPayload(validationError)
    }
}

internal fun validateAcademicGroups(groups: List<ServerAcademicGroupDto>): String? {
    if (groups.any { it.id <= 0 || it.name.isBlank() }) {
        return "Сервер вернул группу без обязательного идентификатора или названия"
    }
    if (groups.map { it.id }.hasDuplicates()) {
        return "Сервер вернул повторяющиеся идентификаторы групп"
    }
    return null
}

internal fun validateStudents(
    students: List<ServerStudentDto>,
    expectedGroupId: Int
): String? {
    if (students.any {
            it.id <= 0 || it.group != expectedGroupId ||
                (it.firstName.isBlank() && it.lastName.isBlank())
        }
    ) {
        return "Сервер вернул студента без обязательных данных или из другой группы"
    }
    if (students.map { it.id }.hasDuplicates()) {
        return "Сервер вернул повторяющиеся идентификаторы студентов"
    }
    return null
}

internal fun validateProfile(profile: ServerProfileDto): String? {
    if (profile.user.id <= 0 || profile.user.email.isBlank() || profile.user.role.isBlank()) {
        return "Сервер вернул профиль без обязательных данных пользователя"
    }
    if (profile.results.any { it.testName.isBlank() || it.completedAt.isBlank() }) {
        return "Сервер вернул неполный результат теста в профиле"
    }
    if (profile.sections.any { section ->
            section.number <= 0 ||
                section.lectures.any { it.number <= 0 } ||
                section.seminars.any { it.number <= 0 } ||
                section.scores.any { it.label.isBlank() }
        }
    ) {
        return "Сервер вернул повреждённые данные разделов профиля"
    }
    if (profile.sections.map { it.number }.hasDuplicates()) {
        return "Сервер вернул повторяющиеся разделы профиля"
    }
    return null
}

internal fun validateLectureLimits(limits: List<ServerLectureLimitDto>): String? {
    if (limits.any {
            it.lectureId <= 0 || it.groupId <= 0 || it.currentCount < 0 ||
                (it.limit != null && it.limit < 0)
        }
    ) {
        return "Сервер вернул некорректную ячейку матрицы лекций"
    }
    if (limits.map { it.lectureId to it.groupId }.hasDuplicates()) {
        return "Сервер вернул повторяющиеся ячейки матрицы лекций"
    }
    return null
}

internal fun validateLectureStatement(statement: ServerLectureStatementDto): String? {
    if (statement.groupId <= 0) return "В лекционной ведомости отсутствует идентификатор группы"
    if (statement.rows.map { it.student.id }.hasDuplicates()) {
        return "Лекционная ведомость содержит повторяющихся студентов"
    }
    statement.rows.forEach { row ->
        val studentError = validateStatementStudent(row.student, statement.groupId)
        if (studentError != null) return studentError
        if (row.passes.any { pass ->
                val hasIdentity = (pass.lectureId ?: 0) > 0 || (pass.lecturePlanId ?: 0) > 0
                !hasIdentity || pass.title.isBlank() || (pass.limit != null && pass.limit < 0)
            }
        ) {
            return "Лекционная ведомость содержит ячейку без обязательных данных"
        }
        val cellIds = row.passes.map { it.lectureId?.let { id -> "lecture:$id" } ?: "plan:${it.lecturePlanId}" }
        if (cellIds.hasDuplicates()) return "Лекционная ведомость содержит повторяющиеся ячейки"
    }
    return null
}

internal fun validateSeminarStatement(statement: ServerSeminarStatementDto): String? {
    if (statement.groupId <= 0 || statement.coursePlanId == null || statement.coursePlanId <= 0) {
        return "В семинарской ведомости отсутствуют данные группы или учебного плана"
    }
    if (statement.rows.map { it.student.id }.hasDuplicates()) {
        return "Семинарская ведомость содержит повторяющихся студентов"
    }
    statement.rows.forEach { row ->
        val studentError = validateStatementStudent(row.student, statement.groupId)
        if (studentError != null) return studentError
        if (row.passes.any {
                it.seminarPassId <= 0 || it.seminarPlanId <= 0 || it.sectionNumber <= 0 ||
                    !it.workPoints.isFinite() || it.title.isBlank()
            }
        ) {
            return "Семинарская ведомость содержит ячейку без обязательных данных"
        }
        if (row.passes.map { it.seminarPassId }.hasDuplicates()) {
            return "Семинарская ведомость содержит повторяющиеся ячейки"
        }
    }
    return null
}

internal fun validateNews(news: List<ServerNewsDto>): String? {
    if (news.any { item ->
            item.id.isBlank() || item.title.isBlank() ||
                item.attachments.any { it.title.isBlank() || it.url.isBlank() }
        }
    ) {
        return "Сервер вернул новость или прикрепление без обязательных данных"
    }
    if (news.map { it.id }.hasDuplicates()) {
        return "Сервер вернул повторяющиеся идентификаторы новостей"
    }
    return null
}

private fun validateStatementStudent(student: ServerStudentDto, expectedGroupId: Int): String? =
    if (student.id <= 0 || student.group != expectedGroupId ||
        (student.firstName.isBlank() && student.lastName.isBlank())
    ) {
        "Ведомость содержит студента без обязательных данных или из другой группы"
    } else {
        null
    }

private fun invalidAcademicPayload(message: String): AppResult.Error = AppResult.Error(
    message = message,
    type = AppErrorType.DATA
)

private fun <T> List<T>.hasDuplicates(): Boolean = size != distinct().size
