package com.onemind.app.domain.search

import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.Memory

/**
 * Blends keyword and semantic evidence into one ordering.
 *
 * Built here rather than alongside #24 on purpose. With only keyword search in
 * place, a ranking abstraction would have been a guess about a hypothetical second
 * consumer; with two real scoring sources whose distributions genuinely differ, the
 * normalisation problem is concrete and the seam earns itself.
 *
 * ## Why the two scores need normalising
 *
 * They already share a [0, 1] scale by construction — #24 defines coverage that way
 * and #25 clamps cosine into it — but sharing a *range* is not sharing a
 * *distribution*. Cosine similarity over a sentence-embedding model clusters high:
 * unrelated text measured around 0.5 on-device during #13, so 0.5 means "nothing in
 * common" rather than "half a match". Keyword coverage is honest at 0.5 — it means
 * half the query's terms appeared.
 *
 * Blending them raw would let a semantic near-miss outrank a genuine partial keyword
 * hit. So the semantic score is rescaled from its useful band into [0, 1] before
 * weighting, which is the difference between two numbers that look comparable and
 * two that are.
 */
object ResultRanking {

    /**
     * Keyword weight.
     *
     * Lower than semantic because this app exists for people who have forgotten the
     * words they saved. Someone who remembers the exact term is well served either
     * way; someone describing a half-remembered thing is only served by meaning.
     */
    const val KEYWORD_WEIGHT = 0.4

    /** Semantic weight. See [KEYWORD_WEIGHT]. */
    const val SEMANTIC_WEIGHT = 0.6

    /**
     * Bonus for matching a category the query implied.
     *
     * Small, and additive rather than a multiplier, because it is a hint and not
     * evidence. Enough to break a tie between two otherwise equal results; never
     * enough to lift an irrelevant Memory over a relevant one.
     */
    const val CATEGORY_BOOST = 0.05

    /**
     * Cosine below which a match carries no information.
     *
     * From #13's on-device measurement: Universal Sentence Encoder scored unrelated
     * text around 0.5. Everything below this is noise, so the useful band starts
     * here and the score is rescaled from it rather than from zero.
     */
    private const val SEMANTIC_FLOOR = 0.5

    /**
     * Minimum blended score worth showing.
     *
     * The locked decisions are explicit: "No memories found" is preferable to
     * misleading results, and weakly related Memories must not be shown merely to
     * populate the screen. This is the line that enforces that, and it is the reason
     * the searchers below it are permissive — they gather candidates, this decides.
     */
    const val MINIMUM_RELEVANCE = 0.15

    /**
     * Combine candidate sets into one ranked list.
     *
     * A Memory found by both sources appears once, with both contributions counted.
     * Returning it twice would be the most visible possible bug, and scoring it by
     * whichever source happened to be checked first would waste the corroboration —
     * agreement between two independent signals is the strongest evidence available.
     */
    fun rank(
        memories: Map<Long, Memory>,
        keywordMatches: List<KeywordMatch>,
        vectorMatches: List<VectorMatch>,
        impliedCategories: List<Category> = emptyList()
    ): List<SearchResult> {
        val keywordById = keywordMatches.associateBy { it.memoryId }
        val vectorById = vectorMatches.associateBy { it.memoryId }
        val impliedCategoryIds = impliedCategories.map { it.id }.toSet()

        // Union, not intersection: a Memory found by only one signal is still a
        // candidate. Requiring both would discard exactly the cases each method
        // exists to catch — an exact product name has no semantic neighbours, and a
        // paraphrase shares no words.
        return (keywordById.keys + vectorById.keys)
            .mapNotNull { memoryId ->
                // A candidate with no Memory behind it means the index outlived the
                // row. Skipping is right: there is nothing to show.
                val memory = memories[memoryId] ?: return@mapNotNull null

                val keywordScore = keywordById[memoryId]?.score ?: 0.0
                val rawSemantic = vectorById[memoryId]?.score ?: 0.0
                val semanticScore = rescaleSemantic(rawSemantic)

                var combined = (KEYWORD_WEIGHT * keywordScore) + (SEMANTIC_WEIGHT * semanticScore)

                if (impliedCategoryIds.isNotEmpty() &&
                    memory.derived.categories.any { it.id in impliedCategoryIds }
                ) {
                    combined += CATEGORY_BOOST
                }

                SearchResult(
                    memory = memory,
                    score = combined.coerceIn(0.0, 1.0),
                    keywordScore = keywordScore,
                    semanticScore = rawSemantic,
                    matchedText = keywordById[memoryId]?.matchedText
                )
            }
            .filter { it.score >= MINIMUM_RELEVANCE }
            .sortedWith(
                // Score first. Recency breaks ties, because between two equally
                // relevant Memories the more recent one is the likelier target — and
                // an arbitrary tiebreak would make result order unstable between
                // identical searches.
                compareByDescending<SearchResult> { it.score }
                    .thenByDescending { it.memory.createdAt }
            )
    }

    /**
     * Map a cosine score from its useful band into [0, 1].
     *
     * Below [SEMANTIC_FLOOR] contributes nothing. Above it, the remaining range is
     * stretched, so a 0.9 similarity reads as strong rather than as "0.9, much like
     * the 0.6 that means nothing".
     */
    private fun rescaleSemantic(raw: Double): Double {
        if (raw <= SEMANTIC_FLOOR) return 0.0
        return ((raw - SEMANTIC_FLOOR) / (1.0 - SEMANTIC_FLOOR)).coerceIn(0.0, 1.0)
    }
}
