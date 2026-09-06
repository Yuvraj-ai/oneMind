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
