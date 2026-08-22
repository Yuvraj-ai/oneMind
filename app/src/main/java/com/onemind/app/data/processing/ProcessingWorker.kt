package com.onemind.app.data.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onemind.app.data.ai.ProviderRestorer
import com.onemind.app.domain.processing.ProcessingPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * WorkManager entry point for the Processing Pipeline.
 *
 * Deliberately thin: it unwraps the memory id, hands off to
 * [ProcessingPipeline], and decides only whether the attempt is worth retrying.
 * All orchestration behaviour lives in the pipeline, where it is testable
 * without the framework.
 */
@HiltWorker
class ProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: ProcessingPipeline,
    private val providerRestorer: ProviderRestorer
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val memoryId = inputData.getLong(KEY_MEMORY_ID, INVALID_MEMORY_ID)
        if (memoryId == INVALID_MEMORY_ID) return Result.failure()

        // A worker is usually started in a fresh process where no screen has run, so
        // nothing has configured a provider. Without this, every stage that needs a
        // model would record NOT_SUPPORTED even though the user has one set up.
        // Idempotent, so calling it per run costs nothing when it is already active.
        providerRestorer.restore()

        return try {
            // Every outcome is a success from WorkManager's point of view: a
            // Memory that was deleted, already claimed, or enriched with some
            // stages failing all represent work that is finished, not work to
            // retry. Only an exception escaping the pipeline warrants a retry.
            pipeline.run(memoryId)
            Result.success()
        } catch (e: CancellationException) {
            // Cancellation is not failure. Rethrowing lets WorkManager reschedule,
            // and — critically — keeps this out of the branch below, which would
            // otherwise record a spurious FAILED for work that was merely stopped.
            throw e
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                // NonCancellable because this is the write that gives the user a way
                // back. If the worker is being torn down as the last attempt fails,
                // a cancellable write would silently not happen and the Memory would
                // be left mid-pipeline with no retry affordance.
                withContext(NonCancellable) { pipeline.markFailed(memoryId) }
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_MEMORY_ID = "memory_id"
        const val INVALID_MEMORY_ID = -1L

        /** Attempts before a Memory is left in FAILED for the user to retry. */
        const val MAX_ATTEMPTS = 3
    }
}
