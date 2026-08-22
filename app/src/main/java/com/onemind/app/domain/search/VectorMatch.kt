package com.onemind.app.domain.search

/** One Memory matched by semantic similarity. */
data class VectorMatch(
    val memoryId: Long,
    /** Cosine similarity, in [0, 1] after clamping. */
    val score: Double
)
