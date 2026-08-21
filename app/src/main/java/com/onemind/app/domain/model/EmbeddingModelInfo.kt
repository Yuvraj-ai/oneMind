package com.onemind.app.domain.model

/**
 * Metadata for the team-selected embedding model.
 * Not user-configurable — chosen for efficiency and broad device compatibility.
 */
data class EmbeddingModelInfo(
    /** Unique identifier */
    val id: String,
    /** Human-readable name */
    val displayName: String,
    /** Download size in MB */
    val downloadSizeMb: Int,
    /** URL to download */
    val downloadUrl: String,
    /** Output vector dimensions (e.g. 384) */
    val outputDimensions: Int,
    /** Model format */
    val format: ModelFormat = ModelFormat.MEDIAPIPE
)
