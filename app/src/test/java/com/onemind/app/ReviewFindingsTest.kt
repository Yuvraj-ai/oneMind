package com.onemind.app

import com.onemind.app.domain.search.FtsQuery
import com.onemind.app.domain.search.KeywordScoring
import com.onemind.app.domain.search.TemporalExpressionParser
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Reproductions for the pre-release review findings, written before fixing so each
 * one is confirmed rather than taken on trust.
 */
class ReviewFindingsTest {

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 8, 19, 14, 0, 0, 0, zone).toInstant()
    private val parser = TemporalExpressionParser()

    // W2: bare month names become hard date filters.

    @Test
    fun `W2 - the modal verb may must not be read as the month of May`() {
        val parsed = parser.parse("the ramen recipe I may have saved", zone, now)

        assertNull(
            "'may' as a modal verb became a date filter: ${parsed?.matchedText}",
            parsed
        )
    }

    @Test
    fun `W2 - march as a verb must not be read as the month`() {
        assertNull(parser.parse("protest march photos", zone, now))
    }

    @Test
    fun `W2 - in May with the preposition is still a month`() {
        assertNotNull(parser.parse("photos from in May", zone, now))
    }

    @Test
    fun `W2 - a month name with a year context is still a month`() {
        assertNotNull(parser.parse("what did I save in July", zone, now))
    }

    // D4: offsets computed on a lowercased copy, applied to the original.

    @Test
    fun `D4 - a query containing a dotted capital I must not crash`() {
        // U+0130 lowercases to two characters, shifting every subsequent offset.
        val parsed = parser.parse("\u0130STANBUL trip last week", zone, now)

        assertNotNull(parsed)
        assertEquals(
            "matchedText was sliced with shifted offsets",
            "last week",
            parsed!!.matchedText
        )
    }

    @Test
    fun `D4 - several dotted capitals do not shift the match`() {
        val parsed = parser.parse("\u0130\u0130\u0130 notes from yesterday", zone, now)

        assertEquals("yesterday", parsed!!.matchedText)
    }

    // W3: FTS tokenises on dots, FtsQuery does not.

    @Test
    fun `W3 - a term after a dot must score above zero`() {
        // SQLite's simple tokeniser splits "node.js" into "node" and "js", so FTS
        // returns this row for the query "js". Our scorer must agree, or the row is
        // found by the index and then discarded by our own arithmetic.
        val score = KeywordScoring.score("a guide to node.js streams", listOf("js"))

        assertTrue("query 'js' scored $score against a document containing node.js", score > 0.0)
    }

    @Test
    fun `W3 - a file extension is searchable`() {
        assertTrue(KeywordScoring.score("diagram.png attached", listOf("png")) > 0.0)
    }

    @Test
    fun `W3 - a domain is still searchable as a whole`() {
        assertTrue(KeywordScoring.score("see github.com for more", listOf("github")) > 0.0)
        assertTrue(KeywordScoring.score("see github.com for more", listOf("com")) > 0.0)
    }

    @Test
    fun `W3 - tokenising splits on dots so scoring matches FTS`() {
        assertTrue(FtsQuery.documentTerms("node.js").contains("node"))
        assertTrue(FtsQuery.documentTerms("node.js").contains("js"))
    }
}
