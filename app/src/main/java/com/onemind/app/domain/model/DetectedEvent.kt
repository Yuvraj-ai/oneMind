package com.onemind.app.domain.model

import java.time.Instant

/**
 * A future date/time detected in a Memory.
 *
 * Not a separate kind of thing a user saves — it is a *lens* on an existing Memory.
 * A Memory becomes event-bearing when the pipeline finds a `parsedInstant` that is
 * after `createdAt`. It expires when that instant passes, but the Memory persists;
 * only its event status changes.
 *
 * Examples of what creates one: "AI Summit 2026 on September 15", a screenshot of a
 * calendar invite, "dentist appointment Tuesday at 3pm".
 */
data class DetectedEvent(
    val id: Long = 0,
    val memoryId: Long,
    /** When the event happens, parsed from the Memory's content. */
    val eventTime: Instant,
    /** Short description, derived from the Memory's title or the date's surrounding text. */
    val eventTitle: String,
    val status: EventStatus = EventStatus.UPCOMING,
    /** When reminders were last scheduled, so we don't double-schedule. */
    val remindersScheduledAt: Instant? = null
)

enum class EventStatus {
    /** The event time has not yet passed. */
    UPCOMING,
    /** The event time has passed. Kept for history. */
    EXPIRED
}
