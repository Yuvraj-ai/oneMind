package com.onemind.app.capture

import android.content.ClipData
import android.content.Context
import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType

/**
 * Parses a clipboard clip into a ContentBlock.
 *
 * Extracted from [ClipboardTileService] so the parsing logic is testable without
 * needing a running TileService. The service delegates here.
 */
object ClipboardParser {

    private val URL_PATTERN = Regex("""^https?://\S+$""", RegexOption.IGNORE_CASE)

    /**
     * Parse the primary clip into a ContentBlock.
     *
     * Returns null when the clipboard is empty or contains nothing usable.
     *
     * @param clip The primary clip, or null if empty.
     * @param context Needed to coerce HTML clips to text.
     */
    fun parse(clip: ClipData?, context: Context): ContentBlock? {
        if (clip == null || clip.itemCount == 0) return null
        return parseItem(clip.getItemAt(0), context)
    }

    private fun parseItem(item: ClipData.Item, context: Context): ContentBlock? {
        // URI in clipboard → URL block if it is an HTTP(S) link.
        val uri = item.uri
        if (uri != null && uri.scheme?.startsWith("http") == true) {
            return ContentBlock(type = ContentType.URL, content = uri.toString())
        }

        // Text (plain or coerced from HTML).
        val text = item.coerceToText(context)?.toString()?.trim()
        if (!text.isNullOrBlank()) {
            if (text.lines().size == 1 && URL_PATTERN.matches(text)) {
                return ContentBlock(type = ContentType.URL, content = text)
            }
            return ContentBlock(type = ContentType.TEXT, content = text)
        }

        return null
    }
}
