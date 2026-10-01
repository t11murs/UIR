package com.example.uir_android.domain.repository

import com.example.uir_android.domain.model.ActiveTestDraft

interface TestDraftRepository {
    suspend fun get(ownerKey: String, testId: Int): ActiveTestDraft?
    suspend fun getActiveTestIds(ownerKey: String): Set<Int>
    suspend fun save(draft: ActiveTestDraft)
    suspend fun delete(ownerKey: String, testId: Int)
    suspend fun deleteAllForOwner(ownerKey: String)
}
