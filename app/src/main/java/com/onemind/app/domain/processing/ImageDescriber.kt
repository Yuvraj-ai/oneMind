package com.onemind.app.domain.processing

/**
 * Describes what is in an image, when a configured model can.
 *
 * Deliberately narrow. The implementation reaches only for the provider the user
 * actively chose, which is what makes "never upload an image somewhere the user
 * did not ask for" a structural property rather than a rule someone has to
 * remember: there is no API here that could name a different provider.
 *
 * An interface for the same reason as [TextRecognizer]: it keeps
 * [stages.VisionStage] testable on the JVM.
 */
interface ImageDescriber {

    /**
     * Whether the active provider can describe images at all.
     *
     * False is an ordinary state, not an error. A user running a small local
     * text-only model has made a legitimate choice, and the pipeline records that
     * vision was unavailable rather than pretending it failed.
     */
    fun isAvailable(): Boolean

    /**
     * Identifies the provider and model that would produce a description, for
     * recording provenance. Null when nothing is available.
     */
    fun modelIdentifier(): String?

    /**
     * Describe the image at [imagePath].
     *
     * A success carrying blank text is a legitimate outcome. Reserve failure for
     * a genuine problem: an unreadable file, a provider error, a refusal.
     */
    suspend fun describe(imagePath: String, prompt: String): Result<String>
}
