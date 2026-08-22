package com.onemind.app.domain.search

import com.onemind.app.domain.processing.EmbeddingGenerator
import com.onemind.app.domain.repository.DerivedDataRepository
import javax.inject.Inject

/**
 * Semantic search: finds Memories whose meaning is close to the query's, whether or
 * not they share any words with it.
 *
 * This is what makes "things about running AI on phones" find a Memory that says
 * "Deploying quantized LLMs using ONNX Runtime Mobile on Android". Keyword search
 * cannot do that, and semantic search cannot reliably find an exact product name,
 * which is why the locked decisions require both rather than treating one as a
 * replacement for the other.
 *
 * Runs entirely on-device against stored vectors, so it works offline.
 *
 * ## Why a flat scan
 *
 * Every stored vector is compared to the query. At the scale this app targets —
 * thousands of Memories, 100-dimension vectors — that is well under the latency
 * budget, and an approximate index would add a correctness liability (recall
 * silently degrading as vectors are added) in exchange for time we do not need to
 * save. Revisit if the corpus grows by an order of magnitude.
 */
class VectorSearcher @Inject constructor(
    private val embeddingGenerator: EmbeddingGenerator,
    private val derivedDataRepository: DerivedDataRepository
) {

    /**
     * Search for [query] by meaning, best match first.
     *
     * Returns empty rather than failing when the model is unavailable: semantic
     * search is one signal among several, and #28 must be able to fall back to
     * keyword results rather than surfacing an error for a search that partly
     * worked.
     */
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LIMIT,
        minSimilarity: Double = DEFAULT_MIN_SIMILARITY
    ): List<VectorMatch> {
        if (query.isBlank()) return emptyList()

        if (!embeddingGenerator.isReady) {
            val loaded = embeddingGenerator.load()
            if (loaded.isFailure) return emptyList()
        }

        val queryVector = embeddingGenerator.embed(query).getOrElse { return emptyList() }

        val currentModel = embeddingGenerator.modelId

        return derivedDataRepository.getAllEmbeddings()
            // A vector produced by a different model describes a different space.
            // Comparing across models yields a number that looks like a similarity
            // and means nothing, so those rows are skipped rather than scored. They
            // become searchable again once the pipeline re-embeds them.
            .filter { it.modelId == currentModel }
            // Dimension disagreement would throw inside similarity(). It should not
            // be possible given the model filter, but a stored vector is data on
            // disk and a mismatch means something is already wrong — skipping is
            // better than crashing a search.
            .filter { it.vector.size == queryVector.size }
            .map { stored ->
                VectorMatch(
                    memoryId = stored.memoryId,
                    // Cosine runs [-1, 1]; negatives mean "opposed", which for
                    // retrieval is just "irrelevant". Clamping to [0, 1] keeps this
                    // on the same scale as the keyword score so #28 can blend them.
                    score = embeddingGenerator
                        .similarity(queryVector, stored.vector)
                        .toDouble()
                        .coerceIn(0.0, 1.0)
                )
            }
            .filter { it.score >= minSimilarity }
            .sortedByDescending { it.score }
            .take(limit)
    }

    companion object {
        const val DEFAULT_LIMIT = 50

        /**
         * Similarity floor.
         *
         * Set from measurement, not taste. Universal Sentence Encoder was measured
         * on-device during #13 scoring related text around 0.86 and unrelated text
         * around 0.5-0.6, so a threshold in the mid-0.4s admits loosely-related
         * matches while excluding noise. Deliberately permissive here because #28
         * applies the real relevance cut after blending — filtering hard at this
         * layer would discard candidates that keyword evidence could have rescued.
         */
        const val DEFAULT_MIN_SIMILARITY = 0.45
    }
}
