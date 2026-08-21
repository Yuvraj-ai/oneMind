package com.onemind.app.ui.composer

/**
 * UI state for the Memory Composer screen.
 */
data class ComposerUiState(
    /** The current text content in the editor */
    val text: String = "",
    /** Paths to attached images (canonical paths after optimization) */
    val imagePaths: List<ImageAttachment> = emptyList(),
    /** Whether the memory is being loaded (edit mode) */
    val isLoading: Boolean = false,
    /** Whether auto-save just fired (brief visual confirmation) */
    val showSavedIndicator: Boolean = false,
    /** The memory ID if editing an existing memory, null if creating new */
    val memoryId: Long? = null,
    /** Whether the memory has been committed (SAVED state, user left) */
    val isCommitted: Boolean = false
)

/**
 * Represents an attached image in the composer.
 */
data class ImageAttachment(
    /** URI or file path of the image source (before optimization) */
    val sourceUri: String,
    /** Path to the optimized canonical image (after save) */
    val canonicalPath: String? = null,
    /** Path to the thumbnail (after save) */
    val thumbnailPath: String? = null
)
