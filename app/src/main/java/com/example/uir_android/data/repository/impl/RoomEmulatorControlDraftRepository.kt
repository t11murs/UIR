package com.example.uir_android.data.repository.impl

import com.example.uir_android.data.db.EmulatorControlDraftDao
import com.example.uir_android.data.db.EmulatorControlDraftEntity
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.domain.model.EmulatorCommandDraft
import com.example.uir_android.domain.model.EmulatorControlDraft
import com.example.uir_android.domain.model.EmulatorQuestionDraft
import com.example.uir_android.domain.repository.EmulatorControlDraftRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext

@Serializable
private data class StoredCommand(
    val leftRuleText: String,
    val rightRuleText: String
)

@Serializable
private data class StoredQuestion(
    val questionId: Int,
    val alphabetText: String,
    val inputTape: String,
    val commands: List<StoredCommand>
)

@Singleton
class RoomEmulatorControlDraftRepository @Inject constructor(
    private val dao: EmulatorControlDraftDao,
    private val json: Json,
    private val dispatchers: AppDispatchers
) : EmulatorControlDraftRepository {
    override suspend fun get(ownerKey: String, controlId: Int): EmulatorControlDraft? =
        withContext(dispatchers.io) {
            dao.get(ownerKey.normalizedDraftOwner(), controlId)?.toDomain(json)
        }

    override suspend fun getActiveControlIds(ownerKey: String): Set<Int> =
        withContext(dispatchers.io) {
            dao.getAll(ownerKey.normalizedDraftOwner()).mapTo(mutableSetOf()) { it.controlId }
        }

    override suspend fun save(draft: EmulatorControlDraft) = withContext(dispatchers.io) {
        dao.upsert(draft.toEntity(json))
    }

    override suspend fun delete(ownerKey: String, controlId: Int) = withContext(dispatchers.io) {
        dao.delete(ownerKey.normalizedDraftOwner(), controlId)
    }
}

private fun EmulatorControlDraftEntity.toDomain(json: Json): EmulatorControlDraft? = runCatching {
    val stored = json.decodeFromString<List<StoredQuestion>>(questionsJson)
    EmulatorControlDraft(
        ownerKey = ownerKey,
        controlId = controlId,
        runId = runId,
        endsAt = endsAt,
        currentQuestionIndex = currentQuestionIndex,
        questions = stored.associate { question ->
            question.questionId to EmulatorQuestionDraft(
                alphabetText = question.alphabetText,
                inputTape = question.inputTape,
                commands = question.commands.map { EmulatorCommandDraft(it.leftRuleText, it.rightRuleText) }
            )
        },
        updatedAt = updatedAt
    )
}.getOrNull()

private fun EmulatorControlDraft.toEntity(json: Json): EmulatorControlDraftEntity {
    val stored = questions.entries.sortedBy { it.key }.map { (questionId, question) ->
        StoredQuestion(
            questionId = questionId,
            alphabetText = question.alphabetText,
            inputTape = question.inputTape,
            commands = question.commands.map { StoredCommand(it.leftRuleText, it.rightRuleText) }
        )
    }
    return EmulatorControlDraftEntity(
        ownerKey = ownerKey.normalizedDraftOwner(),
        controlId = controlId,
        runId = runId,
        endsAt = endsAt,
        currentQuestionIndex = currentQuestionIndex,
        questionsJson = json.encodeToString(stored),
        updatedAt = updatedAt
    )
}

private fun String.normalizedDraftOwner(): String = trim().lowercase()
