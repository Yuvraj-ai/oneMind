package com.onemind.app

import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.ui.feed.BentoSize
import com.onemind.app.ui.feed.BentoSizing
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bento rhythm: one large card for the newest image, then alternating medium and
 * small.
 *
 * Derived from position rather than stored on the Memory. `index.html` reads a `size`
 * field off its mock data; the real model has none and should not gain one — how large a
 * card is drawn is the grid's business, and putting it in the domain model would make the
 * processing pipeline responsible for a layout decision.
 */
class BentoSizingTest {

    private fun textMemory(id: Long) = Memory(
        id = id,
        contentBlocks = listOf(ContentBlock(type = ContentType.TEXT, content = "hi"))
    )

    private fun imageMemory(id: Long) = Memory(
        id = id,
        contentBlocks = listOf(ContentBlock(type = ContentType.IMAGE, content = "/tmp/a.webp"))
    )

    @Test
    fun anEmptyFeedHasNoSizes() {
        assertEquals(emptyList<BentoSize>(), BentoSizing.sizes(emptyList()))
    }

    @Test
    fun withNoImagesEveryCardAlternatesFromMedium() {
        val sizes = BentoSizing.sizes((1L..5L).map { textMemory(it) })

        assertEquals(
            listOf(
                BentoSize.MEDIUM, BentoSize.SMALL,
                BentoSize.MEDIUM, BentoSize.SMALL,
                BentoSize.MEDIUM
            ),
            sizes
        )
    }

    @Test
    fun theNewestImageGetsTheLargeCard() {
        val sizes = BentoSizing.sizes(listOf(imageMemory(1), textMemory(2), imageMemory(3)))

        // First in the list, because the feed is newest-first. Only one card is large,
        // however many images there are.
        assertEquals(BentoSize.LARGE, sizes[0])
        assertEquals(1, sizes.count { it == BentoSize.LARGE })
    }

    @Test
    fun theLargeCardIsNotCountedInTheAlternation() {
        val sizes = BentoSizing.sizes(
            listOf(textMemory(1), textMemory(2), imageMemory(3), textMemory(4), textMemory(5))
        )

        // Positions 0, 1, 3, 4 alternate as if position 2 were not there. Counting the
        // large card would put two smalls next to each other and break the rhythm at
        // exactly the point the eye is drawn to.
        assertEquals(
            listOf(
                BentoSize.MEDIUM, BentoSize.SMALL,
                BentoSize.LARGE,
                BentoSize.MEDIUM, BentoSize.SMALL
            ),
            sizes
        )
    }

    @Test
    fun anImageWithNoTextStillCountsAsAnImage() {
        val sizes = BentoSizing.sizes(listOf(imageMemory(1)))

        assertEquals(listOf(BentoSize.LARGE), sizes)
    }

    @Test
    fun sizesAlignsOneToOneWithTheInput() {
        val memories = (1L..7L).map { if (it == 4L) imageMemory(it) else textMemory(it) }

        // The grid zips these together, so a length mismatch would silently shift every
        // card's shape by one.
        assertEquals(memories.size, BentoSizing.sizes(memories).size)
    }
}
