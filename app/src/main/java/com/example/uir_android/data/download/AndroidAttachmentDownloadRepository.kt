package com.example.uir_android.data.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.SystemClock
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.uir_android.BuildConfig
import com.example.uir_android.core.common.AppDispatchers
import com.example.uir_android.core.common.AppErrorType
import com.example.uir_android.core.common.AppResult
import com.example.uir_android.core.common.ApplicationScope
import com.example.uir_android.core.network.resolveServerUrl
import com.example.uir_android.data.local.AppSettingsStore
import com.example.uir_android.data.local.normalizeAccountOwnerKey
import com.example.uir_android.data.remote.ServerSessionGuard
import com.example.uir_android.data.remote.awaitResponse
import com.example.uir_android.domain.model.AttachmentDownloadStatus
import com.example.uir_android.domain.repository.AttachmentDownloadRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class AndroidAttachmentDownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val settingsStore: AppSettingsStore,
    private val sessionGuard: ServerSessionGuard,
    private val downloadStore: AttachmentDownloadStore,
    private val dispatchers: AppDispatchers,
    @ApplicationScope private val applicationScope: CoroutineScope
) : AttachmentDownloadRepository {
    private val enqueueMutex = Mutex()
    private val downloadJobs = mutableMapOf<Long, Job>()
    private val downloadClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    init {
        applicationScope.launch {
            var previousOwner: String? = null
            settingsStore.authSessionFlow
                .map { session ->
                    if (session.isLoggedIn) ownerKey(session.email) else ANONYMOUS_OWNER_KEY
                }
                .distinctUntilChanged()
                .collect { owner ->
                    if (previousOwner != null && previousOwner != owner) {
                        cancelRunningDownloads()
                    }
                    previousOwner = owner
                }
        }
    }

    override suspend fun findDownloadId(url: String): Long? = withContext(dispatchers.io) {
        val ownerKey = currentOwnerKey()
        val record = downloadStore.find(ownerKey, url) ?: return@withContext null
        val checked = if (record.state == StoredDownloadState.COMPLETED &&
            !completedFile(record).isFile
        ) {
            record.copy(
                state = StoredDownloadState.FAILED,
                errorMessage = FILE_MISSING_MESSAGE,
                updatedAt = System.currentTimeMillis()
            ).also { downloadStore.put(it) }
        } else {
            record
        }
        if (checked.state.isActive) ensureDownloadRunning(checked.id)
        checked.id
    }

    override suspend fun enqueue(url: String, fileName: String): AppResult<Long> =
        withContext(dispatchers.io) {
            enqueueMutex.withLock {
                try {
                    val resolvedUrl = resolveServerUrl(url)
                    val httpUrl = resolvedUrl.toHttpUrlOrNull()
                        ?: return@withLock AppResult.Error("Некорректная ссылка на файл")
                    if (httpUrl.scheme !in setOf("http", "https")) {
                        return@withLock AppResult.Error("Некорректная ссылка на файл")
                    }
                    val session = settingsStore.currentAuthSession()
                    if (httpUrl.isServerUrl() && session.cookieHeader.isBlank()) {
                        return@withLock AppResult.Error(
                            message = "Для скачивания файла требуется вход в аккаунт",
                            type = AppErrorType.AUTHENTICATION
                        )
                    }
                    val ownerKey = ownerKey(session.email)
                    val existing = downloadStore.find(ownerKey, url)
                    if (existing != null) {
                        if (existing.state == StoredDownloadState.COMPLETED &&
                            completedFile(existing).isFile
                        ) {
                            return@withLock AppResult.Success(existing.id)
                        }
                        if (existing.state.isActive) {
                            ensureDownloadRunning(existing.id)
                            return@withLock AppResult.Success(existing.id)
                        }
                        if (existing.state == StoredDownloadState.FAILED &&
                            temporaryFile(existing).isFile
                        ) {
                            downloadStore.put(
                                existing.copy(
                                    state = StoredDownloadState.QUEUED,
                                    errorMessage = null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            ensureDownloadRunning(existing.id)
                            return@withLock AppResult.Success(existing.id)
                        }
                        deleteFiles(existing)
                        downloadStore.remove(existing.id)
                    }

                    val directory = downloadDirectory().also { dir ->
                        if (!dir.exists() && !dir.mkdirs()) {
                            throw IOException("Не удалось создать каталог загрузок")
                        }
                    }
                    val id = nextDownloadId()
                    val safeName = sanitizeAttachmentFileName(fileName, resolvedUrl)
                    val completedName = "${id}_$safeName"
                    val record = AttachmentDownloadRecord(
                        id = id,
                        ownerKey = ownerKey,
                        sourceUrl = url,
                        resolvedUrl = resolvedUrl,
                        displayName = safeName,
                        temporaryFileName = "$completedName.part",
                        completedFileName = completedName,
                        mimeType = mimeTypeFor(safeName)
                    )
                    check(directory.resolve(record.temporaryFileName).canonicalPath.startsWith(directory.canonicalPath))
                    downloadStore.put(record)
                    ensureDownloadRunning(id)
                    AppResult.Success(id)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppResult.Error("Не удалось начать скачивание", error)
                }
            }
        }

    override fun observe(downloadId: Long): Flow<AttachmentDownloadStatus> = flow {
        val record = downloadStore.get(downloadId)
        if (record?.state?.isActive == true) ensureDownloadRunning(downloadId)
        emitAll(
            downloadStore.observe(downloadId)
                .map(::toDownloadStatus)
                .distinctUntilChanged()
                .transformWhile { status ->
                    emit(status)
                    status is AttachmentDownloadStatus.Downloading
                }
        )
    }.flowOn(dispatchers.io)

    override suspend fun cancel(downloadId: Long): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            val record = downloadStore.get(downloadId)
            if (record != null) {
                if (record.ownerKey != currentOwnerKey()) {
                    return@withContext AppResult.Error("Скачивание принадлежит другому аккаунту")
                }
                removeJob(downloadId)?.cancelAndJoin()
                deleteFiles(record)
                downloadStore.remove(downloadId)
            }
            AppResult.Success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppResult.Error("Не удалось отменить скачивание", error)
        }
    }

    override suspend fun open(downloadId: Long): AppResult<Unit> {
        val prepared = withContext(dispatchers.io) {
            try {
                val record = downloadStore.get(downloadId)
                    ?: return@withContext AppResult.Error(
                        FILE_MISSING_MESSAGE,
                        FileNotFoundException(FILE_MISSING_MESSAGE)
                    )
                if (record.ownerKey != currentOwnerKey()) {
                    return@withContext AppResult.Error("Файл принадлежит другому аккаунту")
                }
                val file = completedFile(record)
                if (record.state != StoredDownloadState.COMPLETED || !file.isFile) {
                    downloadStore.put(
                        record.copy(
                            state = StoredDownloadState.FAILED,
                            errorMessage = FILE_MISSING_MESSAGE,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    return@withContext AppResult.Error(
                        FILE_MISSING_MESSAGE,
                        FileNotFoundException(FILE_MISSING_MESSAGE)
                    )
                }
                AppResult.Success(
                    OpenedAttachment(
                        uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.attachment-files",
                            file
                        ),
                        mimeType = record.mimeType ?: mimeTypeFor(record.displayName) ?: "*/*"
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppResult.Error("Не удалось подготовить файл к открытию", error)
            }
        }
        if (prepared is AppResult.Error) return prepared
        val attachment = (prepared as AppResult.Success).data
        return withContext(dispatchers.main) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(attachment.uri, attachment.mimeType)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
                AppResult.Success(Unit)
            } catch (error: ActivityNotFoundException) {
                AppResult.Error("Нет приложения для открытия этого файла", error)
            } catch (error: Exception) {
                AppResult.Error("Не удалось открыть файл", error)
            }
        }
    }

    private fun ensureDownloadRunning(downloadId: Long) {
        synchronized(downloadJobs) {
            if (downloadJobs[downloadId]?.isActive == true) return
            val job = applicationScope.launch(start = CoroutineStart.LAZY) {
                runDownload(downloadId)
            }
            downloadJobs[downloadId] = job
            job.invokeOnCompletion {
                synchronized(downloadJobs) {
                    if (downloadJobs[downloadId] === job) downloadJobs.remove(downloadId)
                }
            }
            job.start()
        }
    }

    private suspend fun runDownload(downloadId: Long) {
        var lastError: Throwable? = null
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            try {
                downloadOnce(downloadId)
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: NonRetryableDownloadException) {
                failDownload(downloadId, error.message ?: DOWNLOAD_FAILED_MESSAGE)
                return
            } catch (error: Exception) {
                lastError = error
                if (attempt + 1 < MAX_DOWNLOAD_ATTEMPTS) {
                    delay(RETRY_BASE_DELAY_MS * (attempt + 1))
                }
            }
        }
        failDownload(downloadId, downloadErrorMessage(lastError))
    }

    private suspend fun downloadOnce(downloadId: Long) {
        val record = downloadStore.get(downloadId) ?: return
        val tempFile = temporaryFile(record)
        tempFile.parentFile?.mkdirs()
        val existingBytes = tempFile.takeIf(File::isFile)?.length() ?: 0L
        val session = settingsStore.currentAuthSession()
        if (record.ownerKey != ownerKey(session.email)) {
            throw NonRetryableDownloadException("Аккаунт изменился. Начните скачивание заново")
        }
        val targetUrl = record.resolvedUrl.toHttpUrlOrNull()
            ?: throw NonRetryableDownloadException("Некорректная ссылка на файл")
        if (targetUrl.isServerUrl() && session.cookieHeader.isBlank()) {
            throw NonRetryableDownloadException("Сессия истекла. Войдите в аккаунт снова")
        }
        val request = Request.Builder()
            .url(targetUrl)
            .get()
            .header("Accept", "application/octet-stream,*/*")
            .header("User-Agent", "UIR-Android/${BuildConfig.VERSION_NAME}")
            .apply {
                if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
                if (targetUrl.isServerUrl()) header("Cookie", session.cookieHeader)
            }
            .build()

        downloadClient.newCall(request).awaitResponse().use { response ->
            if (response.isRedirect) {
                val redirectPath = response.header("Location")
                    ?.let(targetUrl::resolve)
                    ?.encodedPath
                    ?: targetUrl.encodedPath
                val accessError = sessionGuard.responseError(
                    code = response.code,
                    finalPath = redirectPath,
                    body = ""
                )
                throw NonRetryableDownloadException(
                    accessError?.message ?: "Сервер перенаправил скачивание"
                )
            }
            if (response.code == 401 || response.code == 403) {
                val accessError = sessionGuard.responseError(
                    code = response.code,
                    finalPath = targetUrl.encodedPath,
                    body = ""
                )
                throw NonRetryableDownloadException(
                    accessError?.message ?: "Сервер отклонил скачивание: ${response.code}"
                )
            }
            if (response.code == 408 || response.code == 425 || response.code == 429 ||
                response.code in 500..599
            ) {
                throw IOException("Временная ошибка сервера: ${response.code}")
            }
            if (response.code == 416 && completedRangeSize(response.header("Content-Range")) == existingBytes) {
                completeDownload(record, tempFile, record.mimeType)
                return
            }
            if (response.code !in setOf(200, 206)) {
                throw NonRetryableDownloadException("Сервер отклонил скачивание: ${response.code}")
            }
            val append = response.code == 206 && existingBytes > 0L
            if (response.code == 206 &&
                contentRangeStart(response.header("Content-Range")) != existingBytes
            ) {
                throw NonRetryableDownloadException("Сервер вернул некорректный диапазон файла")
            }
            val initialBytes = if (append) existingBytes else 0L
            val body = response.body ?: throw IOException("Сервер вернул пустой файл")
            val totalBytes = responseTotalBytes(
                responseCode = response.code,
                existingBytes = initialBytes,
                bodyLength = body.contentLength(),
                contentRange = response.header("Content-Range")
            )
            var downloadedBytes = initialBytes
            var lastPersistedAt = 0L
            val responseMimeType = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?: record.mimeType
            downloadStore.put(
                record.copy(
                    state = StoredDownloadState.DOWNLOADING,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    mimeType = responseMimeType,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
            body.byteStream().use { input ->
                FileOutputStream(tempFile, append).buffered().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastPersistedAt >= PROGRESS_PERSIST_INTERVAL_MS) {
                            lastPersistedAt = now
                            downloadStore.put(
                                record.copy(
                                    state = StoredDownloadState.DOWNLOADING,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    mimeType = responseMimeType,
                                    errorMessage = null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                    output.flush()
                }
            }
            if (totalBytes != null && downloadedBytes != totalBytes) {
                throw IOException("Размер полученного файла не совпадает с ожидаемым")
            }
            completeDownload(
                record = record.copy(
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    mimeType = responseMimeType
                ),
                tempFile = tempFile,
                mimeType = responseMimeType
            )
        }
    }

    private suspend fun completeDownload(
        record: AttachmentDownloadRecord,
        tempFile: File,
        mimeType: String?
    ) {
        if (!tempFile.isFile) throw FileNotFoundException(FILE_MISSING_MESSAGE)
        val completedFile = completedFile(record)
        if (completedFile.exists() && !completedFile.delete()) {
            throw IOException("Не удалось заменить ранее скачанный файл")
        }
        if (!tempFile.renameTo(completedFile)) {
            tempFile.copyTo(completedFile, overwrite = true)
            tempFile.delete()
        }
        if (!completedFile.isFile) throw IOException("Не удалось сохранить скачанный файл")
        downloadStore.put(
            record.copy(
                state = StoredDownloadState.COMPLETED,
                downloadedBytes = completedFile.length(),
                totalBytes = completedFile.length(),
                mimeType = mimeType,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun failDownload(downloadId: Long, message: String) {
        val record = downloadStore.get(downloadId) ?: return
        downloadStore.put(
            record.copy(
                state = StoredDownloadState.FAILED,
                downloadedBytes = temporaryFile(record).takeIf(File::isFile)?.length() ?: 0L,
                errorMessage = message,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun nextDownloadId(): Long {
        var candidate = System.currentTimeMillis().coerceAtLeast(1L)
        while (downloadStore.get(candidate) != null) candidate += 1L
        return candidate
    }

    private suspend fun currentOwnerKey(): String =
        ownerKey(settingsStore.currentAuthSession().email)

    private fun ownerKey(email: String): String =
        normalizeAccountOwnerKey(email).ifBlank { ANONYMOUS_OWNER_KEY }

    private fun HttpUrl.isServerUrl(): Boolean =
        host.equals(BuildConfig.SERVER_BASE_URL.toHttpUrlOrNull()?.host, ignoreCase = true)

    private fun downloadDirectory(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?.resolve(DOWNLOAD_DIRECTORY_NAME)
            ?: context.filesDir.resolve(FALLBACK_DOWNLOAD_DIRECTORY)

    private fun temporaryFile(record: AttachmentDownloadRecord): File =
        downloadDirectory().resolve(record.temporaryFileName)

    private fun completedFile(record: AttachmentDownloadRecord): File =
        downloadDirectory().resolve(record.completedFileName)

    private fun deleteFiles(record: AttachmentDownloadRecord) {
        temporaryFile(record).delete()
        completedFile(record).delete()
    }

    private fun removeJob(downloadId: Long): Job? = synchronized(downloadJobs) {
        downloadJobs.remove(downloadId)
    }

    private fun cancelRunningDownloads() {
        val jobs = synchronized(downloadJobs) {
            downloadJobs.values.toList().also { downloadJobs.clear() }
        }
        jobs.forEach(Job::cancel)
    }

    private fun toDownloadStatus(record: AttachmentDownloadRecord?): AttachmentDownloadStatus {
        if (record == null) return AttachmentDownloadStatus.Failed("Скачивание не найдено")
        return when (record.state) {
            StoredDownloadState.QUEUED,
            StoredDownloadState.DOWNLOADING -> AttachmentDownloadStatus.Downloading(
                downloadId = record.id,
                progressPercent = record.totalBytes
                    ?.takeIf { it > 0L }
                    ?.let { total ->
                        ((record.downloadedBytes * 100L) / total).toInt().coerceIn(0, 100)
                    }
            )
            StoredDownloadState.COMPLETED -> AttachmentDownloadStatus.Completed(record.id)
            StoredDownloadState.FAILED -> AttachmentDownloadStatus.Failed(
                record.errorMessage ?: DOWNLOAD_FAILED_MESSAGE
            )
        }
    }

    private fun downloadErrorMessage(error: Throwable?): String = when (error) {
        is FileNotFoundException -> FILE_MISSING_MESSAGE
        else -> DOWNLOAD_FAILED_MESSAGE
    }

    private fun mimeTypeFor(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return extension.takeIf(String::isNotBlank)
            ?.let(MimeTypeMap.getSingleton()::getMimeTypeFromExtension)
    }

    private companion object {
        const val MAX_DOWNLOAD_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 750L
        const val PROGRESS_PERSIST_INTERVAL_MS = 500L
        const val DOWNLOAD_BUFFER_SIZE = 32 * 1024
        const val DOWNLOAD_DIRECTORY_NAME = "UIR"
        const val FALLBACK_DOWNLOAD_DIRECTORY = "attachment_downloads"
        const val ANONYMOUS_OWNER_KEY = "__anonymous__"
        const val FILE_MISSING_MESSAGE = "Скачанный файл не найден. Скачайте его повторно"
        const val DOWNLOAD_FAILED_MESSAGE = "Не удалось скачать файл"
    }
}

private data class OpenedAttachment(
    val uri: android.net.Uri,
    val mimeType: String
)

private class NonRetryableDownloadException(message: String) : IOException(message)

private val StoredDownloadState.isActive: Boolean
    get() = this == StoredDownloadState.QUEUED || this == StoredDownloadState.DOWNLOADING

internal fun contentRangeStart(contentRange: String?): Long? = contentRange
    ?.let { CONTENT_RANGE_PATTERN.matchEntire(it.trim()) }
    ?.groupValues
    ?.getOrNull(1)
    ?.toLongOrNull()

internal fun completedRangeSize(contentRange: String?): Long? = contentRange
    ?.let { UNSATISFIED_RANGE_PATTERN.matchEntire(it.trim()) }
    ?.groupValues
    ?.getOrNull(1)
    ?.toLongOrNull()

internal fun responseTotalBytes(
    responseCode: Int,
    existingBytes: Long,
    bodyLength: Long,
    contentRange: String?
): Long? {
    val rangeTotal = contentRange
        ?.let { CONTENT_RANGE_PATTERN.matchEntire(it.trim()) }
        ?.groupValues
        ?.getOrNull(3)
        ?.takeUnless { it == "*" }
        ?.toLongOrNull()
    if (rangeTotal != null) return rangeTotal
    if (bodyLength < 0L) return null
    return if (responseCode == 206) existingBytes + bodyLength else bodyLength
}

internal fun sanitizeAttachmentFileName(fileName: String, url: String): String {
    val fallback = url.substringBefore('?').substringAfterLast('/').ifBlank { "attachment" }
    val decoded = runCatching {
        URLDecoder.decode(fileName.ifBlank { fallback }, StandardCharsets.UTF_8.name())
    }.getOrDefault(fileName.ifBlank { fallback })
    return decoded
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(120)
        .ifBlank { "attachment" }
}

private val CONTENT_RANGE_PATTERN = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
private val UNSATISFIED_RANGE_PATTERN = Regex("bytes\\s+\\*/(\\d+)", RegexOption.IGNORE_CASE)

@Module
@InstallIn(SingletonComponent::class)
abstract class AttachmentDownloadModule {
    @Binds
    abstract fun bindAttachmentDownloadRepository(
        implementation: AndroidAttachmentDownloadRepository
    ): AttachmentDownloadRepository
}
