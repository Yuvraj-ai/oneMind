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
    val viewMode: ViewMode = ViewMode.FEED
)

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
