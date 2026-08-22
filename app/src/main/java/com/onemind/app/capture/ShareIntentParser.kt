package com.onemind.app.capture

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * Extracts content blocks from an incoming Share intent.
 *
 * This is the seam between Android's intent system and oneMind's domain. Intent
 * data is chaotic — apps send text that is really a URL, images as content URIs
 * that may be permission-gated, multiple items in unpredictable orders — and this
 * absorbs all of that so the rest of the app only sees [ContentBlock]s.
 *
 * Images are copied to a temp file because content URIs are only readable while
 * the receiver Activity is alive; [ImageFileStorage] later moves them to
 * permanent storage. Text and URLs are extracted in place.
 */
class ShareIntentParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Parse a share intent into content blocks.
     *
     * Returns an empty list when there is nothing usable — the caller decides
     * whether to notify the user, not the parser.
     */
    fun parse(intent: Intent): List<ContentBlock> {
        val action = intent.action ?: return emptyList()

        return when (action) {
            Intent.ACTION_SEND -> parseSingle(intent)
            Intent.ACTION_SEND_MULTIPLE -> parseMultiple(intent)
            else -> emptyList()
        }
    }

    /**
     * Best-effort source detection.
     *
     * On some devices and Android versions callingPackage is null. Never fabricate
     * a source — null is the honest answer.
     */
    fun detectSource(intent: Intent): String? {
        // The referrer extra is the most reliable on modern Android.
        val referrer = intent.getStringExtra(Intent.EXTRA_REFERRER)
        if (referrer != null && referrer.startsWith("android-app://")) {
            return referrer.removePrefix("android-app://").substringBefore('/')
        }
        return null
    }

    private fun parseSingle(intent: Intent): List<ContentBlock> {
        val type = intent.type ?: return emptyList()
        val blocks = mutableListOf<ContentBlock>()

        when {
            type.startsWith("image/") -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    copyImageToTemp(uri)?.let { path ->
                        blocks.add(ContentBlock(type = ContentType.IMAGE, content = path))
                    }
                }
                // An image share may also include text (e.g. a caption).
                extractText(intent)?.let { blocks.add(it) }
            }

            type == "text/plain" || type.startsWith("text/") -> {
                extractText(intent)?.let { blocks.add(it) }
            }

            else -> {
                // Unknown type — try text as fallback.
                extractText(intent)?.let { blocks.add(it) }
            }
        }

        return blocks
    }

    private fun parseMultiple(intent: Intent): List<ContentBlock> {
        val blocks = mutableListOf<ContentBlock>()

        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        if (uris != null) {
            uris.forEachIndexed { index, uri ->
                copyImageToTemp(uri)?.let { path ->
                    blocks.add(ContentBlock(type = ContentType.IMAGE, content = path, position = index))
                }
            }
        }

        // Multiple image shares can also include text.
        extractText(intent)?.let { blocks.add(it) }

        return blocks
    }

    /**
     * Extract text from the intent, classifying it as TEXT or URL.
     *
     * Many apps share a URL by putting it in EXTRA_TEXT with type "text/plain".
     * Detecting this avoids the user having to manually re-classify it.
     */
    private fun extractText(intent: Intent): ContentBlock? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        if (text.isNullOrBlank()) return null

        // A single line that looks like a URL → URL block.
        if (text.lines().size == 1 && URL_PATTERN.matches(text)) {
            return ContentBlock(type = ContentType.URL, content = text)
        }

        return ContentBlock(type = ContentType.TEXT, content = text)
    }

    /**
     * Copy a content URI to a temp file in app storage.
     *
     * Content URIs are only readable during the receiver's lifecycle, so we must
     * copy them now. ImageFileStorage later converts these to canonical WebP and
     * produces thumbnails.
     *
     * Returns the temp file path, or null if the copy failed (permission denied,
     * unreadable stream, etc.).
     */
    private fun copyImageToTemp(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempDir = File(context.cacheDir, "share_import_temp").also { it.mkdirs() }
            val tempFile = File(tempDir, "${UUID.randomUUID()}.tmp")

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                return null
            }

            tempFile.absolutePath
        } catch (e: SecurityException) {
            // Permission to read the URI was denied.
            null
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private val URL_PATTERN = Regex(
            """^https?://\S+$""",
            RegexOption.IGNORE_CASE
        )
    }
}
