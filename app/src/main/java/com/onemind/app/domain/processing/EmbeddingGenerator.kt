package com.onemind.app.domain.processing

/**
 * Turns text into a vector for semantic search.
 *
 * Named [EmbeddingGenerator] rather than "TextEmbedder" to stay distinct from
 * MediaPipe's own `TextEmbedder` class, which the implementation wraps.
 *
 * Deliberately does not expose a dimension constant. The dimensionality is a
 * property of the loaded model, and hardcoding it is how a stored vector column
 * ends up disagreeing with the vectors in it.
 */
interface EmbeddingGenerator {

    /** Whether the model is loaded and ready. */
    val isReady: Boolean

    /**
     * Dimensionality of the vectors this generator produces, or null before the
     * model is loaded. Read from the model, never assumed.
     */
    val dimensions: Int?

    /** Identifies the model, so a model change can invalidate stored vectors. */
    val modelId: String

    /** Load the model. Idempotent. */
    suspend fun load(): Result<Unit>

    /** Release the model and its native resources. */
    suspend fun unload()

    /**
     * Embed [text].
     *
     * Blank input is a failure rather than a zero vector: a zero vector would sit
     * in the index and match everything weakly, which is worse than absent.
     */
    suspend fun embed(text: String): Result<FloatArray>

    /**
     * Cosine similarity between two vectors of equal length.
     *
     * Lives here because it belongs with the thing that defines the vector space.
     */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) {
            "Cannot compare vectors of different sizes: ${a.size} vs ${b.size}"
        }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0.0 || normB == 0.0) return 0f
        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
    }
}
