package com.onemind.app.domain.model

/**
 * The type of a content block within a Memory.
 */
enum class ContentType {
    /** Plain or rich text content */
    TEXT,

    /** An image (stored as file, referenced by path) */
    IMAGE,

    /** A URL / link */
    URL
}
