package com.onemind.app.domain.search

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

/**
 * Finds time expressions in a query and converts them to absolute ranges.
 *
 * Small in surface, large in edge cases, which is why it is separate from the rest
 * of query understanding. "In July" during January means *last* July; "last week"
 * on the 2nd of January crosses a year boundary; a month name has to survive being
 * typed in any case. Each of those has a wrong answer that is easy to ship and
 * hard to notice, because the search still returns *something*.
 *
 * Deliberately has no dependency on an LLM. Time expressions are a closed,
 * well-understood set, and resolving them by rule is faster, free, works offline,
 * and cannot hallucinate a date. #27 uses this rather than replacing it.
 *
 * All arithmetic happens in the device's local timezone, matching the Phase 3 date
 * grouping decision: "yesterday" means the user's yesterday, not UTC's.
 */
class TemporalExpressionParser @Inject constructor() {

    /**
     * Parse the first time expression in [query], or null if there is none.
     *
     * Null is meaningfully different from an empty range: it means "no temporal
     * constraint", so the caller searches all of time rather than nothing.
     *
     * Ambiguous input returns null rather than a guess. A wrong date filter hides
     * the Memory the user is looking for and gives them no clue why, which is worse
     * than applying no filter at all.
     */
    fun parse(
        query: String,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ): TemporalExpression? {
        val today = now.atZone(zone).toLocalDate()

        // Matching is done case-insensitively against the ORIGINAL string rather
        // than against a lowercased copy. Lowercasing is not length-preserving —
        // U+0130 (dotted capital I) becomes two characters — so an offset found in
        // the copy can point past the end of the original. That is a
        // StringIndexOutOfBoundsException on a keystroke for anyone typing Turkish.
        return parseNumberedDaysAgo(query, today, zone)
            ?: parseNumberedWeeksAgo(query, today, zone)
            ?: parseNumberedMonthsAgo(query, today, zone)
            ?: parseNamedRelative(query, today, zone)
            ?: parseMonthName(query, today, zone)
    }

    // --- "N days ago", including spelled-out numbers -----------------------

    private fun parseNumberedDaysAgo(
        query: String,
        today: LocalDate,
        zone: ZoneId
    ): TemporalExpression? {
        val match = countRegex("days?").find(query) ?: return null
        val count = numberOf(match.groupValues[1]) ?: return null
        val day = today.minusDays(count.toLong())
        return dayRange(day, zone, match.value)
    }

    private fun parseNumberedWeeksAgo(
        query: String,
        today: LocalDate,
        zone: ZoneId
    ): TemporalExpression? {
        val match = countRegex("weeks?").find(query) ?: return null
        val count = numberOf(match.groupValues[1]) ?: return null
        // The whole of that week, not the single day N*7 days back: someone saying
        // "two weeks ago" is pointing at a period, not a date.
        val target = today.minusWeeks(count.toLong())
        val weekStart = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return range(weekStart, weekStart.plusWeeks(1), zone, match.value)
    }

    private fun parseNumberedMonthsAgo(
        query: String,
        today: LocalDate,
        zone: ZoneId
    ): TemporalExpression? {
        val match = countRegex("months?").find(query) ?: return null
        val count = numberOf(match.groupValues[1]) ?: return null
        val target = today.minusMonths(count.toLong())
        val monthStart = target.withDayOfMonth(1)
        return range(monthStart, monthStart.plusMonths(1), zone, match.value)
    }

    private fun countRegex(unit: String) = Regex(
        """\b(\d+|${NUMBER_WORDS.keys.joinToString("|")})\s+$unit\s+ago\b""",
        RegexOption.IGNORE_CASE
    )

    // --- named relative expressions ----------------------------------------

    private fun parseNamedRelative(
        query: String,
        today: LocalDate,
        zone: ZoneId
    ): TemporalExpression? {
        // Longest first: "the day before yesterday" contains "yesterday", and "last
        // week" would otherwise be found by a "week" rule.
        NAMED.forEach { (phrase, resolver) ->
            val match = Regex(Regex.escape(phrase), RegexOption.IGNORE_CASE).find(query)
            if (match != null) {
                val (start, endExclusive) = resolver(today)
                return range(start, endExclusive, zone, match.value)
            }
        }
        return null
    }

    // --- "in July" ----------------------------------------------------------

    /**
     * "in July", but never a bare "may".
     *
     * The preposition is **required**, and that is the whole point of the rule.
     * Several month names are ordinary English words — "may" and "march" most
     * obviously — so an optional preposition turns "the recipe I may have saved"
     * into a filter for the whole of last May. Since [SearchOrchestrator] applies
     * temporal constraints as hard filters, that discards nearly every Memory and
     * reports "No memories found" with nothing to explain it.
     *
     * Requiring "in", "during", or "back in" costs the user one word and removes an
     * entire class of silently wrong result.
     */
    private fun parseMonthName(
        query: String,
        today: LocalDate,
        zone: ZoneId
    ): TemporalExpression? {
        val match = Regex(
            """\b(?:in|during|back\s+in)\s+(${MONTHS.keys.joinToString("|")})\b""",
            RegexOption.IGNORE_CASE
        ).find(query) ?: return null
        val month = MONTHS[match.groupValues[1].lowercase()] ?: return null

        // The most recent occurrence of that month. In January, "in July" means last
        // July — reading it as this year's July would point at a future range and
        // return nothing, with no hint as to why.
        var year = today.year
        if (month.value > today.monthValue) year -= 1

        val monthStart = LocalDate.of(year, month, 1)
        return range(monthStart, monthStart.plusMonths(1), zone, match.value)
    }

    // --- range construction -------------------------------------------------

    private fun dayRange(day: LocalDate, zone: ZoneId, matched: String) =
        range(day, day.plusDays(1), zone, matched)

    private fun range(
        start: LocalDate,
        endExclusive: LocalDate,
        zone: ZoneId,
        matched: String
    ) = TemporalExpression(
        start = start.atStartOfDay(zone).toInstant(),
        endExclusive = endExclusive.atStartOfDay(zone).toInstant(),
        matchedText = matched
    )

    private fun numberOf(token: String): Int? =
        token.toIntOrNull() ?: NUMBER_WORDS[token]

    companion object {
        /** Spelled-out counts, which people type as often as digits. */
        private val NUMBER_WORDS = mapOf(
            "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
            "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10
        )

        private val MONTHS = mapOf(
            "january" to Month.JANUARY, "february" to Month.FEBRUARY,
            "march" to Month.MARCH, "april" to Month.APRIL,
            "may" to Month.MAY, "june" to Month.JUNE,
            "july" to Month.JULY, "august" to Month.AUGUST,
            "september" to Month.SEPTEMBER, "october" to Month.OCTOBER,
            "november" to Month.NOVEMBER, "december" to Month.DECEMBER
        )

        /**
         * Named expressions, **ordered longest-first**.
         *
         * Order is load-bearing, not cosmetic: matching is by substring, so
         * "yesterday" appears inside "the day before yesterday" and "week" inside
         * "last week". A shorter phrase placed first would swallow the longer one
         * and silently produce the wrong range.
         */
        private val NAMED: List<Pair<String, (LocalDate) -> Pair<LocalDate, LocalDate>>> = listOf(
            "the day before yesterday" to { d -> d.minusDays(2) to d.minusDays(1) },
            "day before yesterday" to { d -> d.minusDays(2) to d.minusDays(1) },

            "last week" to { d ->
                // The previous Monday-to-Sunday block, not "the last seven days".
                // Someone saying "last week" on a Tuesday does not mean to include
                // this Monday.
                val thisWeekStart = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                thisWeekStart.minusWeeks(1) to thisWeekStart
            },
            "this week" to { d ->
                val start = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to start.plusWeeks(1)
            },
            "past week" to { d -> d.minusDays(7) to d.plusDays(1) },
            "last seven days" to { d -> d.minusDays(7) to d.plusDays(1) },

            "last month" to { d ->
                val start = d.withDayOfMonth(1).minusMonths(1)
                start to start.plusMonths(1)
            },
            "this month" to { d ->
                val start = d.withDayOfMonth(1)
                start to start.plusMonths(1)
            },
            "past month" to { d -> d.minusMonths(1) to d.plusDays(1) },

            "last year" to { d ->
                val start = LocalDate.of(d.year - 1, 1, 1)
                start to start.plusYears(1)
            },
            "this year" to { d ->
                val start = LocalDate.of(d.year, 1, 1)
                start to start.plusYears(1)
            },

            "yesterday" to { d -> d.minusDays(1) to d },
            "today" to { d -> d to d.plusDays(1) }
        )
    }
}
