package com.onemind.app

import com.onemind.app.domain.model.Memory
import com.onemind.app.ui.feed.DateGrouping
import com.onemind.app.ui.feed.DateGrouping.DateGroup
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Date grouping logic for the timeline view.
 *
 * Uses a fixed "now" so tests are deterministic regardless of when CI runs.
 */
class DateGroupingTest {

    private val zone = ZoneId.of("America/New_York")
    // Wednesday 2026-08-19 at 14:00 Eastern.
    private val now = ZonedDateTime.of(2026, 8, 19, 14, 0, 0, 0, zone).toInstant()

    private fun memory(date: LocalDate): Memory {
        val instant = date.atStartOfDay(zone).plusHours(10).toInstant()
        return Memory(id = date.toEpochDay(), createdAt = instant)
    }

    @Test
    fun `a memory created today groups under TODAY`() {
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2026, 8, 19))),
            zone, now
        )

        assertEquals(1, groups.size)
        assertEquals(DateGroup.TODAY, groups[0].first)
    }

    @Test
    fun `a memory created yesterday groups under YESTERDAY`() {
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2026, 8, 18))),
            zone, now
        )

        assertEquals(DateGroup.YESTERDAY, groups[0].first)
    }

    @Test
    fun `a memory earlier this week groups under THIS_WEEK`() {
        // 2026-08-19 is Wednesday; Monday is 2026-08-17.
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2026, 8, 17))),
            zone, now
        )

        assertEquals(DateGroup.THIS_WEEK, groups[0].first)
    }

    @Test
    fun `a memory this month but before this week groups under THIS_MONTH`() {
        // 2026-08-10 is well before the week that starts on 2026-08-17.
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2026, 8, 10))),
            zone, now
        )

        assertEquals(DateGroup.THIS_MONTH, groups[0].first)
    }

    @Test
    fun `a memory last month groups under OLDER`() {
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2026, 7, 15))),
            zone, now
        )

        assertEquals(DateGroup.OLDER, groups[0].first)
    }

    @Test
    fun `a memory from last year groups under OLDER`() {
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2025, 3, 1))),
            zone, now
        )

        assertEquals(DateGroup.OLDER, groups[0].first)
    }

    @Test
    fun `empty sections are not returned`() {
        val groups = DateGrouping.group(
            listOf(memory(LocalDate.of(2026, 8, 19))),
            zone, now
        )

        assertEquals(1, groups.size)
    }

    @Test
    fun `groups are returned in fixed order regardless of memory order`() {
        val memories = listOf(
            memory(LocalDate.of(2026, 7, 1)),   // OLDER
            memory(LocalDate.of(2026, 8, 19)),  // TODAY
            memory(LocalDate.of(2026, 8, 18)),  // YESTERDAY
            memory(LocalDate.of(2026, 8, 10))   // THIS_MONTH
        )

        val groups = DateGrouping.group(memories, zone, now)

        assertEquals(
            listOf(DateGroup.TODAY, DateGroup.YESTERDAY, DateGroup.THIS_MONTH, DateGroup.OLDER),
            groups.map { it.first }
        )
    }

    @Test
    fun `multiple memories in one group stay together`() {
        val memories = listOf(
            Memory(id = 1, createdAt = now.minusSeconds(60)),
            Memory(id = 2, createdAt = now.minusSeconds(3600))
        )

        val groups = DateGrouping.group(memories, zone, now)

        assertEquals(1, groups.size)
        assertEquals(DateGroup.TODAY, groups[0].first)
        assertEquals(2, groups[0].second.size)
    }

    @Test
    fun `midnight boundary is handled correctly`() {
        // A memory created at 23:59 today is still TODAY.
        val lateToday = ZonedDateTime.of(2026, 8, 19, 23, 59, 0, 0, zone).toInstant()
        val groups = DateGrouping.group(
            listOf(Memory(id = 1, createdAt = lateToday)),
            zone, now
        )

        assertEquals(DateGroup.TODAY, groups[0].first)
    }

    @Test
    fun `timezone matters — same UTC instant can be different days`() {
        // 2026-08-19 01:00 UTC is still 2026-08-18 21:00 Eastern (yesterday).
        val utcEarlyMorning = Instant.parse("2026-08-19T01:00:00Z")
        val groups = DateGrouping.group(
            listOf(Memory(id = 1, createdAt = utcEarlyMorning)),
            zone, now
        )

        assertEquals(DateGroup.YESTERDAY, groups[0].first)
    }

    @Test
    fun `empty list returns empty`() {
        assertTrue(DateGrouping.group(emptyList(), zone, now).isEmpty())
    }

    @Test
    fun `labels are human-readable`() {
        assertEquals("Today", DateGroup.TODAY.label)
        assertEquals("Yesterday", DateGroup.YESTERDAY.label)
        assertEquals("This Week", DateGroup.THIS_WEEK.label)
        assertEquals("This Month", DateGroup.THIS_MONTH.label)
        assertEquals("Older", DateGroup.OLDER.label)
    }
}
