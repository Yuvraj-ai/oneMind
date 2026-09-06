package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory

/**
 * How large a card is drawn in the bento grid.
 *
 * Each has its own asymmetric silhouette — see `CardShapeLarge` / `Medium` / `Small` —
 * and `LARGE` spans both grid columns.
 */
enum class BentoSize { LARGE, MEDIUM, SMALL }

/**
 * The bento rhythm.
 *
 * One large card for the newest Memory that has an image, then medium and small
 * alternating. Position, not a stored field: `index.html` reads a `size` off its mock
 * data, and adding one to the real `Memory` would make the processing pipeline the owner
 * of a decision the grid makes.
 *
 * Timeline and Events force `MEDIUM` and do not call this at all.
 */
object BentoSizing {

    fun sizes(memories: List<Memory>): List<BentoSize> {
        val largeIndex = memories.indexOfFirst { it.imageBlocks().isNotEmpty() }

        // Advanced only for the cards that participate, so the large one does not consume
        // a turn. Counting it would put two smalls side by side immediately after the
        // card the eye is already on.
        var alternation = 0

        return memories.mapIndexed { index, _ ->
            if (index == largeIndex) {
                BentoSize.LARGE
            } else {
                val size = if (alternation % 2 == 0) BentoSize.MEDIUM else BentoSize.SMALL
                alternation++
                size
            }
        }
    }
}
