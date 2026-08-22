package com.onemind.app.domain.processing

/**
 * The persisted state of one derived-data record.
 *
 * Distinct from [StageResult], which is the transient value a stage *returns*
 * to the pipeline. This is what gets *stored* alongside the derived data, so the
 * UI can tell the user why a field is empty. The two are related but not 1:1:
 * [StageResult.Skipped] never reaches storage (there is nothing to store a status
 * against), and [PROCESSING] is never returned by a stage.
 */
enum class StageStatus {
    /** Ran and produced useful derived data. */
    SUCCESS,

    /** Ran to completion and found nothing. A photo of a mountain has no text. */
    EMPTY,

    /** The capability was unavailable. A text-only model cannot describe images. */
    NOT_SUPPORTED,

    /** Work is in flight. */
    PROCESSING,

    /** Attempted and errored. */
    FAILED
}

/**
 * Map a stage's return value onto the status to persist.
 *
 * Kept here as the single place the two vocabularies meet, so they cannot drift.
 * Returns null for [StageResult.Skipped]: there was nothing to act on, so there
 * is no record to write a status against.
 */
fun StageResult.toStatus(): StageStatus? = when (this) {
    StageResult.Success -> StageStatus.SUCCESS
    StageResult.Empty -> StageStatus.EMPTY
    StageResult.NotSupported -> StageStatus.NOT_SUPPORTED
    is StageResult.Failed -> StageStatus.FAILED
    StageResult.Skipped -> null
}
