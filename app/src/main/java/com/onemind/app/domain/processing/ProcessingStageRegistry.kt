package com.onemind.app.domain.processing

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The ordered list of stages the pipeline runs.
 *
 * Adding an enrichment to oneMind means implementing [ProcessingStage] and
 * binding it into the Hilt set that feeds this registry; the pipeline itself
 * never changes. Order comes from [StageId], so it cannot drift out of step with
 * the set of registered stages.
 */
@Singleton
class ProcessingStageRegistry @Inject constructor(
    private val stages: Set<@JvmSuppressWildcards ProcessingStage>
) {
    /** Stages in execution order. */
    fun all(): List<ProcessingStage> = stages.sortedBy { it.id.ordinal }
}
