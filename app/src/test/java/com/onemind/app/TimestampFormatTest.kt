package com.onemind.app

import com.onemind.app.ui.feed.formatCardDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class TimestampFormatTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun cardDateSameDayFormatsAsTime() {
        val now = ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, zone).toInstant()
        val upload = ZonedDateTime.of(2026, 9, 15, 9, 41, 0, 0, zone).toInstant()

        val formatted = formatCardDate(instant = upload, now = now, zone = zone)
        assertEquals("9:41 AM", formatted)
    }

    @Test
    fun cardDateYesterdayFormatsAsYesterday() {
        val now = ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, zone).toInstant()
        val upload = ZonedDateTime.of(2026, 9, 14, 18, 0, 0, 0, zone).toInstant()

        val formatted = formatCardDate(instant = upload, now = now, zone = zone)
        assertEquals("Yesterday", formatted)
    }

    @Test
    fun cardDateWithinWeekFormatsAsDayOfWeek() {
        val now = ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, zone).toInstant() // Tuesday
        val upload = ZonedDateTime.of(2026, 9, 11, 10, 0, 0, 0, zone).toInstant() // Friday (4 days ago)

        val formatted = formatCardDate(instant = upload, now = now, zone = zone)
        assertEquals("Friday", formatted)
    }

    @Test
    fun cardDateSameYearFormatsAsMonthAndDay() {
        val now = ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, zone).toInstant()
        val upload = ZonedDateTime.of(2026, 7, 4, 10, 0, 0, 0, zone).toInstant()

        val formatted = formatCardDate(instant = upload, now = now, zone = zone)
        assertEquals("Jul 4", formatted)
    }

    @Test
    fun cardDatePreviousYearFormatsWithYear() {
        val now = ZonedDateTime.of(2026, 9, 15, 14, 30, 0, 0, zone).toInstant()
        val upload = ZonedDateTime.of(2025, 12, 25, 10, 0, 0, 0, zone).toInstant()

        val formatted = formatCardDate(instant = upload, now = now, zone = zone)
        assertEquals("Dec 25, 2025", formatted)
    }
}
