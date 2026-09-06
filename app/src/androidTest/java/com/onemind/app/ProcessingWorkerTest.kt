package com.onemind.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.onemind.app.data.ai.ProviderRestorer
import com.onemind.app.data.events.EventReminderScheduler
import com.onemind.app.data.processing.ProcessingWorker
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.processing.PipelineOutcome
import com.onemind.app.domain.processing.ProcessingPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That a Memory's reminders get scheduled without waiting for the next app start.
 *
 * `scheduleAll()` shipped in v0.1.2 with exactly one caller — application start —
 * while its own KDoc claimed it also ran "after each pipeline run that detects
 * events". Nothing did. Save something happening tomorrow, leave the app running,
 * and the reminder simply never existed. It was the feature's headline promise, and
 * the reason it went unnoticed is that killing and reopening the app — which any
 * developer does constantly — silently repaired it.
 *
 * The worker is the seam because `EventDetectionStage` lives in `domain` and must
 * not know WorkManager exists, the same reasoning that put `ProviderRestorer` here.
 * These tests exist mostly to pin *that the call happens at all*: the defect was
 * never a wrong implementation, it was a missing line, and only a test that names
 * the collaborator can notice a missing line.
 */
@RunWith(AndroidJUnit4::class)
class ProcessingWorkerTest {

    private lateinit var pipeline: ProcessingPipeline
    private lateinit var providerRestorer: ProviderRestorer
    private lateinit var reminderScheduler: EventReminderScheduler

    @Before
    fun setup() {
        pipeline = mockk(relaxed = true)
        providerRestorer = mockk(relaxed = true)
        reminderScheduler = mockk(relaxed = true)
    }

    /**
     * A worker built around the mocks above.
     *
     * `setWorkerFactory` rather than the default reflective one: [ProcessingWorker]
     * is an `@HiltWorker`, so it has no (Context, WorkerParameters) constructor for
     * WorkManager to find, and handing it the collaborators directly is what lets
     * this test assert on them.
     */
    private fun worker(
        memoryId: Long? = MEMORY_ID,
        attempt: Int = 1
    ): ProcessingWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = TestListenableWorkerBuilder.from(context, ProcessingWorker::class.java)
            .setRunAttemptCount(attempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ProcessingWorker(
                    appContext,
                    workerParameters,
                    pipeline,
                    providerRestorer,
                    reminderScheduler
                )
            })

        if (memoryId != null) {
            builder.setInputData(workDataOf(ProcessingWorker.KEY_MEMORY_ID to memoryId))
        }
        return builder.build()
    }

    private fun completed() = PipelineOutcome.Completed(emptyMap())

    @Test
    fun schedulesRemindersAfterThePipelineCompletes() = runTest {
        coEvery { pipeline.run(MEMORY_ID) } returns completed()

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { reminderScheduler.scheduleAll() }
    }

    @Test
    fun schedulesRemindersOnlyAfterTheRunHasFinished() = runTest {
        // Order is the whole point: the event rows the scheduler reads are written by
        // the detection stage during the run. Scheduling first would scan a table that
        // does not yet contain this Memory's events and find nothing to do — the same
        // no-op the defect already was, but harder to see.
        coEvery { pipeline.run(MEMORY_ID) } returns completed()

        worker().doWork()

        coVerifyOrder {
            pipeline.run(MEMORY_ID)
            reminderScheduler.scheduleAll()
        }
    }

    @Test
    fun doesNotScheduleRemindersWhenThePipelineThrew() = runTest {
        coEvery { pipeline.run(MEMORY_ID) } throws IllegalStateException("boom")

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        coVerify(exactly = 0) { reminderScheduler.scheduleAll() }
    }

    @Test
    fun doesNotScheduleRemindersWhenTheMemoryWasAlreadyGone() = runTest {
        // The Memory was deleted while the run sat in the queue. No stage ran, so no
        // event was detected, so there is nothing this run could have to schedule.
        coEvery { pipeline.run(MEMORY_ID) } returns PipelineOutcome.MemoryGone

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { reminderScheduler.scheduleAll() }
    }

    @Test
    fun doesNotScheduleRemindersWhenAnotherRunHadAlreadyClaimedTheMemory() = runTest {
        coEvery { pipeline.run(MEMORY_ID) } returns
            PipelineOutcome.NotEligible(ProcessingState.READY)

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { reminderScheduler.scheduleAll() }
    }

    @Test
    fun doesNotScheduleRemindersForAnInvalidMemoryId() = runTest {
        val result = worker(memoryId = null).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { reminderScheduler.scheduleAll() }
    }

    @Test
    fun enrichmentStillSucceedsWhenSchedulingRemindersFails() = runTest {
        // Enrichment is what the user asked for and it is finished; reminders are a
        // consequence of it. If scheduling throws, letting that reach the catch below
        // would retry a run that already succeeded and, on the last attempt, mark a
        // fully enriched Memory as FAILED — showing a retry button for work that
        // worked. The missed reminders repair themselves: remindersScheduledAt is
        // still null, so the next app start picks them up.
        coEvery { pipeline.run(MEMORY_ID) } returns completed()
        coEvery { reminderScheduler.scheduleAll() } throws IllegalStateException("boom")

        val result = worker(attempt = ProcessingWorker.MAX_ATTEMPTS).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { pipeline.markFailed(any()) }
    }

    private companion object {
        const val MEMORY_ID = 42L
    }
}
