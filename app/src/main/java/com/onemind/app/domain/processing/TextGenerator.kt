package com.onemind.app.domain.processing

/**
 * Generates text from a prompt using whichever provider the user configured.
 *
 * The shared seam for every stage that needs a language model: metadata
 * extraction, categorisation and summarisation all run through this. Same shape
 * as [ImageDescriber] and for the same two reasons — it keeps the stages testable
 * on the JVM, and the implementation's only route to a provider is the one the
 * user chose, so nothing can be sent somewhere they did not ask for.
 */
interface TextGenerator {

    /**
     * Whether a generative provider is configured and able to run.
     *
     * False is an ordinary state. With local inference deferred (ADR-0002), a
     * user who configures no provider gets capture, OCR and semantic search, and
     * stages that need this record NOT_SUPPORTED rather than failing.
     */
    fun isAvailable(): Boolean

    /** Identifies the provider and model, for recording provenance. */
    fun modelIdentifier(): String?

    /**
     * Run [prompt] and return the raw response.
     *
     * No parsing or validation happens here. Callers own interpreting the
     * response, because what counts as malformed depends on what was asked for.
     */
    suspend fun generate(prompt: String, maxTokens: Int = DEFAULT_MAX_TOKENS): Result<String>

    companion object {
        const val DEFAULT_MAX_TOKENS = 512
    }
}
