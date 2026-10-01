package com.example.uir_android.data.repository.impl

import com.example.uir_android.data.db.ActiveTestDraftDao
import com.example.uir_android.data.db.ActiveTestDraftEntity
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.domain.model.ActiveTestDraft
import com.example.uir_android.domain.repository.TestDraftRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.withContext

@Serializable
private data class DraftAnswer(
    val questionId: Int,
    val values: List<String>
)

@Singleton
class RoomTestDraftRepository @Inject constructor(
    private val dao: ActiveTestDraftDao,
    private val json: Json,
    private val dispatchers: AppDispatchers
) : TestDraftRepository {
    override suspend fun get(ownerKey: String, testId: Int): ActiveTestDraft? =
        withContext(dispatchers.io) {
            dao.get(ownerKey.normalizedOwnerKey(), testId)?.toDomain(json)
        }

    override suspend fun getActiveTestIds(ownerKey: String): Set<Int> = withContext(dispatchers.io) {
        val normalizedOwner = ownerKey.normalizedOwnerKey()
        dao.getAll(normalizedOwner).mapTo(mutableSetOf()) { it.testId }
    }

    override suspend fun save(draft: ActiveTestDraft) = withContext(dispatchers.io) {
        dao.upsert(draft.toEntity(json))
    }

    override suspend fun delete(ownerKey: String, testId: Int) = withContext(dispatchers.io) {
        dao.delete(ownerKey.normalizedOwnerKey(), testId)
    }

    override suspend fun deleteAllForOwner(ownerKey: String) = withContext(dispatchers.io) {
        dao.deleteAllForOwner(ownerKey.normalizedOwnerKey())
    }
}

private fun ActiveTestDraftEntity.toDomain(json: Json): ActiveTestDraft? = runCatching {
    val entries = json.decodeFromString<List<DraftAnswer>>(answersJson)
    ActiveTestDraft(
        ownerKey = ownerKey,
        testId = testId,
        runId = runId,
        endsAt = endsAt,
        currentQuestionIndex = currentQuestionIndex,
        answers = entries.associate { it.questionId to it.values },
        markedQuestionIds = json.decodeFromString<List<Int>>(markedQuestionIdsJson).toSet(),
        updatedAt = updatedAt
    )
}.getOrNull()

private fun ActiveTestDraft.toEntity(json: Json): ActiveTestDraftEntity {
    val entries = answers.entries
        .sortedBy(Map.Entry<Int, List<String>>::key)
        .map { DraftAnswer(it.key, it.value) }
    return ActiveTestDraftEntity(
        ownerKey = ownerKey.normalizedOwnerKey(),
        testId = testId,
        runId = runId,
        endsAt = endsAt,
        currentQuestionIndex = currentQuestionIndex,
        answersJson = json.encodeToString(entries),
        markedQuestionIdsJson = json.encodeToString(markedQuestionIds.sorted()),
        updatedAt = updatedAt
    )
}

private fun String.normalizedOwnerKey(): String = trim().lowercase()
