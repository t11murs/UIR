package com.example.uir_android.data.local

import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.data.db.CompletedTestReviewDao
import com.example.uir_android.data.db.CompletedTestReviewEntity
import com.example.uir_android.domain.model.CompletedTestReview
import com.example.uir_android.domain.model.TestDetail
import com.example.uir_android.domain.model.TestQuestion
import com.example.uir_android.domain.model.TestQuestionContentItem
import com.example.uir_android.domain.model.TestQuestionResult
import com.example.uir_android.domain.model.TestResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class CompletedTestReviewStore @Inject constructor(
    private val dao: CompletedTestReviewDao,
    private val json: Json,
    private val dispatchers: AppDispatchers
) {
    suspend fun getLatest(rawOwnerKey: String, testId: Int): CompletedTestReview? =
        withContext(dispatchers.io) {
            val ownerKey = normalizeAccountOwnerKey(rawOwnerKey)
            if (ownerKey.isBlank()) return@withContext null
            dao.getLatest(ownerKey, testId)?.let { entity ->
                runCatching {
                    json.decodeFromString<StoredCompletedTestReview>(entity.reviewJson).toDomain()
                }.getOrNull()?.takeIf { review ->
                    review.test.id == entity.testId &&
                        review.test.runId == entity.runId &&
                        review.result.runId == entity.runId
                }
            }
        }

    suspend fun save(rawOwnerKey: String, review: CompletedTestReview) = withContext(dispatchers.io) {
        val ownerKey = normalizeAccountOwnerKey(rawOwnerKey)
        val runId = review.result.runId
        if (ownerKey.isBlank() || runId <= 0 || review.test.runId != runId) return@withContext
        dao.upsert(
            CompletedTestReviewEntity(
                ownerKey = ownerKey,
                testId = review.test.id,
                runId = runId,
                reviewJson = json.encodeToString(review.toStored()),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearTest(rawOwnerKey: String, testId: Int) = withContext(dispatchers.io) {
        val ownerKey = normalizeAccountOwnerKey(rawOwnerKey)
        if (ownerKey.isNotBlank()) dao.deleteForTest(ownerKey, testId)
    }
}

@Serializable
private data class StoredCompletedTestReview(
    val test: StoredTestDetail,
    val result: StoredTestResult
)

@Serializable
private data class StoredTestDetail(
    val id: Int,
    val name: String,
    val type: String,
    val timeMinutes: Int,
    val totalPoints: Double,
    val runId: Int,
    val endsAt: String,
    val questions: List<StoredTestQuestion>
)

@Serializable
private data class StoredTestQuestion(
    val id: Int,
    val count: Int,
    val typeCode: Int,
    val typeName: String,
    val text: List<String>,
    val imageUrls: List<String>,
    val textParts: List<String>,
    val variants: List<String>,
    val variantGroups: List<List<String>>,
    val supported: Boolean,
    val contentItems: List<StoredQuestionContentItem> = emptyList()
)

@Serializable
private data class StoredQuestionContentItem(
    val index: Int,
    val type: String,
    val text: String = "",
    val imageUrl: String = ""
)

@Serializable
private data class StoredTestResult(
    val runId: Int,
    val score: Double,
    val total: Double,
    val markRu: String,
    val markEu: String,
    val finishedAt: String,
    val details: List<StoredQuestionResult>
)

@Serializable
private data class StoredQuestionResult(
    val questionId: Int,
    val score: Double,
    val points: Double,
    val rightPercent: Int,
    val answers: List<String>,
    val correctAnswers: List<String> = emptyList()
)

private fun CompletedTestReview.toStored() = StoredCompletedTestReview(
    test = StoredTestDetail(
        id = test.id,
        name = test.name,
        type = test.type,
        timeMinutes = test.timeMinutes,
        totalPoints = test.totalPoints,
        runId = test.runId,
        endsAt = test.endsAt,
        questions = test.questions.map { question ->
            StoredTestQuestion(
                question.id,
                question.count,
                question.typeCode,
                question.typeName,
                question.text,
                question.imageUrls,
                question.textParts,
                question.variants,
                question.variantGroups,
                question.supported,
                question.contentItems.map { item ->
                    StoredQuestionContentItem(item.index, item.type, item.text, item.imageUrl)
                }
            )
        }
    ),
    result = StoredTestResult(
        runId = result.runId,
        score = result.score,
        total = result.total,
        markRu = result.markRu,
        markEu = result.markEu,
        finishedAt = result.finishedAt,
        details = result.details.map { detail ->
            StoredQuestionResult(
                detail.questionId,
                detail.score,
                detail.points,
                detail.rightPercent,
                detail.answers,
                detail.correctAnswers
            )
        }
    )
)

private fun StoredCompletedTestReview.toDomain() = CompletedTestReview(
    test = TestDetail(
        id = test.id,
        name = test.name,
        type = test.type,
        timeMinutes = test.timeMinutes,
        totalPoints = test.totalPoints,
        runId = test.runId,
        endsAt = test.endsAt,
        questions = test.questions.map { question ->
            TestQuestion(
                question.id,
                question.count,
                question.typeCode,
                question.typeName,
                question.text,
                question.imageUrls,
                question.textParts,
                question.variants,
                question.variantGroups,
                question.supported,
                question.contentItems.map { item ->
                    TestQuestionContentItem(item.index, item.type, item.text, item.imageUrl)
                }
            )
        }
    ),
    result = TestResult(
        runId = result.runId,
        score = result.score,
        total = result.total,
        markRu = result.markRu,
        markEu = result.markEu,
        finishedAt = result.finishedAt,
        details = result.details.map { detail ->
            TestQuestionResult(
                detail.questionId,
                detail.score,
                detail.points,
                detail.rightPercent,
                detail.answers,
                detail.correctAnswers
            )
        }
    )
)
