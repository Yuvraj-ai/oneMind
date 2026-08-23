package com.onemind.app.data.local.entity

import com.onemind.app.domain.model.DetectedEvent
import java.time.Instant

/**
 * Maps detected-event rows to and from [DetectedEvent].
 *
 * The only place that knows an event time is stored as epoch millis. Everything
 * above this reads an [Instant].
 */
object EventMapper {

    fun DetectedEventEntity.toDomain() = DetectedEvent(
        id = id,
        memoryId = memoryId,
        eventTime = Instant.ofEpochMilli(eventTime),
        eventTitle = eventTitle,
        status = status,
        remindersScheduledAt = remindersScheduledAt?.let(Instant::ofEpochMilli)
    )

    fun DetectedEvent.toEntity() = DetectedEventEntity(
        id = id,
        memoryId = memoryId,
        eventTime = eventTime.toEpochMilli(),
        eventTitle = eventTitle,
        status = status,
        remindersScheduledAt = remindersScheduledAt?.toEpochMilli()
    )
}
