package com.onemind.app.ui.events

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.ExtractedEntity

/**
 * One event as the Events screen draws it.
 *
 * Carries the [DetectedEvent] whole rather than flattening it into strings, because
 * every action on the card needs its id and its status. The two extra fields are not
 * on `DetectedEvent` and deliberately are not being added to it: they belong to the
 * Memory the event is a lens on, and reading them is a presentation concern.
 */
data class EventCardUi(
    val event: DetectedEvent,
    /** The Memory's first PLACE entity, or null when it named no place. */
    val location: String? = null,
    /** At most [EventCardAssembly.MAX_CHIPS] of the Memory's categories. */
    val categories: List<Category> = emptyList()
)

/**
 * Joins events to the Memory data their cards show.
 *
 * Pure and separate from the ViewModel so it can be checked on the JVM, the same
 * reasoning that put `ReminderPlanner` and `DateGrouping` in their own files. It takes
 * maps rather than a repository because deciding *what* a card shows and deciding
 * *how many queries that costs* are different problems.
 */
object EventCardAssembly {

    /** How many category chips fit a card before it starts wrapping. */
    const val MAX_CHIPS = 3

    fun assemble(
        events: List<DetectedEvent>,
        entities: Map<Long, List<ExtractedEntity>>,
        categories: Map<Long, List<Category>>
    ): List<EventCardUi> = events.map { event ->
        EventCardUi(
            event = event,
            // First rather than best: entities carry a confidence, but it is nullable
            // and often absent, so ranking on it would mostly be ranking on nothing.
            location = entities[event.memoryId]
                ?.firstOrNull { it.entityType == EntityType.PLACE }
                ?.name,
            categories = categories[event.memoryId].orEmpty().take(MAX_CHIPS)
        )
    }
}
