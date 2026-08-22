package com.onemind.app.domain.processing

import com.onemind.app.domain.model.Memory

/**
 * A single enrichment step in the Processing Pipeline.
 *
 * Stages are pluggable: the pipeline runs whatever stages are registered, in
 * [StageId] order, and a failing stage never aborts the ones behind it. Each
 * stage owns persisting its own derived data.
 */
interface ProcessingStage {

    /** Which stage this is. Also fixes its position in the pipeline. */
    val id: StageId

    /**
     * Enrich [memory], persisting whatever derived data this stage produces.
     *
     * Implementations should not throw: translate errors into
     * [StageResult.Failed] so the pipeline can carry on. The pipeline contains
     * escaping exceptions anyway, but a stage that reports its own failure gives
     * a better reason.
     */
    suspend fun process(memory: Memory): StageResult
}
