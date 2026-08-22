package com.onemind.app.domain.search

import com.onemind.app.domain.model.Memory

/**
 * One ranked search result.
 *
 * Carries the component scores as well as the combined one. That is not
 * diagnostics for its own sake: when a result looks wrong, the useful question is
 * *which* signal put it there, and without the parts a blended number cannot answer
 * it. #29 also uses [keywordScore] to decide whether a literal snippet is worth
 * showing or whether the match was purely semantic.
 */
data class SearchResult(
    val memory: Memory,
    /** Blended relevance, in [0, 1]. */
    val score: Double,
    /** Keyword contribution before weighting, or 0 if keyword search missed it. */
    val keywordScore: Double = 0.0,
    /** Semantic contribution before weighting, or 0 if vector search missed it. */
    val semanticScore: Double = 0.0,
    /** The indexed text that matched, when keyword search found it. */
    val matchedText: String? = null
) {
    /** True when only meaning matched, so there is no literal term to highlight. */
    val isSemanticOnly: Boolean get() = keywordScore == 0.0 && semanticScore > 0.0
}
