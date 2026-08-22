package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Groups memories by date relative to today.
 *
 * Uses the device's local timezone so "Today" means the user's today, not UTC.
 * Computed once per emission from the repository, not per recomposition.
 */
object DateGrouping {

    enum class DateGroup(val label: String) {
        TODAY("Today"),
        YESTERDAY("Yesterday"),
        THIS_WEEK("This Week"),
        THIS_MONTH("This Month"),
        OLDER("Older")
    }

    /**
     * Group memories into date sections.
     *
     * Returns only groups that have at least one memory — no empty headers.
     * Within each group, memories are already in most-recent-first order
     * (the repository returns them that way).
     */
    fun group(
        memories: List<Memory>,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ): List<Pair<DateGroup, List<Memory>>> {
        val today = now.atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val monthStart = today.withDayOfMonth(1)

        val grouped = memories.groupBy { memory ->
            val memoryDate = memory.createdAt.atZone(zone).toLocalDate()
            classify(memoryDate, today, yesterday, weekStart, monthStart)
        }

        // Return in fixed order, skipping empty groups.
        return DateGroup.entries
            .filter { it in grouped }
            .map { group -> group to grouped.getValue(group) }
    }

    private fun classify(
        date: LocalDate,
        today: LocalDate,
        yesterday: LocalDate,
        weekStart: LocalDate,
        monthStart: LocalDate
    ): DateGroup = when {
        date == today -> DateGroup.TODAY
        date == yesterday -> DateGroup.YESTERDAY
        date >= weekStart -> DateGroup.THIS_WEEK
        date >= monthStart -> DateGroup.THIS_MONTH
        else -> DateGroup.OLDER
    }
}
