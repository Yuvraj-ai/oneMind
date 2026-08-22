package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.search.FtsQuery
import com.onemind.app.domain.search.SearchResult

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
    val searchResults: List<SearchResult> = emptyList(),

    /**
     * Terms the current search matched on, for highlighting snippets.
     *
     * Held here rather than recomputed per card: every result needs the same list,
     * and re-parsing the query for each one would repeat the work on every frame.
     */
    val searchTerms: List<String> = emptyList(),

    val isSearching: Boolean = false
) {
    /**
     * True once the user has typed something the search can actually act on.
     *
     * Keyed on whether a query could be *built*, not on whether text was typed.
     * Those differ: `FtsQuery.build` returns null for a single character or a query
     * made only of stopwords, so keying on raw text made typing "the" or "a" show a
     * hard "No memories found" — telling the user their memories were missing when
     * nothing had been searched for.
     */
    val isSearchActive: Boolean get() = FtsQuery.build(searchQuery) != null
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
