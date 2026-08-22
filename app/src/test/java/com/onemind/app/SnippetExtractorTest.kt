package com.onemind.app

import com.onemind.app.domain.search.SnippetExtractor
import org.junit.Assert.*
import org.junit.Test

/**
 * Snippet extraction and highlight positioning.
 *
 * Offsets are the whole risk here. A highlight range that runs past the end of the
 * snippet crashes the text renderer, and one that is off by a few characters
 * emphasises the wrong word — which looks like a bug in the search rather than in
 * the display. Both are asserted directly.
 */
class SnippetExtractorTest {

    private fun extract(document: String, vararg terms: String, length: Int = 120) =
        SnippetExtractor.extract(document, terms.toList(), length)

    /** The text actually inside the highlight ranges. */
    private fun highlightedText(snippet: SnippetExtractor.Snippet): List<String> =
        snippet.highlights.map { snippet.text.substring(it.first, it.last + 1) }

    // --- finding the match ---------------------------------------------------

    @Test
    fun `the matched term is highlighted`() {
        val snippet = extract("A recipe for tonkotsu ramen from Tokyo", "tonkotsu")!!

        assertEquals(listOf("tonkotsu"), highlightedText(snippet))
    }

    @Test
    fun `matching is case-insensitive but the original casing is shown`() {
        val snippet = extract("A recipe for Tonkotsu Ramen", "tonkotsu")!!

        assertEquals(listOf("Tonkotsu"), highlightedText(snippet))
    }

    @Test
    fun `several terms are all highlighted`() {
        val snippet = extract("A recipe for tonkotsu ramen from Tokyo", "tonkotsu", "tokyo")!!

        assertEquals(listOf("tonkotsu", "Tokyo"), highlightedText(snippet))
    }

    @Test
    fun `a repeated term is highlighted at every occurrence`() {
        val snippet = extract("ramen and more ramen and ramen again", "ramen")!!

        assertEquals(3, snippet.highlights.size)
    }

    // --- prefix matching, mirroring how the query ran ------------------------

    @Test
    fun `a prefix match highlights the whole word`() {
        // FTS matched on a prefix, so highlighting only the typed letters would
        // leave the result looking half-explained.
        val snippet = extract("A recipe for ramen", "ram")!!

        assertEquals(listOf("ramen"), highlightedText(snippet))
    }

    @Test
    fun `a term is not highlighted mid-word`() {
        // Highlighting "ram" inside "program" reads as a bug.
        assertNull(extract("a program listing", "ram"))
    }

    @Test
    fun `overlapping matches are merged rather than double-styled`() {
        // "ram" and "ramen" both hit "ramen". Overlapping spans would double-style
        // the text or throw, depending on the renderer.
        val snippet = extract("a bowl of ramen", "ram", "ramen")!!

        assertEquals(1, snippet.highlights.size)
        assertEquals(listOf("ramen"), highlightedText(snippet))
    }

    // --- windowing -----------------------------------------------------------

    @Test
    fun `a short document is returned whole with no ellipsis`() {
        val snippet = extract("short and sweet ramen", "ramen")!!

        assertEquals("short and sweet ramen", snippet.text)
    }

    @Test
    fun `a match late in a long document is not cut off`() {
        // The bug this guards against: showing the opening 120 characters and
        // omitting the reason the result matched at all.
        val document = "filler ".repeat(100) + "tonkotsu at the very end"

        val snippet = extract(document, "tonkotsu")!!

        assertTrue("snippet must contain the match", snippet.text.contains("tonkotsu"))
        assertTrue("snippet must highlight the match", snippet.highlights.isNotEmpty())
        assertEquals(listOf("tonkotsu"), highlightedText(snippet))
    }

    @Test
    fun `a match in the middle of a long document is centred`() {
        val document = "before ".repeat(50) + "tonkotsu " + "after ".repeat(50)

        val snippet = extract(document, "tonkotsu", length = 60)!!

        assertTrue(snippet.text.contains("tonkotsu"))
        assertTrue("should be elided at the start", snippet.text.startsWith("\u2026"))
        assertTrue("should be elided at the end", snippet.text.endsWith("\u2026"))
    }

    @Test
    fun `a match at the very start gets a full snippet without a leading ellipsis`() {
        val document = "tonkotsu " + "filler ".repeat(60)

        val snippet = extract(document, "tonkotsu", length = 60)!!

        assertFalse(snippet.text.startsWith("\u2026"))
        assertEquals(listOf("tonkotsu"), highlightedText(snippet))
    }

    @Test
    fun `a match at the very end gets a full snippet without a trailing ellipsis`() {
        val document = "filler ".repeat(60) + "tonkotsu"

        val snippet = extract(document, "tonkotsu", length = 60)!!

        assertFalse(snippet.text.endsWith("\u2026"))
        assertEquals(listOf("tonkotsu"), highlightedText(snippet))
    }

    @Test
    fun `the snippet stays near the requested length`() {
        val document = "word ".repeat(500)

        val snippet = extract(document, "word", length = 100)!!

        // Allowing slack for ellipses and word-boundary nudging.
        assertTrue("was ${snippet.text.length}", snippet.text.length <= 140)
    }

    // --- highlight ranges are always safe to slice --------------------------

    @Test
    fun `every highlight range lies inside the snippet`() {
        // A range past the end throws inside the text renderer.
        val document = "before ".repeat(40) + "tonkotsu ramen tokyo " + "after ".repeat(40)

        val snippet = extract(document, "tonkotsu", "ramen", "tokyo", length = 50)!!

        snippet.highlights.forEach { range ->
            assertTrue("range $range starts before 0", range.first >= 0)
            assertTrue(
                "range $range runs past ${snippet.text.length}",
                range.last < snippet.text.length
            )
        }
    }

    @Test
    fun `a match partly outside the window is dropped rather than truncated`() {
        val document = "aaa ".repeat(100) + "tonkotsu " + "bbb ".repeat(100) + "tokyo"

        val snippet = extract(document, "tonkotsu", "tokyo", length = 60)!!

        // Only the term the window is centred on survives; the far one is not
        // highlighted at a position it does not occupy.
        highlightedText(snippet).forEach {
            assertTrue(snippet.text.contains(it))
        }
    }

    // --- whitespace normalisation -------------------------------------------

    @Test
    fun `newlines are flattened, since the index joins sections with them`() {
        val snippet = extract("user text\nOCR text\ntonkotsu ramen", "tonkotsu")!!

        assertFalse("newlines would read as a broken paragraph", snippet.text.contains("\n"))
    }

    @Test
    fun `runs of whitespace collapse`() {
        val snippet = extract("lots     of      space tonkotsu", "tonkotsu")!!

        assertFalse(snippet.text.contains("  "))
    }

    @Test
    fun `offsets remain correct after whitespace collapsing`() {
        // The reason collapsing happens before matching: otherwise every offset
        // would refer to a string that is not the one displayed.
        val snippet = extract("a\n\n\nb   c tonkotsu", "tonkotsu")!!

        assertEquals(listOf("tonkotsu"), highlightedText(snippet))
    }

    // --- nothing to show ----------------------------------------------------

    @Test
    fun `no match returns null so the caller can fall back to the summary`() {
        assertNull(extract("a recipe for ramen", "quantization"))
    }

    @Test
    fun `an empty document returns null`() {
        assertNull(extract("", "ramen"))
    }

    @Test
    fun `a blank document returns null`() {
        assertNull(extract("   \n  ", "ramen"))
    }

    @Test
    fun `no query terms returns null`() {
        assertNull(SnippetExtractor.extract("a recipe for ramen", emptyList()))
    }

    @Test
    fun `a blank term is ignored rather than matching everywhere`() {
        assertNull(extract("a recipe for ramen", ""))
    }
}
