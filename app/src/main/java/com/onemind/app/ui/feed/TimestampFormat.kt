package com.onemind.app.ui.feed

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A Memory's timestamp, in the device's locale and timezone.
 *
 * Shared by `BentoCard` and `SearchResultCard` so a Memory's date reads identically wherever
 * it appears — a result and a feed entry are the same object.
 *
 * Moved here from `ui/feed/MemoryCard.kt` when that file was deleted. Kept out of
 * [MemoryDisplay] on purpose: that object is pure and JVM-tested, and this reads
 * `ZoneId.systemDefault()`, which makes its output depend on the machine it runs on.
 */
internal fun formatTimestamp(instant: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}

/**
 * Expressive relative timestamp (e.g. "Just now", "5m ago", "2h ago", "Yesterday", "3d ago", "Sep 10").
 */
internal fun formatRelativeTimestamp(
    instant: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val duration = java.time.Duration.between(instant, now)
    val seconds = duration.seconds
    if (seconds < 0) return "Just now"
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    val today = now.atZone(zone).toLocalDate()
    val memoryDate = instant.atZone(zone).toLocalDate()

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 && memoryDate == today -> "${hours}h ago"
        memoryDate == today.minusDays(1) -> "Yesterday"
        days < 7 -> "${days}d ago"
        memoryDate.year == today.year -> {
            val fmt = DateTimeFormatter.ofPattern("MMM d").withZone(zone)
            fmt.format(instant)
        }
        else -> {
            val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(zone)
            fmt.format(instant)
        }
    }
}

/**
 * Expressive date formatted for memory cards:
 * - Same day: "9:41 AM"
 * - Yesterday: "Yesterday"
 * - Within past 6 days: Day of week (e.g. "Monday")
 * - Same year: "MMM d" (e.g. "Sep 12")
 * - Older: "MMM d, yyyy" (e.g. "Sep 12, 2025")
 */
internal fun formatCardDate(
    instant: Instant,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    locale: java.util.Locale = java.util.Locale.getDefault()
): String {
    val today = now.atZone(zone).toLocalDate()
    val memoryDate = instant.atZone(zone).toLocalDate()
    val days = java.time.temporal.ChronoUnit.DAYS.between(memoryDate, today)

    return when {
        memoryDate == today -> {
            val fmt = DateTimeFormatter.ofPattern("h:mm a", locale).withZone(zone)
            fmt.format(instant).uppercase()
        }
        memoryDate == today.minusDays(1) -> "Yesterday"
        days in 2..6 -> {
            val fmt = DateTimeFormatter.ofPattern("EEEE", locale).withZone(zone)
            fmt.format(instant)
        }
        memoryDate.year == today.year -> {
            val fmt = DateTimeFormatter.ofPattern("MMM d", locale).withZone(zone)
            fmt.format(instant)
        }
        else -> {
            val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy", locale).withZone(zone)
            fmt.format(instant)
        }
    }
}

