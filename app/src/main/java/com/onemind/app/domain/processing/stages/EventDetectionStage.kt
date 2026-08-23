package com.onemind.app.domain.processing.stages

import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.entity.DetectedEventEntity
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.ProcessingStage
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import java.time.Instant
import javax.inject.Inject

/**
 * Detects future events in a Memory's extracted dates.
 *
 * Not an LLM stage. It reads what [MetadataExtractionStage] already found —
 * `ExtractedDate` entries where `isEventTime = true` and `parsedInstant` is in
 * the future — and promotes them to `DetectedEvent` rows with their own lifecycle.
 *
 * Runs after METADATA and before SUMMARIZATION, because the title generation in
 * SUMMARIZATION can benefit from knowing whether the Memory contains an event.
 *
 * ## What counts as an event
 *
 * A date/time is an event when two conditions hold:
 * 1. `isEventTime` is true — the metadata stage judged it to be about something
 *    *happening*, not just a date mentioned in passing.
 * 2. `parsedInstant` is after the Memory's `createdAt` — it is in the future from
 *    the user's perspective when they saved it.
 *
 * Past dates are never events. "I went to a concert last week" is a Memory about
 * something that already happened, not something the user needs to be reminded of.
 */
class EventDetectionStage @Inject constructor(
    private val eventDao: EventDao
) : ProcessingStage {

    override val id = StageId.EVENT_DETECTION

    override suspend fun process(memory: Memory): StageResult {
        // Clear previous events for this Memory, since reprocessing means dates
        // may have changed.
        eventDao.deleteForMemory(memory.id)

        val now = Instant.now()
        val futureEvents = memory.derived.dates
            .filter { date ->
                date.isEventTime &&
                    date.parsedInstant != null &&
                    date.parsedInstant.isAfter(memory.createdAt) &&
                    date.parsedInstant.isAfter(now)
            }

        if (futureEvents.isEmpty()) return StageResult.Empty

        val title = memory.derived.summary?.title
            ?: memory.userText().take(50).ifBlank { "Event" }

        val entities = futureEvents.map { date ->
            DetectedEventEntity(
                memoryId = memory.id,
                eventTime = date.parsedInstant!!.toEpochMilli(),
                eventTitle = date.rawText.ifBlank { title },
                status = EventStatus.UPCOMING
            )
        }

        eventDao.insertAll(entities)
        return StageResult.Success
    }
}
