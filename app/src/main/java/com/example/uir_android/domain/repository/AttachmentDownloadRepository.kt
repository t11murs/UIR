package com.example.uir_android.domain.repository

import com.example.uir_android.core.common.AppResult
import com.example.uir_android.domain.model.AttachmentDownloadStatus
import kotlinx.coroutines.flow.Flow

interface AttachmentDownloadRepository {
    suspend fun findDownloadId(url: String): Long?

    suspend fun enqueue(url: String, fileName: String): AppResult<Long>

    fun observe(downloadId: Long): Flow<AttachmentDownloadStatus>

    suspend fun cancel(downloadId: Long): AppResult<Unit>

    suspend fun open(downloadId: Long): AppResult<Unit>
}
