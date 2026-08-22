package com.onemind.app.domain.processing

/**
 * Outcome of running a single [ProcessingStage] against a Memory.
 *
 * The distinction between [Empty], [NotSupported] and [Failed] is semantic and
 * load-bearing: an image with no text is [Empty], a text-only model asked for a
 * vision description is [NotSupported], and only a genuine error is [Failed].
 */
sealed class StageResult {

    /** The stage ran and produced useful derived data. */
    data object Success : StageResult()

    /** The stage ran to completion but found nothing useful (a valid outcome). */
    data object Empty : StageResult()

    /** The capability this stage needs is unavailable (not an error). */
    data object NotSupported : StageResult()

    /** The stage was attempted and errored. */
    data class Failed(val reason: String, val cause: Throwable? = null) : StageResult()

    /** The stage had nothing to act on for this Memory (e.g. no images to OCR). */
    data object Skipped : StageResult()
}
