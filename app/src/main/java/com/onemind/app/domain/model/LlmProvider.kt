package com.onemind.app.domain.model

/**
 * Abstraction over any LLM provider (local or cloud).
 * This is the seam through which all AI operations flow.
 */
interface LlmProvider {

    /** Human-readable name of this provider */
    val name: String

    /** Whether the model is currently loaded and ready */
    val isLoaded: Boolean

    /** Report which capabilities this provider supports */
    fun capabilities(): Set<LlmCapability>

    /**
     * Generate text from a prompt.
     * @param prompt The input text/instruction
     * @param maxTokens Maximum tokens to generate
     * @return Generated text, or failure
     */
    suspend fun generateText(prompt: String, maxTokens: Int = 512): Result<String>

    /**
     * Generate an embedding vector from text.
     * Only available if EMBEDDINGS capability is reported.
     * @return Float array of the embedding vector, or failure
     */
    suspend fun generateEmbedding(text: String): Result<FloatArray>

    /**
     * Describe an image given its file path.
     * Only available if VISION capability is reported.
     * @param imagePath Path to the image file
     * @param prompt Optional guiding prompt for the description
     * @return Description text, or failure
     */
    suspend fun describeImage(imagePath: String, prompt: String? = null): Result<String>

    /**
     * Load the model into memory. Must be called before generation.
     */
    suspend fun load()

    /**
     * Unload the model from memory to free resources.
     */
    suspend fun unload()
}
