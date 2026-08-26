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

/**
 * Where an event stands, combining what time has done to it with what the user has
 * decided about it.
 *
 * Persisted as TEXT by name, with no explicit converter, which is why adding a value
 * here costs no migration and leaves the schema at version 5. The cost is elsewhere:
 * any SQL that spells a status out as a literal becomes an incomplete enumeration the
 * moment this grows, and nothing fails to compile when it does.
 */
enum class EventStatus {
    /** The event time has not yet passed. */
    UPCOMING,
    /** The event time has passed. Kept for history. */
    EXPIRED,
    /** The user declined this event. Reversible, and shown alongside expired ones. */
    REJECTED,
    /**
     * The user exported this event to their calendar app.
     *
     * Still upcoming — exporting is not dismissing — so it stays in the upcoming list
     * and still expires when its time passes.
     */
    IN_CALENDAR
}
