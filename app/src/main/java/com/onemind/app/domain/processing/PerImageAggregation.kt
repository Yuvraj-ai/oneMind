package com.onemind.app.domain.processing

/**
 * Collapse the per-image outcomes of an image stage into the single result the
 * pipeline sees.
 *
 * Shared by OCR and vision, which face the identical question: a Memory holding
 * five images produces five outcomes, and the pipeline needs one. Any later
 * per-image stage inherits the same rule for free, and more importantly cannot
 * quietly adopt a different one.
 *
 * The rule, in words: partial success is success, because a Memory that gained
 * something genuinely gained something. Only a clean sweep of failures is a stage
 * failure. Everything running without producing anything is [StageResult.Empty],
 * which is an answer rather than a fault.
 *
 * @param statuses one status per image, in any order
 * @param stageLabel names the work in the failure message, e.g. "OCR"
 */
fun aggregatePerImageStatuses(
    statuses: List<StageStatus>,
    stageLabel: String
): StageResult = when {
    statuses.isEmpty() -> StageResult.Skipped
    statuses.any { it == StageStatus.SUCCESS } -> StageResult.Success
    statuses.all { it == StageStatus.FAILED } ->
        StageResult.Failed("$stageLabel failed for all ${statuses.size} image(s)")
    else -> StageResult.Empty
}
