package com.example.uir_android.domain.repository

import com.example.uir_android.domain.model.EmulatorControlDraft

interface EmulatorControlDraftRepository {
    suspend fun get(ownerKey: String, controlId: Int): EmulatorControlDraft?
    suspend fun getActiveControlIds(ownerKey: String): Set<Int>
    suspend fun save(draft: EmulatorControlDraft)
    suspend fun delete(ownerKey: String, controlId: Int)
}
