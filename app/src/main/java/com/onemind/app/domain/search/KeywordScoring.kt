package com.onemind.app.domain.search

/**
 * Scores an FTS match.
 *
 * ## Why we score at all rather than using FTS's own ranking
 *
 * Room ships `@Fts3` and `@Fts4` annotations and no `@Fts5`. BM25 arrived in FTS5,
 * so with an FTS4 table there is no built-in relevance function to call — FTS4
 * answers "does this row match" and leaves ordering to the caller. Relevance is
 * therefore ours to define whether we want the job or not.
 *
 * ## What the score means
 *
 * Coverage dominates: a Memory containing three of the user's four words is a
 * better answer than one containing a single word thirty times. That is the
 * opposite of raw term frequency, and it is the right way round for this app —
 * someone searching "ramen recipe tokyo" is describing one Memory, not asking for
 * whichever Memory says "ramen" most often.
 *
 * Frequency still contributes, but only as a tiebreaker among equal coverage, and
 * with diminishing returns so a long document cannot win on bulk alone.
 *
 * The result is deliberately in [0, 1]: #28 has to combine this with cosine
 * similarity, and two scores on different scales cannot be blended without one
 * silently dominating.
 */
object KeywordScoring {

    /** How much of the score coverage accounts for. */
    private const val COVERAGE_WEIGHT = 0.85

    /** The remainder, from frequency, as a tiebreaker only. */
    private const val FREQUENCY_WEIGHT = 0.15

    /**
     * Occurrences of a single term beyond which extra hits stop helping.
     *
     * Caps the frequency component so a document that repeats one word cannot
     * outrank one that actually covers the query.
     */
    private const val FREQUENCY_SATURATION = 5.0

    /**
     * Score a document against the query terms, in [0, 1].
     *
     * Matching is prefix-based, mirroring the `*` in the MATCH expression, so the
     * score agrees with why FTS returned the row in the first place. Without that,
     * a row matched on a prefix could score zero and be dropped by a threshold —
     * found by the index, then discarded by our own arithmetic.
     */
    fun score(document: String, queryTerms: List<String>): Double {
        if (queryTerms.isEmpty()) return 0.0

        // documentTerms, not terms: scoring frequency needs the repeats a query
        // builder discards, and a document's stopwords are never consulted anyway.
        val documentTerms = FtsQuery.documentTerms(document)
        if (documentTerms.isEmpty()) return 0.0

        var matchedTermCount = 0
        var frequencyScore = 0.0

        queryTerms.forEach { queryTerm ->
            val occurrences = documentTerms.count { it.startsWith(queryTerm) }
            if (occurrences > 0) {
                matchedTermCount++
                // Saturating, so the 30th occurrence is worth far less than the 2nd.
                frequencyScore += minOf(occurrences.toDouble(), FREQUENCY_SATURATION) /
                    FREQUENCY_SATURATION
            }
        }

        if (matchedTermCount == 0) return 0.0

        val coverage = matchedTermCount.toDouble() / queryTerms.size
        val frequency = frequencyScore / queryTerms.size

        return (COVERAGE_WEIGHT * coverage) + (FREQUENCY_WEIGHT * frequency)
    }
}
