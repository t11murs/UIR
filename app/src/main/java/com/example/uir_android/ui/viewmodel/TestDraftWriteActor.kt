package com.example.uir_android.ui.viewmodel

import com.example.uir_android.domain.model.ActiveTestDraft
import com.example.uir_android.domain.repository.TestDraftRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal class TestDraftWriteActor(
    private val repository: TestDraftRepository,
    scope: CoroutineScope,
    private val onFailure: (String) -> Unit
) {
    private val lock = Any()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val waiters = linkedMapOf<Long, CompletableDeferred<Boolean>>()
    private var latestOperation: VersionedOperation? = null
    private var nextRevision = 0L
    private var completedRevision = 0L
    private var closed = false

    init {
        scope.launch { consumeOperations() }
    }

    fun enqueueSave(draft: ActiveTestDraft, errorMessage: String): Boolean =
        enqueue(DraftOperation.Save(draft, errorMessage)) != null

    fun requestSave(draft: ActiveTestDraft, errorMessage: String): CompletableDeferred<Boolean> {
        val completion = CompletableDeferred<Boolean>()
        enqueue(DraftOperation.Save(draft, errorMessage), completion)
        return completion
    }

    suspend fun saveAndAwait(draft: ActiveTestDraft, errorMessage: String): Boolean =
        requestSave(draft, errorMessage).await()

    fun enqueueDelete(ownerKey: String, testId: Int, errorMessage: String): Boolean =
        enqueue(DraftOperation.Delete(ownerKey, testId, errorMessage)) != null

    suspend fun deleteAndAwait(ownerKey: String, testId: Int, errorMessage: String): Boolean =
        enqueueAndAwait(DraftOperation.Delete(ownerKey, testId, errorMessage))

    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            signal.close()
        }
    }

    private suspend fun enqueueAndAwait(operation: DraftOperation): Boolean {
        val completion = CompletableDeferred<Boolean>()
        if (enqueue(operation, completion) == null) return false
        return completion.await()
    }

    private fun enqueue(
        operation: DraftOperation,
        completion: CompletableDeferred<Boolean>? = null
    ): Long? = synchronized(lock) {
        if (closed) {
            completion?.complete(false)
            return@synchronized null
        }
        val revision = ++nextRevision
        latestOperation = VersionedOperation(revision, operation)
        if (completion != null) waiters[revision] = completion
        if (signal.trySend(Unit).isFailure) {
            waiters.remove(revision)?.complete(false)
            return@synchronized null
        }
        revision
    }

    private suspend fun consumeOperations() {
        try {
            for (ignored in signal) {
                persistLatestOperations()
            }
        } finally {
            val pendingWaiters = synchronized(lock) {
                waiters.values.toList().also { waiters.clear() }
            }
            pendingWaiters.forEach { it.cancel() }
        }
    }

    private suspend fun persistLatestOperations() {
        while (true) {
            val versioned = synchronized(lock) {
                latestOperation?.takeIf { it.revision > completedRevision }
            } ?: return
            val succeeded = persist(versioned.operation)
            val completedWaiters = synchronized(lock) {
                completedRevision = maxOf(completedRevision, versioned.revision)
                waiters.entries
                    .filter { (revision, _) -> revision <= versioned.revision }
                    .map { it.key to it.value }
                    .also { completed -> completed.forEach { waiters.remove(it.first) } }
                    .map { it.second }
            }
            completedWaiters.forEach { it.complete(succeeded) }
        }
    }

    private suspend fun persist(operation: DraftOperation): Boolean = try {
        when (operation) {
            is DraftOperation.Save -> repository.save(operation.draft)
            is DraftOperation.Delete -> repository.delete(operation.ownerKey, operation.testId)
        }
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        onFailure(operation.errorMessage)
        false
    }
}

private data class VersionedOperation(
    val revision: Long,
    val operation: DraftOperation
)

private sealed interface DraftOperation {
    val errorMessage: String

    data class Save(
        val draft: ActiveTestDraft,
        override val errorMessage: String
    ) : DraftOperation

    data class Delete(
        val ownerKey: String,
        val testId: Int,
        override val errorMessage: String
    ) : DraftOperation
}
