package com.onemind.app.domain.model

/**
 * A single content block within a Memory.
 * Content blocks are ordered and typed. A Memory can contain multiple
 * blocks of different types (text + images + URLs in one Memory).
 */
data class ContentBlock(
    val id: Long = 0,
    val memoryId: Long = 0,
    /** Position in the ordered list of blocks within this Memory */
    val position: Int = 0,
    val type: ContentType,
    /**
     * The content value:
     * - For TEXT: the text string
     * - For IMAGE: the file path to the canonical image
     * - For URL: the URL string
     */
    val content: String,
    /** Optional thumbnail path (for IMAGE type) */
    val thumbnailPath: String? = null,
    /** Optional metadata (JSON or key-value, extensible) */
    val metadata: String? = null
)
