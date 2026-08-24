package com.onemind.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.onemind.app.data.events.EventReminderScheduler
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.DetectedEventEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.domain.events.ReminderLead
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * That re-running `scheduleAll` converges instead of accumulating.
 *
 * The unit tests pin the reminder *names*; this pins what WorkManager does with
 * them, which is the half that no amount of pure logic can prove. It exists because
 * v0.1.2 used a plain `enqueue`: the mark-scheduled write happens after the enqueue
 * and cannot be transactional with it, so a process death between the two left the
 * event looking unscheduled and got its reminders enqueued a second time on next
 * boot. The user got each notification twice and there was nothing in the code
 * saying that could not happen.
 *
 * Events here are always far in the future, so every reminder carries a non-zero
 * initial delay and stays ENQUEUED. That is deliberate: it keeps the test about
 * enqueueing, and avoids the [SynchronousExecutor] immediately trying to construct
 * an `@HiltWorker` that has no factory in this test.
 */
@RunWith(AndroidJUnit4::class)
class EventReminderSchedulerTest {

    private lateinit var database: OneMindDatabase
    private lateinit var eventDao: EventDao
    private lateinit var memoryDao: MemoryDao
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: EventReminderScheduler

    private var memoryId: Long = 0

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        database = Room.inMemoryDatabaseBuilder(context, OneMindDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        eventDao = database.eventDao()
        memoryDao = database.memoryDao()

        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build()
        )
        workManager = WorkManager.getInstance(context)

        scheduler = EventReminderScheduler(context, eventDao)

        // Events have an FK to a Memory, and the whole point of an event is that it
        // is a lens on one, so there is no such thing as a standalone fixture here.
        memoryId = newMemory()
    }

    @After
    fun teardown() {
        database.close()
    }

    private suspend fun newMemory(): Long = memoryDao.insertMemoryWithBlocks(
        MemoryEntity(
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sourceType = SourceType.MANUAL,
            processingState = ProcessingState.SAVED
        ),
        emptyList()
    )

    /** An event [ahead] from now, with no reminders scheduled yet. */
    private suspend fun insertEvent(ahead: Duration, ownedBy: Long = memoryId): Long =
        eventDao.insert(
            DetectedEventEntity(
                memoryId = ownedBy,
                eventTime = Instant.now().plus(ahead).toEpochMilli(),
                eventTitle = "AI Summit",
                remindersScheduledAt = null
            )
        )

    private fun jobsFor(eventId: Long, lead: ReminderLead): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(
            EventReminderScheduler.uniqueWorkName(eventId, lead)
        ).get()

    @Test
    fun scheduleAll_enqueuesBothLeadsForADistantEvent() = runTest {
        val eventId = insertEvent(Duration.ofDays(10))

        scheduler.scheduleAll()

        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_DAYS).size)
        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_HOURS).size)
    }

    @Test
    fun scheduleAll_marksTheEventSoASecondRunDoesNothing() = runTest {
        val eventId = insertEvent(Duration.ofDays(10))

        scheduler.scheduleAll()
        assertTrue(eventDao.getUnscheduledReminders().isEmpty())

        scheduler.scheduleAll()

        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_DAYS).size)
        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_HOURS).size)
    }

    @Test
    fun scheduleAll_doesNotDoubleEnqueueWhenTheMarkWriteWasLost() = runTest {
        val eventId = insertEvent(Duration.ofDays(10))
        scheduler.scheduleAll()

        // The defect's exact shape: the enqueue landed, the mark did not, because the
        // process died between the two. Re-inserting the row under the same id with a
        // null mark is that state.
        val scheduled = eventDao.getEventsForMemory(memoryId).single()
        eventDao.insert(scheduled.copy(remindersScheduledAt = null))
        assertEquals(1, eventDao.getUnscheduledReminders().size)

        scheduler.scheduleAll()

        // Two jobs total, not four. Under the old plain enqueue this was four, and
        // the user heard about the event twice.
        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_DAYS).size)
        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_HOURS).size)
    }

    @Test
    fun scheduleAll_keepsDifferentEventsSeparate() = runTest {
        val first = insertEvent(Duration.ofDays(10))
        val second = insertEvent(Duration.ofDays(20))

        scheduler.scheduleAll()

        // Names carry the event id, so one event's reminders must not have evicted
        // the other's.
        assertEquals(1, jobsFor(first, ReminderLead.TWO_HOURS).size)
        assertEquals(1, jobsFor(second, ReminderLead.TWO_HOURS).size)
    }

    @Test
    fun scheduleAll_enqueuesOnlyTheTwoHourLeadForAnEventInsideTwoDays() = runTest {
        val eventId = insertEvent(Duration.ofHours(6))

        scheduler.scheduleAll()

        assertTrue(jobsFor(eventId, ReminderLead.TWO_DAYS).isEmpty())
        assertEquals(1, jobsFor(eventId, ReminderLead.TWO_HOURS).size)
    }

    @Test
    fun cancelForMemory_cancelsBothLeadsOfTheMemorysEvent() = runTest {
        val eventId = insertEvent(Duration.ofDays(10))
        scheduler.scheduleAll()

        scheduler.cancelForMemory(memoryId)

        // `single()` rather than a predicate over the list: an empty list would make
        // any "all cancelled" assertion vacuously true, which is how a cancel that
        // matched nothing would look like a passing test.
        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(eventId, ReminderLead.TWO_DAYS).single().state
        )
        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(eventId, ReminderLead.TWO_HOURS).single().state
        )
    }

    @Test
    fun cancelForMemory_cancelsEveryEventThatMemoryHas() = runTest {
        // One screenshot can mention two dates, so one Memory can own several events.
        val first = insertEvent(Duration.ofDays(10))
        val second = insertEvent(Duration.ofDays(20))
        scheduler.scheduleAll()

        scheduler.cancelForMemory(memoryId)

        // Both events are more than two days out, so all four jobs exist and all four
        // must go.
        listOf(first, second).forEach { eventId ->
            listOf(ReminderLead.TWO_DAYS, ReminderLead.TWO_HOURS).forEach { lead ->
                assertEquals(
                    "lead $lead of event $eventId",
                    WorkInfo.State.CANCELLED,
                    jobsFor(eventId, lead).single().state
                )
            }
        }
    }

    @Test
    fun cancelForMemory_leavesAnotherMemorysRemindersAlone() = runTest {
        val mine = insertEvent(Duration.ofDays(10))
        val theirs = insertEvent(Duration.ofDays(10), ownedBy = newMemory())
        scheduler.scheduleAll()

        scheduler.cancelForMemory(memoryId)

        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(mine, ReminderLead.TWO_HOURS).single().state
        )
        assertEquals(
            WorkInfo.State.ENQUEUED,
            jobsFor(theirs, ReminderLead.TWO_HOURS).single().state
        )
    }

    @Test
    fun cancelForMemory_stillWorksAfterTheEventRowsHaveCascadedAway() = runTest {
        val eventId = insertEvent(Duration.ofDays(10))
        scheduler.scheduleAll()

        // This is the order deletion actually happens in: the Memory row goes, the
        // event rows cascade with it, and only then is there anything to cancel. A
        // cancel that had to look up "which events did this Memory have" would find
        // none and silently leave the notifications enqueued — which is precisely
        // what shipped.
        memoryDao.deleteMemory(memoryId)
        assertTrue(eventDao.getEventsForMemory(memoryId).isEmpty())

        scheduler.cancelForMemory(memoryId)

        assertEquals(
            WorkInfo.State.CANCELLED,
            jobsFor(eventId, ReminderLead.TWO_HOURS).single().state
        )
    }
}
