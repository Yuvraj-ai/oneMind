package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.entity.EventMapper.toDomain
import com.onemind.app.data.local.entity.EventMapper.toEntity
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao
) : EventRepository {

    override suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>) {
        // Status is the user's judgement about an event, not something re-derived from
        // the Memory's text, so it has to outlive the rows that carry it. Read before
        // deleting. Keyed on eventTime rather than id because the ids are new on every
        // replace and the instant is what identifies the event; two events at the same
        // instant in one Memory would collapse here, which is a case the pipeline does
        // not produce and which would be indistinguishable to a user anyway.
        val previous = dao.getEventsForMemory(memoryId).associateBy { it.eventTime }

        // Clear first and unconditionally: a Memory whose dates were edited away
        // must end up with no events, which an insert-only path cannot express.
        dao.deleteForMemory(memoryId)
        if (events.isEmpty()) return

        dao.insertAll(
            events.map { event ->
                val entity = event.toEntity()
                val carried = previous[entity.eventTime] ?: return@map entity
                // remindersScheduledAt travels with the status: dropping it would have
                // scheduleAll() enqueue a second set of reminders under new ids for an
                // event that already has them.
                entity.copy(
                    status = carried.status,
                    remindersScheduledAt = carried.remindersScheduledAt
                )
            }
        )
    }

    override fun observeUpcoming(): Flow<List<DetectedEvent>> =
        dao.observeUpcoming().map { rows -> rows.map { it.toDomain() } }

    override fun observeExpired(): Flow<List<DetectedEvent>> =
        dao.observeExpired().map { rows -> rows.map { it.toDomain() } }

    override suspend fun expireOverdue(now: Instant): Int =
        dao.expireOverdue(now.toEpochMilli())

    override suspend fun reject(eventId: Long) =
        dao.updateStatus(eventId, EventStatus.REJECTED)

    override suspend fun undoReject(eventId: Long) =
        dao.restoreToUpcoming(eventId)

    override suspend fun markAddedToCalendar(eventId: Long) =
        dao.updateStatus(eventId, EventStatus.IN_CALENDAR)
}
