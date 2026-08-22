package com.onemind.app.domain.search

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.SourceType

/**
 * What a query is asking for, decomposed into the dimensions retrieval can act on.
 *
 * The locked decisions rule out manual filter controls in search: a user writes
 * "the AI stuff I saved from Chrome last week" and the system works out that this
 * is a topic, a source, and a time range. This is that decomposition, and it is
 * the reason there is no filter UI to build.
 *
 * Every field is optional because every one of them may be absent from a query.
 * A bare "Qwen3" carries only [keywordQuery], and that is a complete, valid intent
 * rather than a degenerate one.
 */
data class SearchIntent(
    /**
     * Text for keyword matching. Usually the whole query.
     *
     * Kept even when a temporal or source constraint was extracted, because those
     * words may also appear in the content — someone searching "Chrome extension"
     * means the words, not the source.
     */
    val keywordQuery: String,

    /**
     * Text for semantic matching, with constraint language removed.
     *
     * Differs from [keywordQuery] because embedding is sensitive to phrasing in a
     * way that keyword matching is not: "AI stuff from last week" embeds partly
     * toward the language of *asking about time*, which is noise once the temporal
     * part has become a hard filter.
     */
    val semanticQuery: String,

    /** Hard filter on when the Memory was saved. Null means all of time. */
    val temporal: TemporalExpression? = null,

    /** Hard filter on where the Memory came from. Null means any source. */
    val sourceType: SourceType? = null,

    /** Narrower source constraint, when a specific app was named. */
    val sourcePackage: String? = null,

    /**
     * Categories the query suggests, as a soft signal.
     *
     * Always drawn from the seeded vocabulary — a model can no more invent a
     * category here than it can in #14. Soft rather than hard because a query
     * mentioning "food" should rank recipes higher, not exclude everything else:
     * category assignment is itself a model's judgement and wrong often enough
     * that filtering on it would hide correct answers.
     */
    val categories: List<Category> = emptyList(),

    /**
     * Whether a language model was consulted.
     *
     * Recorded so behaviour is explainable after the fact: an odd result set is a
     * different problem depending on whether it came from a model's reading of the
     * query or from the plain-text fallback.
     */
    val wasDecomposed: Boolean = false
) {
    /** True when the query narrows by something other than text. */
    val hasConstraints: Boolean
        get() = temporal != null || sourceType != null || sourcePackage != null

    companion object {
        /**
         * The intent for a query taken at face value.
         *
         * Used for simple queries, and as the fallback whenever decomposition is
         * unavailable or unusable. Naming it makes the fallback path explicit
         * rather than something assembled inline at three call sites.
         */
        fun literal(query: String) = SearchIntent(
            keywordQuery = query,
            semanticQuery = query,
            wasDecomposed = false
        )
    }
}
