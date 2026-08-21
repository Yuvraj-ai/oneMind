package com.onemind.app.ui.feed

import com.onemind.app.domain.model.Memory

/**
 * UI state for the Memory Feed screen.
 */
data class FeedUiState(
    val memories: List<Memory> = emptyList(),
    val isLoading: Boolean = true,
    val memoryToDelete: Memory? = null
)
