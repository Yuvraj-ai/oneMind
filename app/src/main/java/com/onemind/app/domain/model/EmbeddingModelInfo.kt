package com.onemind.app.domain.model

/**
 * Metadata for the embedding model.
 *
 * Not user-configurable. Chosen for efficiency, broad device compatibility, and
 * an ungated download, since oneMind promises no accounts.
 */
data class EmbeddingModelInfo(
    val id: String,
    val displayName: String,
    /** Real download size in megabytes. */
    val downloadSizeMb: Int,
    /** URL that must resolve unauthenticated. */
    val downloadUrl: String,
    /** Dimensionality of the vectors this model produces. */
    val outputDimensions: Int,
    val format: ModelFormat
)
