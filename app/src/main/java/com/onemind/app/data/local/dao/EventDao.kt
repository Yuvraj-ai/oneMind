package com.onemind.app.data.local.dao

import androidx.room.*
import com.onemind.app.data.local.entity.DetectedEventEntity
import com.onemind.app.domain.model.EventStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: DetectedEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<DetectedEventEntity>)

    @Query("SELECT * FROM detected_events WHERE status = :status ORDER BY eventTime ASC")
    fun observeByStatus(status: EventStatus): Flow<List<DetectedEventEntity>>

    /**
     * Everything still ahead of the user, soonest first.
     *
     * `IN_CALENDAR` belongs here: exporting an event to a calendar app is not
     * dismissing it, so it stays visible and still expires on time.
     */
    @Query(
        """
        SELECT * FROM detected_events
        WHERE status IN ('UPCOMING', 'IN_CALENDAR')
        ORDER BY eventTime ASC
        """
    )
    fun observeUpcoming(): Flow<List<DetectedEventEntity>>

    /**
     * History: events time has passed, and events the user declined.
     *
     * `REJECTED` shares this list rather than disappearing, because rejecting is
     * reversible and a row nothing renders cannot be undone.
     */
    @Query(
        """
        SELECT * FROM detected_events
        WHERE status IN ('EXPIRED', 'REJECTED')
        ORDER BY eventTime DESC
        """
    )
    fun observeExpired(): Flow<List<DetectedEventEntity>>

    @Query("SELECT * FROM detected_events WHERE memoryId = :memoryId")
    suspend fun getEventsForMemory(memoryId: Long): List<DetectedEventEntity>

    @Query("DELETE FROM detected_events WHERE memoryId = :memoryId")
    suspend fun deleteForMemory(memoryId: Long)

    /**
     * Move past-due events to EXPIRED.
     *
     * `REJECTED` is left alone deliberately: a rejected event already renders in the
     * past list, and expiring it would silently strip the undo affordance. Undoing one
     * returns it to `UPCOMING` with a past time, and the next sweep expires it then.
     *
     * @param now current time in epoch millis
     * @return number of rows updated
     */
    @Query(
        """
        UPDATE detected_events SET status = 'EXPIRED'
        WHERE status IN ('UPCOMING', 'IN_CALENDAR') AND eventTime < :now
        """
    )
    suspend fun expireOverdue(now: Long): Int

    /**
     * Upcoming events that need reminders scheduled.
     *
     * An event needs reminders when it has none scheduled yet, or when the event
     * time has been updated (which re-nulls remindersScheduledAt via the pipeline
     * clearing and rewriting).
     *
     * Left on `UPCOMING` alone on purpose. An event is only *owed* reminders while it
     * is still plain upcoming: an `IN_CALENDAR` event keeps the reminders it already
     * has — they are never cancelled — it just does not earn new ones, and a
     * `REJECTED` one earns nothing until it is un-rejected.
     */
    @Query(
        """
        SELECT * FROM detected_events
        WHERE status = 'UPCOMING' AND remindersScheduledAt IS NULL
        ORDER BY eventTime ASC
        """
    )
    suspend fun getUnscheduledReminders(): List<DetectedEventEntity>

    @Query("UPDATE detected_events SET remindersScheduledAt = :scheduledAt WHERE id = :eventId")
    suspend fun markRemindersScheduled(eventId: Long, scheduledAt: Long)

    /** Kept in step with [observeUpcoming], so a count never disagrees with a list. */
    @Query("SELECT COUNT(*) FROM detected_events WHERE status IN ('UPCOMING', 'IN_CALENDAR')")
    suspend fun countUpcoming(): Int

    /** The single write behind rejecting an event and marking one as exported. */
    @Query("UPDATE detected_events SET status = :status WHERE id = :eventId")
    suspend fun updateStatus(eventId: Long, status: EventStatus)

    /**
     * Return an event to plain upcoming and forget its reminders were ever scheduled.
     *
     * One statement rather than two writes, because the two halves are one decision.
     * Rejecting cancels the enqueued jobs but leaves `remindersScheduledAt` set;
     * [getUnscheduledReminders] skips any event that has it, so without clearing it
     * here an un-rejected event would never be reminded about again.
     */
    @Query(
        """
        UPDATE detected_events SET status = 'UPCOMING', remindersScheduledAt = NULL
        WHERE id = :eventId
        """
    )
    suspend fun restoreToUpcoming(eventId: Long)
}
