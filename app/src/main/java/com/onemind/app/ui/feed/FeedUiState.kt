package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.SourceType

/**
 * UI state for the Memory Feed screen.
 */
data class FeedUiState(
    val memories: List<Memory> = emptyList(),
    val isLoading: Boolean = true,
    val memoryToDelete: Memory? = null,

    /** Source filter (#21). Null = show all. */
    val sourceFilter: SourceFilter? = null,
    val availableSources: List<SourceFilterOption> = emptyList(),

    /** View mode (#22). FEED = flat list, TIMELINE = date-grouped. */
    val viewMode: ViewMode = ViewMode.FEED,

    // --- search (#24) ------------------------------------------------------

    /** Exactly what the user typed. */
    val searchQuery: String = "",

    /** Matches for [searchQuery], best first. Meaningless while [isSearching]. */
    val searchResults: List<Memory> = emptyList(),

    val isSearching: Boolean = false
) {
    /**
     * True once the user has typed something usable.
     *
     * Drives which of two modes the screen is in: browsing their memories, or
     * looking at search results. Derived rather than stored, so the two can never
     * disagree.
     */
    val isSearchActive: Boolean get() = searchQuery.isNotBlank()
}

/**
 * A selected source filter.
 */
data class SourceFilter(
    val sourceType: SourceType,
    val sourcePackage: String? = null
)

/**
 * A filter option with its count, for the chips row.
 */
data class SourceFilterOption(
    val sourceType: SourceType,
    val sourcePackage: String?,
    val label: String,
    val count: Int
)

enum class ViewMode { FEED, TIMELINE }
