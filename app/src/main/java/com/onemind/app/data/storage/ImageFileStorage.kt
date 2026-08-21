package com.onemind.app.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages image file storage for Memories.
 * Stores canonical (optimized) images and thumbnails in app-internal storage.
 */
@Singleton
class ImageFileStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        /** Maximum longest edge for canonical images (pixels) */
        const val MAX_RESOLUTION = 1920

        /** Thumbnail longest edge (pixels) */
        const val THUMBNAIL_SIZE = 256

        /** WebP compression quality (0-100) */
        const val COMPRESSION_QUALITY = 80

        private const val IMAGES_DIR = "memory_images"
        private const val THUMBNAILS_DIR = "memory_thumbnails"
    }

    private val imagesDir: File
        get() = File(context.filesDir, IMAGES_DIR).also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.filesDir, THUMBNAILS_DIR).also { it.mkdirs() }

    /**
     * Save an image from a source file path.
     * Produces a canonical WebP image (resolution-capped) and a thumbnail.
     *
     * @return Pair of (canonicalImagePath, thumbnailPath)
     */
    suspend fun saveImage(sourceFilePath: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val sourceFile = File(sourceFilePath)
        require(sourceFile.exists()) { "Source file does not exist: $sourceFilePath" }

        val originalBitmap = BitmapFactory.decodeFile(sourceFilePath)
            ?: throw IllegalArgumentException("Could not decode image: $sourceFilePath")

        try {
            val fileId = UUID.randomUUID().toString()

            // Canonical image: resize to MAX_RESOLUTION, save as WebP
            val canonicalBitmap = resizeBitmap(originalBitmap, MAX_RESOLUTION)
            val canonicalFile = File(imagesDir, "$fileId.webp")
            saveBitmapAsWebP(canonicalBitmap, canonicalFile, COMPRESSION_QUALITY)
            if (canonicalBitmap !== originalBitmap) {
                canonicalBitmap.recycle()
            }

            // Thumbnail: resize to THUMBNAIL_SIZE, save as WebP
            val thumbnailBitmap = resizeBitmap(originalBitmap, THUMBNAIL_SIZE)
            val thumbnailFile = File(thumbnailsDir, "${fileId}_thumb.webp")
            saveBitmapAsWebP(thumbnailBitmap, thumbnailFile, COMPRESSION_QUALITY)
            if (thumbnailBitmap !== originalBitmap) {
                thumbnailBitmap.recycle()
            }

            originalBitmap.recycle()

            Pair(canonicalFile.absolutePath, thumbnailFile.absolutePath)
        } catch (e: Exception) {
            originalBitmap.recycle()
            throw e
        }
    }

    /**
     * Save an image from a Bitmap directly.
     *
     * @return Pair of (canonicalImagePath, thumbnailPath)
     */
    suspend fun saveImage(bitmap: Bitmap): Pair<String, String> = withContext(Dispatchers.IO) {
        val fileId = UUID.randomUUID().toString()

        // Canonical image
        val canonicalBitmap = resizeBitmap(bitmap, MAX_RESOLUTION)
        val canonicalFile = File(imagesDir, "$fileId.webp")
        saveBitmapAsWebP(canonicalBitmap, canonicalFile, COMPRESSION_QUALITY)
        if (canonicalBitmap !== bitmap) {
            canonicalBitmap.recycle()
        }

        // Thumbnail
        val thumbnailBitmap = resizeBitmap(bitmap, THUMBNAIL_SIZE)
        val thumbnailFile = File(thumbnailsDir, "${fileId}_thumb.webp")
        saveBitmapAsWebP(thumbnailBitmap, thumbnailFile, COMPRESSION_QUALITY)
        if (thumbnailBitmap !== bitmap) {
            thumbnailBitmap.recycle()
        }

        Pair(canonicalFile.absolutePath, thumbnailFile.absolutePath)
    }

    /**
     * Delete an image and its thumbnail by canonical path.
     */
    suspend fun deleteImage(canonicalPath: String) = withContext(Dispatchers.IO) {
        val canonicalFile = File(canonicalPath)
        canonicalFile.delete()

        // Derive thumbnail path from canonical
        val thumbnailFileName = canonicalFile.nameWithoutExtension
            .replace(".webp", "") + "_thumb.webp"
        val thumbnailFile = File(thumbnailsDir, thumbnailFileName)
        thumbnailFile.delete()
    }

    /**
     * Delete all images associated with given paths.
     */
    suspend fun deleteImages(paths: List<String>) = withContext(Dispatchers.IO) {
        paths.forEach { path ->
            File(path).delete()
        }
    }

    /**
     * Get total storage used by all memory images in bytes.
     */
    suspend fun getStorageUsedBytes(): Long = withContext(Dispatchers.IO) {
        val imagesSize = imagesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val thumbsSize = thumbnailsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        imagesSize + thumbsSize
    }

    private fun resizeBitmap(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longestEdge = maxOf(width, height)

        if (longestEdge <= maxEdge) return bitmap

        val scale = maxEdge.toFloat() / longestEdge.toFloat()
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    @Suppress("DEPRECATION")
    private fun saveBitmapAsWebP(bitmap: Bitmap, file: File, quality: Int) {
        FileOutputStream(file).use { out ->
            // Use WEBP_LOSSY on API 30+ (our minimum)
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, out)
        }
    }
}
