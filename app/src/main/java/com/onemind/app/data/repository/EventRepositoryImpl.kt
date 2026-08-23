package com.onemind.app.data.repository

import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.entity.EventMapper.toDomain
import com.onemind.app.data.local.entity.EventMapper.toEntity
import com.onemind.app.domain.model.DetectedEvent
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
        // Clear first and unconditionally: a Memory whose dates were edited away
        // must end up with no events, which an insert-only path cannot express.
        dao.deleteForMemory(memoryId)
        if (events.isNotEmpty()) {
            dao.insertAll(events.map { it.toEntity() })
        }
    }

    override fun observeUpcoming(): Flow<List<DetectedEvent>> =
        dao.observeUpcoming().map { rows -> rows.map { it.toDomain() } }

    override fun observeExpired(): Flow<List<DetectedEvent>> =
        dao.observeExpired().map { rows -> rows.map { it.toDomain() } }

    override suspend fun expireOverdue(now: Instant): Int =
        dao.expireOverdue(now.toEpochMilli())
}
