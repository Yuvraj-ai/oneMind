package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.StageStatus

/**
 * What to call a Memory on a card, and what to say about it.
 *
 * Pure, and separate from any Composable, so the feed, the timeline and a search result
 * cannot drift into three slightly different answers — which is what happened when this
 * lived as a private function in one card and a second copy in another.
 *
 * A title and a snippet are two questions, not one. The old joined
 * `"$title — $summaryText"` could only ever be drawn as a single run of body text; the
 * reference sets the title in Outfit at 17 sp and clamps it to three lines, with the
 * snippet as separate, quieter text.
 */
object MemoryDisplay {

    /** What an image-only Memory is called before anything has read it. */
    const val FALLBACK_TITLE = "Untitled memory"

    /**
     * How much of the user's own first line can stand in for a title.
     *
     * Truncated here rather than clamped by the Composable, because a title that wraps to
     * three lines in a two-column grid pushes the chips and the footer off the card.
     */
    const val TITLE_FALLBACK_CHARS = 60

    /**
     * The model's title if it produced one, else the user's own first line.
     *
     * `StageStatus.SUCCESS` is required rather than just a non-null title: a stage that
     * failed may still have written a row, and its title describes whatever it managed
     * before failing.
     */
    fun title(memory: Memory): String {
        val summary = memory.derived.summary
        if (summary?.status == StageStatus.SUCCESS) {
            val title = summary.title?.takeIf { it.isNotBlank() }
            if (title != null) return title
        }

        val firstLine = memory.userText()
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            ?.trim()

        if (firstLine.isNullOrEmpty()) return FALLBACK_TITLE
        return if (firstLine.length <= TITLE_FALLBACK_CHARS) {
            firstLine
        } else {
            firstLine.take(TITLE_FALLBACK_CHARS) + "…"
        }
    }

    /**
     * The line under the title: the model's summary, or the user's text.
     *
     * Empty when the title already *is* the user's text, because a card that says the
     * same thing twice in two sizes looks like a rendering bug.
     */
    fun snippet(memory: Memory): String {
        val summary = memory.derived.summary
        if (summary?.status == StageStatus.SUCCESS && summary.summaryText.isNotBlank()) {
            return summary.summaryText
        }

        val text = memory.userText().trim()
        if (text.isEmpty()) return ""
        return if (text == title(memory)) "" else text
    }
}
