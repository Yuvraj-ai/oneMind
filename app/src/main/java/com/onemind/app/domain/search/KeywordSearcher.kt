package com.onemind.app.domain.search

import com.onemind.app.domain.repository.SearchIndexRepository
import javax.inject.Inject

/**
 * Keyword search over the full-text index.
 *
 * Thin on purpose: the two pieces with judgement in them — turning user input into
 * a safe MATCH expression, and deciding what relevance means — live in [FtsQuery]
 * and [KeywordScoring] where they can be tested without a database. This wires
 * them to storage and sorts the result.
 *
 * Works with no AI provider configured. #27 adds query understanding on top, but
 * keyword search must stand alone, because the alternative is an app whose search
 * box stops working when a network call fails.
 */
class KeywordSearcher @Inject constructor(
    private val searchIndexRepository: SearchIndexRepository
) {

    /**
     * Search for [rawQuery], best match first.
     *
     * An unusable query returns empty rather than throwing. "Unusable" means the
     * user typed only punctuation or a single character — the caller distinguishes
     * that from "searched and found nothing" by checking whether the query itself
     * was blank, which is why this does not need to signal it.
     */
    suspend fun search(rawQuery: String, limit: Int = DEFAULT_LIMIT): List<KeywordMatch> {
        val expression = FtsQuery.build(rawQuery) ?: return emptyList()
        val queryTerms = FtsQuery.terms(rawQuery)

        // Over-fetch, because OR semantics return rows matching any single term and
        // the weakest of those are about to be dropped. Cutting at `limit` in SQL
        // would let an arbitrary set of one-term matches crowd out a full-coverage
        // match that happened to sort later in rowid order.
        return searchIndexRepository
            .match(expression, limit * OVER_FETCH_FACTOR)
            .map { row ->
                KeywordMatch(
                    memoryId = row.memoryId,
                    score = KeywordScoring.score(row.searchableText, queryTerms),
                    matchedText = row.searchableText
                )
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 50

        /**
         * How far past [DEFAULT_LIMIT] to read before scoring.
         *
         * Three is enough to make the crowding-out problem unlikely without
         * loading the whole index for a common word.
         */
        private const val OVER_FETCH_FACTOR = 3
    }
}
