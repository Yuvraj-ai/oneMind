package com.onemind.app.domain.processing

import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.repository.DerivedDataRepository
import com.onemind.app.domain.repository.MemoryRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the ordered chain of [ProcessingStage]s against one Memory and drives its
 * state through PROCESSING to READY (or FAILED).
 *
 * This is the seam the pipeline is tested at: it holds all the orchestration
 * behaviour and knows nothing about WorkManager, so a test can drive a full
 * pipeline run without the Android framework.
 */
@Singleton
class ProcessingPipeline @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val derivedDataRepository: DerivedDataRepository,
    private val stages: ProcessingStageRegistry
) {

    /**
     * Enrich the Memory identified by [memoryId].
     *
     * Every registered stage runs, in order, regardless of what the stages
     * before it returned: a failing stage degrades that one enrichment rather
     * than the whole Memory. The Memory lands on READY as long as the pipeline
     * itself completed, and on FAILED only when it could not run at all.
     */
    suspend fun run(memoryId: Long): PipelineOutcome {
        val memory = memoryRepository.getMemoryById(memoryId)
            ?: return PipelineOutcome.MemoryGone

        // A Memory arrives here as SAVED (fresh), EDITED (reprocessing), or
        // FAILED (user-requested retry). Anything else means another run already
        // claimed it.
        val entryState = memory.processingState
        if (entryState !in ELIGIBLE_ENTRY_STATES) {
            return PipelineOutcome.NotEligible(entryState)
        }

        memoryRepository.transitionState(memoryId, ProcessingState.PROCESSING)

        // Content changed, so the existing inferences describe text that no
        // longer exists. Clearing here rather than in the composer means every
        // capture path gets it, including the ones not built yet.
        if (entryState == ProcessingState.EDITED) {
            derivedDataRepository.clearDerivedData(memoryId)
        }

        val results = mutableMapOf<StageId, StageResult>()
        for (stage in stages.all()) {
            // Re-read between stages so each stage sees the derived data the
            // previous ones persisted.
            val current = memoryRepository.getMemoryById(memoryId)
                ?: return PipelineOutcome.MemoryGone

            results[stage.id] = try {
                stage.process(current)
            } catch (e: CancellationException) {
                // Not a stage failure. CancellationException extends
                // IllegalStateException, so the generic catch below would swallow it,
                // record the stage as Failed, and let the loop carry on running
                // stages inside a coroutine that has already been cancelled.
                throw e
            } catch (e: Exception) {
                StageResult.Failed(
                    reason = e.message ?: "Stage ${stage.id} threw ${e::class.simpleName}",
                    cause = e
                )
            }
        }

        memoryRepository.transitionState(memoryId, ProcessingState.READY)
        return PipelineOutcome.Completed(results)
    }

    /**
     * Mark a Memory as FAILED because the pipeline could not complete.
     * Used by the worker when it exhausts its retries.
     */
    suspend fun markFailed(memoryId: Long) {
        val memory = memoryRepository.getMemoryById(memoryId) ?: return
        if (memory.processingState == ProcessingState.PROCESSING) {
            memoryRepository.transitionState(memoryId, ProcessingState.FAILED)
        }
    }

    companion object {
        /**
         * States a Memory may be in when the pipeline picks it up: freshly
         * committed, edited and awaiting re-enrichment, or failed and retried by
         * the user. The state machine allows PROCESSING from all three.
         */
        val ELIGIBLE_ENTRY_STATES = setOf(
            ProcessingState.SAVED,
            ProcessingState.EDITED,
            ProcessingState.FAILED,
            // Also PROCESSING, which reads like a contradiction and is not. A worker
            // killed mid-run — process death, or WorkManager stopping it because the
            // battery dropped — leaves the Memory claimed by a run that no longer
            // exists. Treating that as ineligible made the state terminal: the retry
            // returned NotEligible, the worker called that success, and the user had
            // no route back short of clearing app data.
            ProcessingState.PROCESSING
        )
    }
}

/**
 * What happened across a whole pipeline run.
 */
sealed class PipelineOutcome {

    /** The pipeline ran every stage. Individual stages may still have failed. */
    data class Completed(val stageResults: Map<StageId, StageResult>) : PipelineOutcome() {

        val failedStages: List<StageId>
            get() = stageResults.filterValues { it is StageResult.Failed }.keys.toList()

        val allSucceeded: Boolean
            get() = stageResults.values.none { it is StageResult.Failed }
    }

    /** The Memory was deleted while the run was queued. */
    data object MemoryGone : PipelineOutcome()

    /** The Memory was not in a state that accepts processing. */
    data class NotEligible(val state: ProcessingState) : PipelineOutcome()
}
