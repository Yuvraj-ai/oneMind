package com.onemind.app

import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.DerivedData
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.MemorySummary
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.ui.feed.MemoryDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a card calls a Memory, and what it says about it.
 *
 * These were one private function inside `MemoryCard` that joined both into
 * `"title — summary"`. That works for a single line of body text and for nothing else:
 * the redesign draws the title as its own element in its own face, and three screens need
 * the same derivation. Pulling it out also made it testable, which it had never been.
 */
class MemoryDisplayTest {

    private fun memory(
        text: String? = null,
        summary: MemorySummary? = null
    ) = Memory(
        id = 1L,
        contentBlocks = if (text == null) emptyList() else listOf(
            ContentBlock(type = ContentType.TEXT, content = text)
        ),
        derived = if (summary == null) DerivedData.EMPTY else DerivedData(summary = summary)
    )

    private fun summary(
        title: String?,
        text: String,
        status: StageStatus = StageStatus.SUCCESS
    ) = MemorySummary(
        memoryId = 1L,
        summaryText = text,
        status = status,
        title = title
    )

    @Test
    fun theModelsTitleWinsWhenThereIsOne() {
        val m = memory(text = "some long body text", summary = summary("Dentist booking", "…"))

        assertEquals("Dentist booking", MemoryDisplay.title(m))
    }

    @Test
    fun aFailedSummarysTitleIsNotUsed() {
        // A stage that did not succeed may still have written a row. Its title describes
        // whatever it managed before failing, which is not something to put in 17 sp
        // Outfit at the top of a card.
        val m = memory(
            text = "Booked the dentist for Thursday",
            summary = summary("garbage", "", status = StageStatus.FAILED)
        )

        assertEquals("Booked the dentist for Thursday", MemoryDisplay.title(m))
    }

    @Test
    fun withNoTitleTheUsersOwnFirstLineIsTheTitle() {
        val m = memory(text = "Booked the dentist\nfor Thursday at 3", summary = null)

        // The first line, not the whole block: the rest is the snippet's job, and a
        // three-line title in a two-column grid pushes everything else off the card.
        assertEquals("Booked the dentist", MemoryDisplay.title(m))
    }

    @Test
    fun aVeryLongFirstLineIsTruncatedRatherThanClamped() {
        val long = "a".repeat(200)
        val m = memory(text = long)

        val title = MemoryDisplay.title(m)
        assertTrue("title was ${title.length} chars", title.length <= 61)
        assertTrue("truncation should be visible", title.endsWith("…"))
    }

    @Test
    fun aMemoryWithNothingReadableStillHasATitle() {
        val m = memory(text = null, summary = null)

        // An image-only Memory before OCR runs. A blank title would leave a card that
        // looks broken rather than one that looks new.
        assertEquals(MemoryDisplay.FALLBACK_TITLE, MemoryDisplay.title(m))
    }

    @Test
    fun blankTextIsTreatedAsNoText() {
        val m = memory(text = "   \n  ")

        assertEquals(MemoryDisplay.FALLBACK_TITLE, MemoryDisplay.title(m))
    }

    @Test
    fun theSnippetIsTheSummaryWhenThereIsOne() {
        val m = memory(text = "raw body", summary = summary("A title", "What this is about."))

        assertEquals("What this is about.", MemoryDisplay.snippet(m))
    }

    @Test
    fun theSnippetFallsBackToTheUsersText() {
        // Multi-line, deliberately. The plan wrote this fixture as a single line with no
        // summary — which is structurally the same input as
        // `theSnippetIsEmptyRatherThanRepeatingTheTitle` below, asserting the opposite
        // result, so the two could not both pass whatever the implementation did. The
        // no-repeat rule is the one the design states and the one the plan calls
        // load-bearing, so this test moves to the case where a fallback genuinely has
        // something left to say: the title took the first line, and the rest is still
        // unsaid.
        val m = memory(text = "Booked the dentist\nfor Thursday at 3", summary = null)

        assertEquals("Booked the dentist", MemoryDisplay.title(m))
        assertEquals("Booked the dentist\nfor Thursday at 3", MemoryDisplay.snippet(m))
    }

    @Test
    fun theSnippetIsEmptyRatherThanRepeatingTheTitle() {
        // Title came from the user's only line of text, so there is nothing left to say.
        // Repeating it is what the old joined string effectively did.
        val m = memory(text = "Booked the dentist", summary = null)

        assertEquals("Booked the dentist", MemoryDisplay.title(m))
        assertEquals("", MemoryDisplay.snippet(m))
    }
}
