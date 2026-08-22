package com.onemind.app.domain.search

import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.repository.MemoryRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

/**
 * The whole search, end to end.
 *
 * Understands the query, gathers candidates by keyword and by meaning, applies the
 * constraints that are genuinely constraints, and ranks what survives.
 *
 * The locked decisions insist this is one retrieval system rather than three:
 * keyword, semantic, contextual and temporal are dimensions of a single ranked
 * search, not separate engines with separate result lists. This class is where that
 * holds or fails, because it is the only place all four meet.
 */
class SearchOrchestrator @Inject constructor(
    private val queryUnderstanding: QueryUnderstanding,
    private val keywordSearcher: KeywordSearcher,
    private val vectorSearcher: VectorSearcher,
    private val memoryRepository: MemoryRepository
) {

    /**
     * Search for [rawQuery].
     *
     * Returns an empty list both for an unusable query and for a query that matched
     * nothing above the relevance threshold. The caller distinguishes those by
     * looking at the query, which it already has — encoding the difference in the
     * return type would complicate every call site to no benefit.
     */
    suspend fun search(rawQuery: String, limit: Int = DEFAULT_LIMIT): List<SearchResult> {
        if (rawQuery.isBlank()) return emptyList()

        val intent = queryUnderstanding.understand(rawQuery)

        // Both searches run at once. They share no state and the latency budget is
        // tight enough that paying for them in sequence would put the total over it
        // for no reason. Over-fetch, because hard filters and the relevance threshold
        // are applied after ranking and both remove candidates.
        val candidateLimit = limit * CANDIDATE_FACTOR
        val (keywordMatches, vectorMatches) = coroutineScope {
            val keyword = async { keywordSearcher.search(intent.keywordQuery, candidateLimit) }
            val vector = async { vectorSearcher.search(intent.semanticQuery, candidateLimit) }
            keyword.await() to vector.await()
        }

        if (keywordMatches.isEmpty() && vectorMatches.isEmpty()) return emptyList()

        val candidateIds = (keywordMatches.map { it.memoryId } + vectorMatches.map { it.memoryId })
            .distinct()

        // Hydrated in one query. Also the only point where the Memory's own
        // metadata becomes available, which is what the hard filters need.
        val memories = memoryRepository.getMemoriesByIds(candidateIds).associateBy { it.id }

        val allowed = memories.filterValues { satisfiesConstraints(it, intent) }

        return ResultRanking
            .rank(
                memories = allowed,
                keywordMatches = keywordMatches.filter { it.memoryId in allowed },
                vectorMatches = vectorMatches.filter { it.memoryId in allowed },
                impliedCategories = intent.categories
            )
            .take(limit)
    }

    /**
     * Whether a Memory passes the query's hard filters.
     *
     * Temporal and source constraints are absolute, not weightings. "What did I save
     * yesterday" is a statement about which Memories are eligible, and letting a
     * highly-relevant Memory from last month through on score would answer a
     * question the user did not ask.
     *
     * Categories are deliberately absent here. They are a ranking hint applied in
     * [ResultRanking], because category assignment is itself a model's judgement and
     * filtering on it would hide correct answers.
     */
    private fun satisfiesConstraints(memory: Memory, intent: SearchIntent): Boolean {
        intent.temporal?.let { window ->
            if (memory.createdAt !in window) return false
        }

        intent.sourceType?.let { required ->
            if (memory.sourceType != required) return false
        }

        intent.sourcePackage?.let { required ->
            // Only excludes when the Memory has a source to disagree with. A Memory
            // whose origin was never determined is unknown, not "not Chrome", and
            // #17 is explicit that a missing source is never fabricated — treating
            // absence as mismatch would quietly punish honest data.
            val actual = memory.sourcePackage ?: return false
            if (!actual.equals(required, ignoreCase = true)) return false
        }

        return true
    }

    companion object {
        const val DEFAULT_LIMIT = 30

        /**
         * How many candidates to gather per requested result.
         *
         * Hard filters and the relevance threshold both cut after retrieval, so
         * fetching exactly [DEFAULT_LIMIT] would routinely return fewer. Three is
         * enough to absorb that without scanning the corpus for a common word.
         */
        private const val CANDIDATE_FACTOR = 3
    }
}
