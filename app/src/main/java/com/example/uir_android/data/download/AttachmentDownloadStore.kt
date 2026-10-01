package com.example.uir_android.data.download

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.attachmentDownloadsDataStore by preferencesDataStore(
    name = "attachment_downloads"
)

@Serializable
internal enum class StoredDownloadState {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

@Serializable
internal data class AttachmentDownloadRecord(
    val id: Long,
    val ownerKey: String,
    val sourceUrl: String,
    val resolvedUrl: String,
    val displayName: String,
    val temporaryFileName: String,
    val completedFileName: String,
    val state: StoredDownloadState = StoredDownloadState.QUEUED,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val mimeType: String? = null,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Singleton
class AttachmentDownloadStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    internal suspend fun find(ownerKey: String, sourceUrl: String): AttachmentDownloadRecord? =
        records().first().firstOrNull { record ->
            record.ownerKey == ownerKey && record.sourceUrl == sourceUrl
        }

    internal suspend fun get(downloadId: Long): AttachmentDownloadRecord? =
        records().first().firstOrNull { it.id == downloadId }

    internal fun observe(downloadId: Long): Flow<AttachmentDownloadRecord?> = records()
        .map { records -> records.firstOrNull { it.id == downloadId } }

    internal suspend fun put(record: AttachmentDownloadRecord) {
        context.attachmentDownloadsDataStore.edit { preferences ->
            val current = decode(preferences[DOWNLOADS])
            save(preferences, current.filterNot { it.id == record.id } + record)
        }
    }

    internal suspend fun remove(downloadId: Long) {
        context.attachmentDownloadsDataStore.edit { preferences ->
            save(
                preferences,
                decode(preferences[DOWNLOADS]).filterNot { it.id == downloadId }
            )
        }
    }

    private fun records(): Flow<List<AttachmentDownloadRecord>> =
        context.attachmentDownloadsDataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .map { preferences -> decode(preferences[DOWNLOADS]) }

    private fun decode(value: String?): List<AttachmentDownloadRecord> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<AttachmentDownloadRecord>>(value) }
            .getOrDefault(emptyList())
    }

    private fun save(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        records: List<AttachmentDownloadRecord>
    ) {
        if (records.isEmpty()) {
            preferences.remove(DOWNLOADS)
        } else {
            preferences[DOWNLOADS] = json.encodeToString(records)
        }
    }

    private companion object {
        val DOWNLOADS = stringPreferencesKey("downloads_v2")
    }
}
