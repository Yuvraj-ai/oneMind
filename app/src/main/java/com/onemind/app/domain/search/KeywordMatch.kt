package com.onemind.app.domain.search

/**
 * One Memory matched by keyword search.
 *
 * Carries the document as well as the score because #29 builds its matching
 * snippet from it, and re-reading the text per result would undo the point of
 * fetching it once.
 */
data class KeywordMatch(
    val memoryId: Long,
    /** Relevance in [0, 1]. See [KeywordScoring]. */
    val score: Double,
    /** The indexed document that matched. */
    val matchedText: String
)
