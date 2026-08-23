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
}
