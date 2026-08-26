package com.onemind.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.onemind.app.data.ai.ProviderRestorer
import com.onemind.app.data.events.EventReminderScheduler
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.entity.ContentBlockEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.data.processing.ProcessingScheduler
import com.onemind.app.data.processing.ProcessingWorker
import com.onemind.app.data.repository.DerivedDataRepositoryImpl
import com.onemind.app.data.repository.EventRepositoryImpl
import com.onemind.app.data.repository.MemoryRepositoryImpl
import com.onemind.app.domain.events.ReminderLead
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.ExtractedDate
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.processing.ProcessingPipeline
import com.onemind.app.domain.processing.ProcessingStageRegistry
import com.onemind.app.domain.processing.stages.EventDetectionStage
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Detection through to enqueued reminders, in one real pipeline run.
 *
 * `ProcessingWorkerTest` pins that a completed run calls `scheduleAll`, but it
 * mocks both the pipeline and the scheduler, so what it cannot show is that the
 * two halves meet: that a run which detects an event leaves behind a row
 * `scheduleAll` will actually find, and that the reminders exist by the time the
 * worker returns. That composition is the whole of #32 — the defect was never a
 * wrong implementation, it was two correct halves with nothing joining them.
 *
 * Nothing here calls `scheduleAll` directly, and no application start happens.
 * The reminders asserted at the end can only have come from the worker.
 *
 * ## No model required, and why that is not a compromise
 *
 * Events come from `ExtractedDate` rows with `isEventTime = true`, which in
 * production `MetadataExtractionStage` produces with a cloud text model. This test
 * writes that row itself and runs a registry holding only [EventDetectionStage] —
 * which is legitimate rather than a shortcut, because [EventDetectionStage] is not
 * an LLM stage. It reads dates that already exist. Seeding the date stands in for
 * the model's output and leaves every step after it real: real Room, real
 * repositories, real detection, real [EventReminderScheduler], real WorkManager.
 *
 * It also matches how the app behaves for a user with no provider configured:
 * `MetadataExtractionStage` returns early when no model is available and does not
 * clear existing dates, so dates already on a Memory survive a run untouched.
 *
 * The event is three days out, so both scheduled leads have a non-zero delay and
 * stay ENQUEUED — the same reason [EventReminderSchedulerTest] keeps its events
 * far away, since [SynchronousExecutor] would otherwise try to construct an
 * `@HiltWorker` that has no factory here.
 */
@RunWith(AndroidJUnit4::class)
class EventDetectionToReminderTest {

    private lateinit var context: Context
    private lateinit var database: OneMindDatabase
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: EventReminderScheduler
    private lateinit var pipeline: ProcessingPipeline

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, OneMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)

        scheduler = EventReminderScheduler(context, database.eventDao())

        // Everything below this line is the production wiring, constructed by hand
        // because the app has no Hilt test runner. The only stand-in is the stage
        // registry, which holds detection alone: the stages before it either need a
        // model or need image files, and neither is what this test is about.
        val memoryRepository = MemoryRepositoryImpl(
            database.memoryDao(),
            database.derivedDataDao(),
            database.categoryDao(),
            database.searchIndexDao(),
            scheduler,
            mockk<ProcessingScheduler>(relaxed = true)
        )
        val derivedDataRepository = DerivedDataRepositoryImpl(
            database.derivedDataDao(),
            database.categoryDao(),
            database.searchIndexDao()
        )
        val detection = EventDetectionStage(
            EventRepositoryImpl(database.eventDao(), scheduler),
            Clock.systemUTC()
        )

        pipeline = ProcessingPipeline(
            memoryRepository,
            derivedDataRepository,
            ProcessingStageRegistry(setOf(detection))
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun aDetectedEventGetsItsRemindersWithinTheSameRun() = runTest {
        val memoryId = saveMemory("Dentist appointment on Thursday at 10am")
        seedEventTimeDate(memoryId, at = Instant.now().plus(Duration.ofDays(3)))

        val result = worker(memoryId).doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // The run detected the event. Titled from the date's own raw text, which is
        // what `EventDetectionStage` prefers over the Memory's title.
        val event = database.eventDao().getEventsForMemory(memoryId).single()
        assertEquals("Thursday at 10am", event.eventTitle)

        // ...and did not stop there, which is the part v0.1.2 got wrong. A null
        // remindersScheduledAt is exactly the state the old code left behind, waiting
        // for an app start that might be days away.
        assertNotNull(
            "Event was detected but never marked as scheduled",
            event.remindersScheduledAt
        )

        // The jobs themselves, in WorkManager, before anything has restarted.
        listOf(ReminderLead.TWO_DAYS, ReminderLead.TWO_HOURS).forEach { lead ->
            val infos = workManager
                .getWorkInfosForUniqueWork(EventReminderScheduler.uniqueWorkName(event.id, lead))
                .get()

            assertEquals("Expected exactly one $lead reminder", 1, infos.size)
            assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
            // Tagged for the Memory, so #33's delete-time cancel can reach it. Worth
            // asserting here rather than only in the scheduler's own test, because
            // this is the path the tag actually arrives by in production.
            assertTrue(
                "Reminder is not cancellable by Memory",
                infos.single().tags.contains(EventReminderScheduler.memoryTag(memoryId))
            )
        }
    }

    @Test
    fun theMemoryEndsReadyAndNotStuckInProcessing() = runTest {
        val memoryId = saveMemory("Standup on Monday")
        seedEventTimeDate(memoryId, at = Instant.now().plus(Duration.ofDays(3)))

        worker(memoryId).doWork()

        val memory = database.memoryDao().getMemoryById(memoryId)!!.memory
        assertEquals(ProcessingState.READY, memory.processingState)
    }

    @Test
    fun aDateTheModelDidNotCallAnEventTimeSchedulesNothing() = runTest {
        // Guards against this suite proving less than it appears to. If the fixture
        // rather than the pipeline were what produced events, this would still find
        // reminders — a date is seeded, only the flag differs.
        val memoryId = saveMemory("Met Priya, we first worked together in March 2019")
        seedEventTimeDate(
            memoryId,
            at = Instant.now().plus(Duration.ofDays(3)),
            isEventTime = false
        )

        val result = worker(memoryId).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(database.eventDao().getEventsForMemory(memoryId).isEmpty())
        assertEquals(0, database.eventDao().countUpcoming())
    }

    @Test
    fun aPastDateSchedulesNothing() = runTest {
        // "I went to a concert last week" is a Memory about something that already
        // happened. Reminding anyone of it would be worse than saying nothing.
        val memoryId = saveMemory("Concert last week was great")
        seedEventTimeDate(memoryId, at = Instant.now().minus(Duration.ofDays(7)))

        worker(memoryId).doWork()

        assertTrue(database.eventDao().getEventsForMemory(memoryId).isEmpty())
    }

    /**
     * A Memory as the composer would have left it: text, and SAVED awaiting
     * enrichment.
     */
    private suspend fun saveMemory(text: String): Long {
        val now = System.currentTimeMillis()
        return database.memoryDao().insertMemoryWithBlocks(
            MemoryEntity(
                createdAt = now,
                updatedAt = now,
                sourceType = SourceType.MANUAL,
                processingState = ProcessingState.SAVED
            ),
            listOf(
                ContentBlockEntity(
                    memoryId = 0,
                    position = 0,
                    type = ContentType.TEXT,
                    content = text
                )
            )
        )
    }

    /**
     * The one thing a model would otherwise have written.
     *
     * Goes in through the repository rather than the DAO so the production mapper is
     * what shapes the row, and a change to it breaks this test too.
     */
    private suspend fun seedEventTimeDate(
        memoryId: Long,
        at: Instant,
        isEventTime: Boolean = true
    ) {
        DerivedDataRepositoryImpl(
            database.derivedDataDao(),
            database.categoryDao(),
            database.searchIndexDao()
        ).saveDates(
            listOf(
                ExtractedDate(
                    memoryId = memoryId,
                    rawText = "Thursday at 10am",
                    parsedInstant = at,
                    isEventTime = isEventTime
                )
            )
        )
    }

    private fun worker(memoryId: Long): ProcessingWorker =
        TestListenableWorkerBuilder.from(context, ProcessingWorker::class.java)
            .setInputData(workDataOf(ProcessingWorker.KEY_MEMORY_ID to memoryId))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = ProcessingWorker(
                    appContext,
                    workerParameters,
                    pipeline,
                    mockk<ProviderRestorer>(relaxed = true),
                    scheduler
                )
            })
            .build()
}
