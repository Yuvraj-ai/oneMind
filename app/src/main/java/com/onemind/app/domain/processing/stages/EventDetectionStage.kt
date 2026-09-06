package com.onemind.app.domain.processing.stages

import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.ProcessingStage
import com.onemind.app.domain.processing.StageId
import com.onemind.app.domain.processing.StageResult
import com.onemind.app.domain.repository.EventRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * Detects future events in a Memory's extracted dates.
 *
 * Not an LLM stage. It reads what [MetadataExtractionStage] already found —
 * `ExtractedDate` entries where `isEventTime = true` and `parsedInstant` is in
 * the future — and promotes them to [DetectedEvent]s with their own lifecycle.
 *
 * Runs after METADATA and before SUMMARIZATION, because the title generation in
 * SUMMARIZATION can benefit from knowing whether the Memory contains an event.
 *
 * ## What counts as an event
 *
 * A date/time is an event when two conditions hold:
 * 1. `isEventTime` is true — the metadata stage judged it to be about something
 *    *happening*, not just a date mentioned in passing.
 * 2. It is still ahead of us: after the Memory's `createdAt`, and after now. The
 *    second check earns its place on reprocessing, where a Memory saved a month
 *    ago may describe something that has since happened.
 *
 * Past dates are never events. "I went to a concert last week" is a Memory about
 * something that already happened, not something the user needs to be reminded of.
 *
 * [clock] is injected rather than read ambiently, so the future/past boundary can
 * be pinned in a test instead of depending on when the suite runs — the same
 * reason [com.onemind.app.domain.search.TemporalExpressionParser] takes its today.
 */
class EventDetectionStage @Inject constructor(
    private val events: EventRepository,
    private val clock: Clock
) : ProcessingStage {

    override val id = StageId.EVENT_DETECTION

    override suspend fun process(memory: Memory): StageResult {
        val now = Instant.now(clock)

        val futureDates = memory.derived.dates.filter { date ->
            val at = date.parsedInstant
            date.isEventTime && at != null && at.isAfter(memory.createdAt) && at.isAfter(now)
        }

        val title = memory.derived.summary?.title
            ?: memory.userText().take(50).ifBlank { "Event" }

        // Written even when the list is empty: that is what clears the events of a
        // Memory whose dates have since been edited away.
        events.replaceEventsForMemory(
            memory.id,
            futureDates.map { date ->
                DetectedEvent(
                    memoryId = memory.id,
                    eventTime = date.parsedInstant!!,
                    eventTitle = date.rawText.ifBlank { title },
                    status = EventStatus.UPCOMING
                )
            }
        )

        return if (futureDates.isEmpty()) StageResult.Empty else StageResult.Success
    }
}
