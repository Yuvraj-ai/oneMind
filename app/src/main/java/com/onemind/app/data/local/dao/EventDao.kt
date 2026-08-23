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

    @Query("SELECT * FROM detected_events WHERE status = 'UPCOMING' ORDER BY eventTime ASC")
    fun observeUpcoming(): Flow<List<DetectedEventEntity>>

    @Query("SELECT * FROM detected_events WHERE status = 'EXPIRED' ORDER BY eventTime DESC")
    fun observeExpired(): Flow<List<DetectedEventEntity>>

    @Query("SELECT * FROM detected_events WHERE memoryId = :memoryId")
    suspend fun getEventsForMemory(memoryId: Long): List<DetectedEventEntity>

    @Query("DELETE FROM detected_events WHERE memoryId = :memoryId")
    suspend fun deleteForMemory(memoryId: Long)

    /**
     * Move past-due events to EXPIRED.
     *
     * @param now current time in epoch millis
     * @return number of rows updated
     */
    @Query("UPDATE detected_events SET status = 'EXPIRED' WHERE status = 'UPCOMING' AND eventTime < :now")
    suspend fun expireOverdue(now: Long): Int

    /**
     * Upcoming events that need reminders scheduled.
     *
     * An event needs reminders when it has none scheduled yet, or when the event
     * time has been updated (which re-nulls remindersScheduledAt via the pipeline
     * clearing and rewriting).
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

    @Query("SELECT COUNT(*) FROM detected_events WHERE status = 'UPCOMING'")
    suspend fun countUpcoming(): Int
}
