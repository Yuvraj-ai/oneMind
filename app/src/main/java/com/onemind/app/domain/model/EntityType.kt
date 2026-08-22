package com.onemind.app.domain.model

/**
 * Classification of a named entity extracted from a Memory's content.
 *
 * Deliberately a small closed set. M5 extracts entities; it does not build a
 * knowledge graph, so this covers the useful cases and stops there.
 */
enum class EntityType {
    PERSON,
    ORGANIZATION,
    PRODUCT,
    PLACE,
    EVENT,
    TECHNOLOGY,

    /** Recognised as an entity, but not confidently one of the above. */
    OTHER
}
