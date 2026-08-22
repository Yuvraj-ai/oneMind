package com.onemind.app.data.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onemind.app.domain.processing.ProcessingPipeline
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

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
    private val pipeline: ProcessingPipeline
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val memoryId = inputData.getLong(KEY_MEMORY_ID, INVALID_MEMORY_ID)
        if (memoryId == INVALID_MEMORY_ID) return Result.failure()

        return try {
            // Every outcome is a success from WorkManager's point of view: a
            // Memory that was deleted, already claimed, or enriched with some
            // stages failing all represent work that is finished, not work to
            // retry. Only an exception escaping the pipeline warrants a retry.
            pipeline.run(memoryId)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                pipeline.markFailed(memoryId)
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
