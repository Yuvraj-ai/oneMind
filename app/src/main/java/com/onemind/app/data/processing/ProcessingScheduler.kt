package com.onemind.app.data.processing

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues Memories for background enrichment.
 *
 * Processing is serialised through a single named chain so that saving twenty
 * screenshots at once enriches them one at a time rather than starving the
 * device. Capture never waits on any of this.
 */
@Singleton
class ProcessingScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Queue [memoryId] for enrichment.
     *
     * Work is appended to one serial chain, and additionally tagged with the
     * Memory's own id so a single Memory's pending work can be cancelled without
     * disturbing the others in the chain.
     */
    fun enqueue(memoryId: Long) {
        val request = OneTimeWorkRequestBuilder<ProcessingWorker>()
            .setInputData(
                workDataOf(ProcessingWorker.KEY_MEMORY_ID to memoryId)
            )
            .setConstraints(processingConstraints())
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_DELAY_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_PROCESSING)
            .addTag(memoryTag(memoryId))
            .build()

        // One serial chain: enrichment runs one Memory at a time.
        workManager.enqueueUniqueWork(
            UNIQUE_CHAIN_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Cancel any queued or running enrichment for [memoryId].
     *
     * Cancelling by tag rather than by unique name, because every Memory shares
     * the one serial chain: the tag is what identifies this Memory's work within
     * it.
     */
    fun cancel(memoryId: Long) {
        workManager.cancelAllWorkByTag(memoryTag(memoryId))
    }

    /**
     * Constraints that keep enrichment out of the user's way.
     *
     * Battery-not-low is the meaningful guardrail. Requiring charging or an
     * unmetered network would leave Memories unenriched for hours, which defeats
     * the point of enriching them at all.
     */
    private fun processingConstraints() = Constraints.Builder()
        .setRequiresBatteryNotLow(true)
        .build()

    companion object {
        const val TAG_PROCESSING = "memory_processing"
        private const val UNIQUE_CHAIN_NAME = "memory_processing_chain"
        private const val BACKOFF_DELAY_SECONDS = 30L

        /** Per-Memory tag, used to cancel one Memory's work within the chain. */
        fun memoryTag(memoryId: Long) = "memory_processing_$memoryId"
    }
}
