package com.onemind.app.data.processing

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onemind.app.data.ai.ProviderRestorer
import com.onemind.app.data.events.EventReminderScheduler
import com.onemind.app.domain.processing.PipelineOutcome
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
 *
 * It is also where enrichment's framework-side consequences are triggered — the
 * provider restore before, the reminder scheduling after. Both belong here rather
 * than in the pipeline because the pipeline lives in `domain` and must not know
 * WorkManager or the AI provider registry exist.
 */
@HiltWorker
class ProcessingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: ProcessingPipeline,
    private val providerRestorer: ProviderRestorer,
    private val eventReminderScheduler: EventReminderScheduler
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
            val outcome = pipeline.run(memoryId)

            // Only a run that actually happened can have produced events. Gone and
            // NotEligible mean no stage ran, so there is nothing new to schedule.
            if (outcome is PipelineOutcome.Completed) scheduleReminders()

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

    /**
     * Turn any events this run detected into reminders, now rather than at next
     * app start.
     *
     * Until this existed, [EventReminderScheduler.scheduleAll] had one caller —
     * application start — while claiming in its own documentation to run after each
     * pipeline run too. So saving something happening tomorrow and not restarting
     * the app produced no reminder at all: the feature's headline promise, silently
     * absent, and hidden from anyone who relaunches the app often.
     *
     * Swallowing the failure is deliberate, and it is why this is not simply a line
     * in the block above. Enrichment is what the user asked for and it is already
     * finished; reminders are a consequence of it. Letting a scheduling failure
     * reach the retry branch would re-run a pipeline that succeeded and, on the last
     * attempt, mark a fully enriched Memory FAILED — offering a retry button for
     * work that worked. Nothing is lost by giving up here: `remindersScheduledAt`
     * stays null, so the next app start schedules them.
     */
    private suspend fun scheduleReminders() {
        try {
            eventReminderScheduler.scheduleAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Enrichment finished but scheduling its reminders failed", e)
        }
    }

    companion object {
        const val KEY_MEMORY_ID = "memory_id"
        const val INVALID_MEMORY_ID = -1L

        /** Attempts before a Memory is left in FAILED for the user to retry. */
        const val MAX_ATTEMPTS = 3

        private const val TAG = "ProcessingWorker"
    }
}
