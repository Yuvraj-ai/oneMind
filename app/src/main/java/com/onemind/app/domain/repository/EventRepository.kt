package com.onemind.app.domain.repository

import com.onemind.app.domain.model.DetectedEvent
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Reads and writes the events the Processing Pipeline found inside Memories.
 *
 * Stated in terms of [DetectedEvent] rather than the storage row on purpose. An
 * event is a *lens* on a Memory that already exists, and the fact that its time
 * happens to be persisted as a column of epoch millis is the implementation's
 * business alone — putting that detail in the pipeline is what previously made
 * `domain` depend on Room.
 */
interface EventRepository {

    /**
     * Make [events] the complete set for [memoryId], dropping any it had before.
     *
     * Replace rather than append, because reprocessing re-derives a Memory's
     * dates from scratch: adding would leave events behind describing text that
     * no longer exists. An empty list is a legitimate call and clears the Memory.
     */
    suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>)

    /** Events still ahead of us, soonest first. */
    fun observeUpcoming(): Flow<List<DetectedEvent>>

    /** Events whose time has passed, most recent first. Kept for history. */
    fun observeExpired(): Flow<List<DetectedEvent>>

    /**
     * Move past-due events to expired.
     *
     * @return how many changed, so a caller can distinguish "nothing was due"
     *   from "this did not run".
     */
    suspend fun expireOverdue(now: Instant): Int

    /**
     * The user declined this event.
     *
     * Reversible, and named for what the user did rather than for the column it
     * writes — `domain` states intent, and which status that becomes is the
     * implementation's business, the same reasoning that keeps `Instant` on this
     * side of the seam and epoch millis on the other.
     */
    suspend fun reject(eventId: Long)

    /**
     * Take back a rejection, returning the event to plain upcoming.
     *
     * Unconditional: an event rejected before its time and undone after it comes back
     * upcoming with a past time, and the next [expireOverdue] moves it on. That is
     * self-correcting, and cheaper than reading the row back to decide.
     */
    suspend fun undoReject(eventId: Long)

    /**
     * The user exported this event to their calendar app.
     *
     * Still upcoming — a calendar entry is a copy, not a dismissal — and its oneMind
     * reminders are deliberately left alone: `ACTION_INSERT` opens the calendar app's
     * own add screen and oneMind never learns whether the user saved or cancelled, so
     * cancelling here would sometimes remove the only reminder they have.
     */
    suspend fun markAddedToCalendar(eventId: Long)
}
