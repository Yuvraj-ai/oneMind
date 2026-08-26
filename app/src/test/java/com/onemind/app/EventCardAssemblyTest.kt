package com.onemind.app

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DerivedSource
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.ExtractedEntity
import com.onemind.app.ui.events.EventCardAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * That an event card shows the location and categories the mock asks for, from data
 * the app already had.
 *
 * `DetectedEvent` has neither field, which looked at first like two cuts. It is not:
 * a Memory's `PLACE` entities and its categories are both reachable from `memoryId`,
 * with no new extraction and no pipeline change. This is where that assembly lives so
 * it can be checked without a device.
 */
class EventCardAssemblyTest {

    private val at: Instant = Instant.parse("2026-09-15T09:00:00Z")

    private fun event(id: Long, memoryId: Long) = DetectedEvent(
        id = id,
        memoryId = memoryId,
        eventTime = at,
        eventTitle = "AI Summit"
    )

    private fun entity(memoryId: Long, name: String, type: EntityType) = ExtractedEntity(
        memoryId = memoryId,
        name = name,
        entityType = type,
        confidence = null,
        source = DerivedSource.OCR
    )

    private fun category(id: Long, name: String) = Category(id = id, name = name)

    @Test
    fun theLocationComesFromThePlaceEntity() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = mapOf(7L to listOf(entity(7L, "Moscone Center", EntityType.PLACE))),
            categories = emptyMap()
        )

        assertEquals("Moscone Center", cards.single().location)
    }

    @Test
    fun otherEntityTypesAreNotMistakenForALocation() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = mapOf(
                7L to listOf(
                    entity(7L, "Google", EntityType.ORGANIZATION),
                    entity(7L, "Sundar", EntityType.PERSON)
                )
            ),
            categories = emptyMap()
        )

        assertNull(cards.single().location)
    }

    @Test
    fun aMemoryWithNoEntitiesSimplyHasNoLocation() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = emptyMap(),
            categories = emptyMap()
        )

        // The mock treats the pin as optional, so absence is a rendering decision,
        // not a hole to fill with a placeholder.
        assertNull(cards.single().location)
    }

    @Test
    fun atMostThreeCategoryChipsAreCarried() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L)),
            entities = emptyMap(),
            categories = mapOf(
                7L to listOf(
                    category(1L, "Work"), category(2L, "Travel"),
                    category(3L, "Tech"), category(4L, "Finance")
                )
            )
        )

        assertEquals(
            listOf("Work", "Travel", "Tech"),
            cards.single().categories.map { it.name }
        )
    }

    @Test
    fun eachEventReadsOnlyItsOwnMemorysData() {
        val cards = EventCardAssembly.assemble(
            events = listOf(event(1L, memoryId = 7L), event(2L, memoryId = 8L)),
            entities = mapOf(
                7L to listOf(entity(7L, "Moscone Center", EntityType.PLACE)),
                8L to listOf(entity(8L, "Dentist on 5th", EntityType.PLACE))
            ),
            categories = mapOf(7L to listOf(category(1L, "Work")))
        )

        assertEquals("Moscone Center", cards[0].location)
        assertEquals(listOf("Work"), cards[0].categories.map { it.name })
        assertEquals("Dentist on 5th", cards[1].location)
        assertTrue(cards[1].categories.isEmpty())
    }

    @Test
    fun theEventItselfIsCarriedThroughUntouched() {
        val original = event(1L, memoryId = 7L)

        val card = EventCardAssembly.assemble(listOf(original), emptyMap(), emptyMap()).single()

        // The actions need an id to act on, so the card carries the event rather than
        // flattening it into display strings.
        assertSame(original, card.event)
    }
}
