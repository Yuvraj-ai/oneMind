package com.onemind.app

import com.onemind.app.domain.search.TemporalExpressionParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Temporal expression parsing.
 *
 * `now` is fixed throughout, so results do not depend on when CI runs. The
 * interesting cases are the boundaries — year rollover, "in July" during January,
 * leap day — because each has a wrong answer that still returns *something*, which
 * is exactly the kind of bug that survives casual testing.
 */
class TemporalExpressionParserTest {

    private val parser = TemporalExpressionParser()
    private val zone = ZoneId.of("America/New_York")

    /** Wednesday, 19 August 2026, 14:00 Eastern. */
    private val now = ZonedDateTime.of(2026, 8, 19, 14, 0, 0, 0, zone).toInstant()

    private fun parse(query: String, at: java.time.Instant = now) =
        parser.parse(query, zone, at)

    /** The local date a range starts on. */
    private fun startDate(query: String, at: java.time.Instant = now): LocalDate? =
        parse(query, at)?.start?.atZone(zone)?.toLocalDate()

    /** The last date *included* in a range, since the bound is exclusive. */
    private fun lastIncludedDate(query: String, at: java.time.Instant = now): LocalDate? =
        parse(query, at)?.endExclusive?.atZone(zone)?.toLocalDate()?.minusDays(1)

    // --- single days --------------------------------------------------------

    @Test
    fun `today resolves to today`() {
        assertEquals(LocalDate.of(2026, 8, 19), startDate("what did I save today"))
        assertEquals(LocalDate.of(2026, 8, 19), lastIncludedDate("what did I save today"))
    }

    @Test
    fun `yesterday resolves to yesterday`() {
        assertEquals(LocalDate.of(2026, 8, 18), startDate("what did I save yesterday"))
        assertEquals(LocalDate.of(2026, 8, 18), lastIncludedDate("what did I save yesterday"))
    }

    @Test
    fun `the day before yesterday is not swallowed by yesterday`() {
        // Substring matching means "yesterday" occurs inside this phrase. Rule
        // order is what stops the shorter match winning.
        assertEquals(LocalDate.of(2026, 8, 17), startDate("the day before yesterday"))
    }

    @Test
    fun `two days ago resolves to that single day`() {
        assertEquals(LocalDate.of(2026, 8, 17), startDate("that thing from two days ago"))
        assertEquals(LocalDate.of(2026, 8, 17), lastIncludedDate("that thing from two days ago"))
    }

    @Test
    fun `a digit count works as well as a word`() {
        assertEquals(startDate("two days ago"), startDate("2 days ago"))
    }

    @Test
    fun `three days ago works`() {
        assertEquals(LocalDate.of(2026, 8, 16), startDate("3 days ago"))
    }

    @Test
    fun `one day ago is singular and still parses`() {
        assertEquals(LocalDate.of(2026, 8, 18), startDate("1 day ago"))
    }

    // --- weeks --------------------------------------------------------------

    @Test
    fun `last week is the previous Monday to Sunday block`() {
        // Not "the last seven days" — someone saying this on a Wednesday does not
        // mean to include Monday of the current week.
        assertEquals(LocalDate.of(2026, 8, 10), startDate("AI stuff from last week"))
        assertEquals(LocalDate.of(2026, 8, 16), lastIncludedDate("AI stuff from last week"))
    }

    @Test
    fun `this week starts on Monday`() {
        assertEquals(LocalDate.of(2026, 8, 17), startDate("saved this week"))
    }

    @Test
    fun `last week is not confused by the word week alone`() {
        assertNotNull(parse("last week"))
        assertEquals(LocalDate.of(2026, 8, 10), startDate("last week"))
    }

    @Test
    fun `two weeks ago covers that whole week`() {
        assertEquals(LocalDate.of(2026, 8, 3), startDate("two weeks ago"))
        assertEquals(LocalDate.of(2026, 8, 9), lastIncludedDate("two weeks ago"))
    }

    @Test
    fun `past week is a rolling seven days including today`() {
        assertEquals(LocalDate.of(2026, 8, 12), startDate("past week"))
        assertEquals(LocalDate.of(2026, 8, 19), lastIncludedDate("past week"))
    }

    // --- months -------------------------------------------------------------

    @Test
    fun `last month is the whole previous calendar month`() {
        assertEquals(LocalDate.of(2026, 7, 1), startDate("last month"))
        assertEquals(LocalDate.of(2026, 7, 31), lastIncludedDate("last month"))
    }

    @Test
    fun `this month starts on the first`() {
        assertEquals(LocalDate.of(2026, 8, 1), startDate("this month"))
    }

    @Test
    fun `two months ago covers that whole month`() {
        assertEquals(LocalDate.of(2026, 6, 1), startDate("two months ago"))
        assertEquals(LocalDate.of(2026, 6, 30), lastIncludedDate("two months ago"))
    }

    // --- month names --------------------------------------------------------

    @Test
    fun `in July resolves to July of this year when July has passed`() {
        assertEquals(LocalDate.of(2026, 7, 1), startDate("what did I save in July about AI"))
        assertEquals(LocalDate.of(2026, 7, 31), lastIncludedDate("in July"))
    }

    @Test
    fun `in December resolves to LAST December, not a future one`() {
        // The critical case. Reading it as this year's December would produce a
        // range in the future and return nothing, with no hint as to why.
        assertEquals(LocalDate.of(2025, 12, 1), startDate("stuff from December"))
    }

    @Test
    fun `in January during August resolves to this year's January`() {
        assertEquals(LocalDate.of(2026, 1, 1), startDate("in January"))
    }

    @Test
    fun `the current month resolves to this year`() {
        assertEquals(LocalDate.of(2026, 8, 1), startDate("in August"))
    }

    @Test
    fun `a month name is case-insensitive`() {
        assertEquals(startDate("in july"), startDate("in JULY"))
        assertEquals(startDate("in july"), startDate("In July"))
    }

    @Test
    fun `the word in is optional`() {
        assertEquals(startDate("in July"), startDate("July"))
    }

    @Test
    fun `February in a non-leap year has 28 days`() {
        assertEquals(LocalDate.of(2026, 2, 28), lastIncludedDate("in February"))
    }

    @Test
    fun `February in a leap year has 29 days`() {
        // 2028 is a leap year. Viewed from March 2028, "in February" is that
        // February.
        val inMarch2028 = ZonedDateTime.of(2028, 3, 15, 12, 0, 0, 0, zone).toInstant()

        assertEquals(
            LocalDate.of(2028, 2, 29),
            lastIncludedDate("in February", at = inMarch2028)
        )
    }

    // --- year boundaries ----------------------------------------------------

    @Test
    fun `last week crossing a year boundary lands in the previous year`() {
        // Friday 2 January 2026. "Last week" is the week of 22-28 December 2025.
        val earlyJanuary = ZonedDateTime.of(2026, 1, 2, 10, 0, 0, 0, zone).toInstant()

        assertEquals(LocalDate.of(2025, 12, 22), startDate("last week", at = earlyJanuary))
        assertEquals(LocalDate.of(2025, 12, 28), lastIncludedDate("last week", at = earlyJanuary))
    }

    @Test
    fun `yesterday crossing a year boundary lands in the previous year`() {
        val newYearsDay = ZonedDateTime.of(2026, 1, 1, 10, 0, 0, 0, zone).toInstant()

        assertEquals(LocalDate.of(2025, 12, 31), startDate("yesterday", at = newYearsDay))
    }

    @Test
    fun `last month in January is December of the previous year`() {
        val january = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, zone).toInstant()

        assertEquals(LocalDate.of(2025, 12, 1), startDate("last month", at = january))
    }

    @Test
    fun `last year is the whole previous calendar year`() {
        assertEquals(LocalDate.of(2025, 1, 1), startDate("last year"))
        assertEquals(LocalDate.of(2025, 12, 31), lastIncludedDate("last year"))
    }

    // --- ranges are half-open ------------------------------------------------

    @Test
    fun `consecutive day ranges do not overlap at midnight`() {
        // Exclusive upper bound: yesterday must end exactly where today begins, or a
        // Memory saved at midnight falls into both.
        val yesterday = parse("yesterday")!!
        val today = parse("today")!!

        assertEquals(yesterday.endExclusive, today.start)
    }

    @Test
    fun `a memory at the last instant of a day is inside that day`() {
        val yesterday = parse("yesterday")!!
        val lastMoment = ZonedDateTime.of(2026, 8, 18, 23, 59, 59, 0, zone).toInstant()

        assertTrue(lastMoment in yesterday)
    }

    @Test
    fun `a memory at the first instant of the next day is outside`() {
        val yesterday = parse("yesterday")!!
        val midnight = ZonedDateTime.of(2026, 8, 19, 0, 0, 0, 0, zone).toInstant()

        assertFalse(midnight in yesterday)
    }

    // --- timezone -----------------------------------------------------------

    @Test
    fun `ranges are computed in the device timezone, not UTC`() {
        // Consistent with the Phase 3 grouping decision: "today" is the user's today.
        val tokyo = ZoneId.of("Asia/Tokyo")
        val easternRange = parser.parse("today", zone, now)!!
        val tokyoRange = parser.parse("today", tokyo, now)!!

        assertNotEquals(easternRange.start, tokyoRange.start)
    }

    // --- the matched text, for stripping ------------------------------------

    @Test
    fun `the matched expression is reported so the caller can strip it`() {
        // Embedding "AI stuff from last week" pushes the vector toward the language
        // of asking about time. The temporal part is already a hard filter by then,
        // so the caller removes it and embeds "AI stuff".
        val parsed = parse("AI stuff from last week")!!

        assertEquals("last week", parsed.matchedText)
    }

    @Test
    fun `the matched text preserves the original casing`() {
        val parsed = parse("AI stuff from Last Week")!!

        assertEquals("Last Week", parsed.matchedText)
    }

    @Test
    fun `removing the matched text leaves the searchable remainder`() {
        val query = "AI stuff from last week"
        val parsed = parse(query)!!

        val remainder = query.replace(parsed.matchedText, "").trim()

        // "from" is left dangling, which does not matter: FtsQuery drops it as a
        // stopword, so what reaches the index is "ai". The two pieces compose
        // without either needing to know about the other.
        assertEquals("AI stuff from", remainder)
        assertEquals(
            listOf("ai"),
            com.onemind.app.domain.search.FtsQuery.terms(remainder)
        )
    }

    // --- no temporal expression ---------------------------------------------

    @Test
    fun `a query with no time expression returns null`() {
        // Null means "no constraint", so the caller searches all of time rather
        // than nothing.
        assertNull(parse("ramen recipe"))
    }

    @Test
    fun `an empty query returns null`() {
        assertNull(parse(""))
    }

    @Test
    fun `a bare number is not treated as a date`() {
        assertNull(parse("qwen 3"))
    }

    @Test
    fun `the word day alone is not a temporal expression`() {
        assertNull(parse("a day in the life"))
    }

    // --- combined queries ---------------------------------------------------

    @Test
    fun `a temporal expression is found alongside other content`() {
        val parsed = parse("show me the AI stuff I saved from Chrome last week")

        assertNotNull(parsed)
        assertEquals(LocalDate.of(2026, 8, 10), parsed!!.start.atZone(zone).toLocalDate())
    }

    @Test
    fun `a temporal expression at the start of a query is found`() {
        assertNotNull(parse("yesterday's screenshots"))
    }
}
