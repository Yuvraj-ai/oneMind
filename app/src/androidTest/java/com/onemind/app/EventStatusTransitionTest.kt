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
import com.onemind.app.data.repository.EventRepositoryImpl
import com.onemind.app.domain.events.ReminderLead
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

/**
 * That a user's decision about an event is respected by every query that reads one.
 *
 * `EventStatus` is persisted by name into a TEXT column, so growing it costs no
 * migration and — this is the hazard — breaks nothing at compile time. Six SQL string
 * literals across five queries spelled `'UPCOMING'` and `'EXPIRED'` out by hand; each
 * silently became an incomplete enumeration. These tests are what makes that visible.
 */
@RunWith(AndroidJUnit4::class)
class EventStatusTransitionTest {

    private lateinit var database: OneMindDatabase
    private lateinit var dao: EventDao
    private lateinit var memoryDao: MemoryDao

    private var memoryId: Long = 0

    private lateinit var scheduler: EventReminderScheduler
    private lateinit var workManager: WorkManager

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        database = Room.inMemoryDatabaseBuilder(context, OneMindDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = database.eventDao()
        memoryDao = database.memoryDao()

        // Far-future events only, so every reminder keeps a non-zero initial delay and
        // stays ENQUEUED — the same reason EventReminderSchedulerTest does this. A
        // zero delay would have SynchronousExecutor try to construct an @HiltWorker
        // that has no factory here.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        scheduler = EventReminderScheduler(context, dao)

        memoryId = newMemory()
    }

    /** The production wiring, constructed by hand — the app has no Hilt test runner. */
    private fun repository() = EventRepositoryImpl(dao, scheduler)

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

    private suspend fun insertEvent(
        ahead: Duration = Duration.ofDays(3),
        status: EventStatus = EventStatus.UPCOMING,
        title: String = "AI Summit",
        remindersScheduledAt: Long? = null
    ): Long = dao.insert(
        DetectedEventEntity(
            memoryId = memoryId,
            eventTime = Instant.now().plus(ahead).toEpochMilli(),
            eventTitle = title,
            status = status,
            remindersScheduledAt = remindersScheduledAt
        )
    )

    @Test
    fun upcomingListIncludesEventsAddedToTheCalendar() = runTest {
        val id = insertEvent(status = EventStatus.IN_CALENDAR)

        // Exporting to a calendar is not dismissing. The event is still coming up, so
        // it stays where the user can see it.
        assertEquals(listOf(id), dao.observeUpcoming().first().map { it.id })
    }

    @Test
    fun pastListIncludesRejectedEvents() = runTest {
        val id = insertEvent(status = EventStatus.REJECTED)

        assertEquals(listOf(id), dao.observeExpired().first().map { it.id })
        assertTrue(dao.observeUpcoming().first().isEmpty())
    }

    @Test
    fun expireOverdueMovesAnEventTheUserAddedToTheirCalendar() = runTest {
        val id = insertEvent(ahead = Duration.ofDays(-1), status = EventStatus.IN_CALENDAR)

        assertEquals(1, dao.expireOverdue(System.currentTimeMillis()))

        // Left alone this event would sit at the top of the upcoming list forever,
        // showing a date in the past.
        assertEquals(EventStatus.EXPIRED, dao.getEventsForMemory(memoryId).single().status)
        assertEquals(listOf(id), dao.observeExpired().first().map { it.id })
    }

    @Test
    fun expireOverdueLeavesARejectedEventRejected() = runTest {
        insertEvent(ahead = Duration.ofDays(-1), status = EventStatus.REJECTED)

        assertEquals(0, dao.expireOverdue(System.currentTimeMillis()))

        // Deliberate: a rejected event already renders in the past list, and expiring
        // it would silently strip the undo affordance. Undoing it returns it to
        // UPCOMING with a past time, and the next sweep expires it then.
        assertEquals(EventStatus.REJECTED, dao.getEventsForMemory(memoryId).single().status)
    }

    @Test
    fun countUpcomingCountsTheSameRowsTheUpcomingListShows() = runTest {
        insertEvent(status = EventStatus.UPCOMING)
        insertEvent(status = EventStatus.IN_CALENDAR, title = "Dentist")
        insertEvent(status = EventStatus.REJECTED, title = "Standup")

        assertEquals(2, dao.countUpcoming())
        assertEquals(2, dao.observeUpcoming().first().size)
    }

    @Test
    fun updateStatusWritesTheGivenStatus() = runTest {
        val id = insertEvent()

        dao.updateStatus(id, EventStatus.REJECTED)

        assertEquals(EventStatus.REJECTED, dao.getEventsForMemory(memoryId).single().status)
    }

    @Test
    fun restoreToUpcomingAlsoForgetsThatRemindersWereScheduled() = runTest {
        val id = insertEvent(status = EventStatus.REJECTED, remindersScheduledAt = 1_000L)

        dao.restoreToUpcoming(id)

        val row = dao.getEventsForMemory(memoryId).single()
        assertEquals(EventStatus.UPCOMING, row.status)
        // Both halves matter. Rejecting cancelled the enqueued jobs but left the mark
        // set; without clearing it, getUnscheduledReminders would skip this event
        // forever and undo would hand back an event nothing will ever remind about.
        assertNull(row.remindersScheduledAt)
        assertEquals(listOf(id), dao.getUnscheduledReminders().map { it.id })
    }

    @Test
    fun unscheduledRemindersStillIgnoresEverythingButPlainUpcoming() = runTest {
        insertEvent(status = EventStatus.IN_CALENDAR)
        insertEvent(status = EventStatus.REJECTED, title = "Dentist")

        // Left on UPCOMING alone on purpose: an event is only *owed* reminders while
        // it is still plain upcoming. An IN_CALENDAR event keeps the reminders it
        // already has (they are never cancelled), it just does not earn new ones.
        assertTrue(dao.getUnscheduledReminders().isEmpty())
    }

    // --- the repository verbs ---------------------------------------------

    @Test
    fun rejectingMovesAnEventOutOfUpcomingAndIntoThePastList() = runTest {
        val repository = repository()
        val id = insertEvent()

        repository.reject(id)

        assertTrue(repository.observeUpcoming().first().isEmpty())
        assertEquals(listOf(id), repository.observeExpired().first().map { it.id })
    }

    @Test
    fun undoingARejectionBringsTheEventBack() = runTest {
        val repository = repository()
        val id = insertEvent()
        repository.reject(id)

        repository.undoReject(id)

        assertEquals(listOf(id), repository.observeUpcoming().first().map { it.id })
        assertTrue(repository.observeExpired().first().isEmpty())
    }

    @Test
    fun undoingARejectionLetsTheEventEarnRemindersAgain() = runTest {
        val repository = repository()
        val id = insertEvent(remindersScheduledAt = 1_000L)
        repository.reject(id)

        repository.undoReject(id)

        // Rejecting cancelled the jobs. If undo left the mark in place, nothing would
        // ever re-arm them and the user would get an event back with no reminders.
        assertEquals(listOf(id), dao.getUnscheduledReminders().map { it.id })
    }

    @Test
    fun markingAnEventAddedToCalendarKeepsItUpcoming() = runTest {
        val repository = repository()
        val id = insertEvent()

        repository.markAddedToCalendar(id)

        assertEquals(listOf(id), repository.observeUpcoming().first().map { it.id })
        assertEquals(
            EventStatus.IN_CALENDAR,
            dao.getEventsForMemory(memoryId).single().status
        )
    }

    // --- reprocessing ------------------------------------------------------

    /** The same event, re-derived, as the pipeline would hand it back. */
    private fun rederived(at: Instant, title: String = "AI Summit") = DetectedEvent(
        memoryId = memoryId,
        eventTime = at,
        eventTitle = title
    )

    @Test
    fun reprocessingKeepsARejectedEventRejected() = runTest {
        val repository = repository()
        val at = Instant.now().plus(Duration.ofDays(3))
        val id = dao.insert(
            DetectedEventEntity(
                memoryId = memoryId,
                eventTime = at.toEpochMilli(),
                eventTitle = "AI Summit"
            )
        )
        repository.reject(id)

        repository.replaceEventsForMemory(memoryId, listOf(rederived(at)))

        // Without this the user's rejection is undone by a retry they did not connect
        // to it, and the event they declined starts reminding them again.
        assertEquals(EventStatus.REJECTED, dao.getEventsForMemory(memoryId).single().status)
    }

    @Test
    fun reprocessingKeepsTheRemindersScheduledMark() = runTest {
        val repository = repository()
        val at = Instant.now().plus(Duration.ofDays(3))
        dao.insert(
            DetectedEventEntity(
                memoryId = memoryId, eventTime = at.toEpochMilli(),
                eventTitle = "AI Summit", remindersScheduledAt = 1_000L
            )
        )

        repository.replaceEventsForMemory(memoryId, listOf(rederived(at)))

        // Carried with the status, and for the same reason: dropping it would make
        // scheduleAll() enqueue a second set of reminders under new ids for an event
        // that already has them.
        assertEquals(1_000L, dao.getEventsForMemory(memoryId).single().remindersScheduledAt)
        assertTrue(dao.getUnscheduledReminders().isEmpty())
    }

    @Test
    fun anEventAtANewTimeIsTreatedAsNew() = runTest {
        val repository = repository()
        val original = Instant.now().plus(Duration.ofDays(3))
        val id = dao.insert(
            DetectedEventEntity(
                memoryId = memoryId, eventTime = original.toEpochMilli(),
                eventTitle = "AI Summit", remindersScheduledAt = 1_000L
            )
        )
        repository.reject(id)

        val moved = Instant.now().plus(Duration.ofDays(5))
        repository.replaceEventsForMemory(memoryId, listOf(rederived(moved)))

        // The user rejected an event on the 3rd. This is one on the 5th — the text
        // changed under it, so it is a different event and inherits nothing.
        val row = dao.getEventsForMemory(memoryId).single()
        assertEquals(EventStatus.UPCOMING, row.status)
        assertNull(row.remindersScheduledAt)
    }

    @Test
    fun anEmptyReplacementStillClearsTheMemory() = runTest {
        val repository = repository()
        val id = insertEvent()
        repository.reject(id)

        repository.replaceEventsForMemory(memoryId, emptyList())

        // A Memory whose dates were edited away must end up with no events. Carrying
        // status forward must not turn this into an insert-only path.
        assertTrue(dao.getEventsForMemory(memoryId).isEmpty())
    }

    @Test
    fun onlyTheMatchingEventInheritsAStatus() = runTest {
        val repository = repository()
        val kept = Instant.now().plus(Duration.ofDays(3))
        val other = Instant.now().plus(Duration.ofDays(4))
        val keptId = dao.insert(
            DetectedEventEntity(
                memoryId = memoryId,
                eventTime = kept.toEpochMilli(),
                eventTitle = "AI Summit"
            )
        )
        repository.reject(keptId)

        repository.replaceEventsForMemory(
            memoryId,
            listOf(rederived(kept), rederived(other, title = "Dentist"))
        )

        val byTime = dao.getEventsForMemory(memoryId).associateBy { it.eventTime }
        assertEquals(EventStatus.REJECTED, byTime[kept.toEpochMilli()]!!.status)
        assertEquals(EventStatus.UPCOMING, byTime[other.toEpochMilli()]!!.status)
    }

    // --- reminders ---------------------------------------------------------

    @Test
    fun rejectingCancelsTheRemindersAlreadyEnqueued() = runTest {
        val repository = repository()
        val id = insertEvent(ahead = Duration.ofDays(10))
        scheduler.scheduleAll()

        repository.reject(id)

        // The status alone only stops *new* reminders. Without the cancel, these fire
        // days later about something the user just declined.
        listOf(ReminderLead.TWO_DAYS, ReminderLead.TWO_HOURS).forEach { lead ->
            assertEquals(
                "lead $lead",
                WorkInfo.State.CANCELLED,
                workManager.getWorkInfosForUniqueWork(
                    EventReminderScheduler.uniqueWorkName(id, lead)
                ).get().single().state
            )
        }
    }
}
