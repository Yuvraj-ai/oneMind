package com.onemind.app.domain.model

/**
 * Capabilities that an LLM provider can report.
 * Used to determine what processing is available.
 */
enum class LlmCapability {
    /** Can generate text from a prompt */
    TEXT_GENERATION,

    /** Can process image inputs (vision model) */
    VISION,

    /** Can generate embeddings (separate from generative) */
    EMBEDDINGS
}
