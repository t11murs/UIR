package com.example.uir_android.domain.model

sealed interface AttachmentDownloadStatus {
    data class Downloading(
        val downloadId: Long,
        val progressPercent: Int?
    ) : AttachmentDownloadStatus

    data class Completed(
        val downloadId: Long
    ) : AttachmentDownloadStatus

    data class Failed(
        val message: String
    ) : AttachmentDownloadStatus
}
