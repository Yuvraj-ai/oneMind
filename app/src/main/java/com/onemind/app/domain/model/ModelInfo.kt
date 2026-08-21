package com.onemind.app.domain.model

/**
 * Metadata for a downloadable local model.
 */
data class ModelInfo(
    /** Unique identifier for this model */
    val id: String,
    /** Human-readable display name */
    val displayName: String,
    /** Parameter count in billions (e.g. 1.0, 1.5, 2.0) */
    val parameterCountB: Float,
    /** Download size in megabytes */
    val downloadSizeMb: Int,
    /** URL to download the model file */
    val downloadUrl: String,
    /** Quantization format (e.g. "int4", "int8") */
    val quantizationFormat: String,
    /** Minimum RAM required in MB */
    val requiredRamMb: Int,
    /** Capabilities this model supports */
    val capabilities: Set<LlmCapability>,
    /** Model format/runtime (mediapipe, gguf, etc.) */
    val format: ModelFormat = ModelFormat.MEDIAPIPE
)

/**
 * The format/runtime used to load and run the model.
 */
enum class ModelFormat {
    /** Google MediaPipe LLM Inference API (FlatBuffer format) */
    MEDIAPIPE,
    /** llama.cpp GGUF format (via JNI) */
    GGUF
}
