package com.onemind.app.domain.processing

/**
 * Reads text out of an image.
 *
 * An interface rather than a direct ML Kit call so [stages.OcrStage] can be
 * tested on the JVM. The stage's interesting behaviour is how it aggregates many
 * per-image outcomes into one stage result, and that is worth testing without an
 * emulator in the loop.
 */
interface TextRecognizer {

    /**
     * Recognise text in the image at [imagePath].
     *
     * A successful result with blank text is a legitimate outcome, not an error:
     * a photo of a mountain contains no text. Reserve failure for a genuine
     * problem, such as an image that cannot be decoded.
     */
    suspend fun recognize(imagePath: String): Result<String>
}
