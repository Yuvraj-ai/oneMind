package com.onemind.app.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages model file downloads with progress reporting and resume support.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MODELS_DIR = "models"
        private const val BUFFER_SIZE = 8192
    }

    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    /**
     * Download a model file with progress reporting.
     * Supports resume if a partial file exists.
     *
     * @param modelId Unique model identifier (used as filename)
     * @param downloadUrl URL to download from
     * @return Flow of [DownloadProgress] updates
     */
    fun downloadModel(modelId: String, downloadUrl: String): Flow<DownloadProgress> = flow {
        val targetFile = File(modelsDir, modelId)
        val tempFile = File(modelsDir, "$modelId.tmp")

        emit(DownloadProgress.Started(modelId))

        try {
            val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000

            // Resume support
            if (existingBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$existingBytes-")
            }

            connection.connect()

            val responseCode = connection.responseCode
            val totalBytes = when {
                responseCode == 206 -> existingBytes + connection.contentLengthLong
                responseCode == 200 -> connection.contentLengthLong
                else -> throw RuntimeException("HTTP error: $responseCode")
            }

            // If server doesn't support range and we have partial, restart
            val append = responseCode == 206
            if (!append && tempFile.exists()) {
                tempFile.delete()
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile, append)

            val buffer = ByteArray(BUFFER_SIZE)
            var downloadedBytes = if (append) existingBytes else 0L
            var bytesRead: Int

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        emit(DownloadProgress.Downloading(
                            modelId = modelId,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes,
                            progressPercent = if (totalBytes > 0) {
                                (downloadedBytes * 100 / totalBytes).toInt()
                            } else 0
                        ))
                    }
                }
            }

            // Rename temp to final
            tempFile.renameTo(targetFile)

            emit(DownloadProgress.Completed(modelId, targetFile.absolutePath))

        } catch (e: Exception) {
            // Keep temp file for resume
            emit(DownloadProgress.Failed(modelId, e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Cancel an in-progress download by deleting the temp file.
     */
    fun cancelDownload(modelId: String) {
        val tempFile = File(modelsDir, "$modelId.tmp")
        tempFile.delete()
    }

    /**
     * Check if a model is already downloaded.
     */
    fun isModelDownloaded(modelId: String): Boolean {
        return File(modelsDir, modelId).exists()
    }

    /**
     * Get the file path of a downloaded model.
     */
    fun getModelPath(modelId: String): String? {
        val file = File(modelsDir, modelId)
        return if (file.exists()) file.absolutePath else null
    }

    /**
     * Delete a cached model file.
     * @return freed bytes
     */
    fun deleteModel(modelId: String): Long {
        val file = File(modelsDir, modelId)
        val size = file.length()
        file.delete()
        return size
    }

    /**
     * Total storage used by all downloaded models.
     */
    fun getTotalStorageUsedBytes(): Long {
        return modelsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}

/**
 * Progress updates emitted during a model download.
 */
sealed class DownloadProgress {
    abstract val modelId: String

    data class Started(override val modelId: String) : DownloadProgress()

    data class Downloading(
        override val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int
    ) : DownloadProgress()

    data class Completed(
        override val modelId: String,
        val filePath: String
    ) : DownloadProgress()

    data class Failed(
        override val modelId: String,
        val error: String
    ) : DownloadProgress()
}
