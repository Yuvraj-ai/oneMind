package com.onemind.app.domain.model

/**
 * Metadata for a downloadable model.
 *
 * [downloadSizeMb] is the real `content-length` of the file, not an estimate.
 * The first version of the registry carried invented sizes that were 1.5-3x too
 * small, which made the RAM-based recommendation meaningless.
 */
data class ModelInfo(
    /** Unique identifier, also the on-disk filename. */
    val id: String,
    val displayName: String,
    /** Parameter count in billions. */
    val parameterCountB: Float,
    /** Real download size in megabytes. */
    val downloadSizeMb: Int,
    /** URL that must resolve unauthenticated. Gated models are unusable here. */
    val downloadUrl: String,
    val quantizationFormat: String,
    val requiredRamMb: Int,
    val capabilities: Set<LlmCapability>,
    val format: ModelFormat
)

/**
 * The runtime a model file needs.
 *
 * Which of these oneMind can actually execute is a moving target; see
 * `docs/research/2026-08-on-device-inference.md` and ADR-0002.
 */
enum class ModelFormat {
    /**
     * MediaPipe Task Bundle (`.task`), run by `com.google.mediapipe:tasks-genai`.
     * That API is in maintenance-only mode, so nothing here executes this yet.
     */
    MEDIAPIPE_TASK,

    /**
     * LiteRT-LM bundle (`.litertlm`), run by
     * `com.google.ai.edge.litertlm:litertlm`, currently `0.0.0-alpha05`.
     * Vision-capable models are distributed only in this format.
     */
    LITERT_LM,

    /**
     * Plain LiteRT / TFLite (`.tflite`), run by `com.google.ai.edge.litert:litert`
     * at a stable `2.2.0`. This is the one format oneMind executes today, and it
     * is what the embedding model uses.
     */
    LITERT
}
