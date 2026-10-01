package com.example.uir_android.domain.attendance

import com.example.uir_android.domain.model.LectureAttendanceUpdate
import com.example.uir_android.domain.model.LectureLimitUpdate

data class LectureLimitDraft(
    val lectureId: Int,
    val groupId: Int,
    val value: String
)

data class LectureLimitUpdatesResult(
    val updates: List<LectureLimitUpdate>,
    val invalidDrafts: List<LectureLimitDraft>
) {
    val isValid: Boolean get() = invalidDrafts.isEmpty()
}

fun buildLectureLimitUpdates(drafts: List<LectureLimitDraft>): LectureLimitUpdatesResult {
    val invalid = mutableListOf<LectureLimitDraft>()
    val updates = drafts.mapNotNull { draft ->
        val limit = draft.value.toIntOrNull()
        if (limit == null || limit < 0) {
            invalid += draft
            null
        } else {
            LectureLimitUpdate(draft.lectureId, draft.groupId, limit)
        }
    }
    return LectureLimitUpdatesResult(
        updates = updates.takeIf { invalid.isEmpty() }.orEmpty(),
        invalidDrafts = invalid
    )
}

fun buildLectureAttendanceUpdates(
    lectureId: Int,
    currentSelections: Map<Int, Boolean>,
    initiallySelectedStudentIds: Set<Int>
): List<LectureAttendanceUpdate> = currentSelections.mapNotNull { (studentId, selected) ->
    val wasSelected = studentId in initiallySelectedStudentIds
    if (selected == wasSelected) {
        null
    } else {
        LectureAttendanceUpdate(
            studentId = studentId,
            lectureId = lectureId,
            present = selected
        )
    }
}
