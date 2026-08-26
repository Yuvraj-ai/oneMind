package com.onemind.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.data.local.OneMindDatabase
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.local.entity.DetectedEventEntity
import com.onemind.app.data.local.entity.MemoryEntity
import com.onemind.app.data.repository.EventRepositoryImpl
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

    @Before
    fun setup() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OneMindDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.eventDao()
        memoryDao = database.memoryDao()
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
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()

        repository.reject(id)

        assertTrue(repository.observeUpcoming().first().isEmpty())
        assertEquals(listOf(id), repository.observeExpired().first().map { it.id })
    }

    @Test
    fun undoingARejectionBringsTheEventBack() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()
        repository.reject(id)

        repository.undoReject(id)

        assertEquals(listOf(id), repository.observeUpcoming().first().map { it.id })
        assertTrue(repository.observeExpired().first().isEmpty())
    }

    @Test
    fun undoingARejectionLetsTheEventEarnRemindersAgain() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent(remindersScheduledAt = 1_000L)
        repository.reject(id)

        repository.undoReject(id)

        // Rejecting cancelled the jobs. If undo left the mark in place, nothing would
        // ever re-arm them and the user would get an event back with no reminders.
        assertEquals(listOf(id), dao.getUnscheduledReminders().map { it.id })
    }

    @Test
    fun markingAnEventAddedToCalendarKeepsItUpcoming() = runTest {
        val repository = EventRepositoryImpl(dao)
        val id = insertEvent()

        repository.markAddedToCalendar(id)

        assertEquals(listOf(id), repository.observeUpcoming().first().map { it.id })
        assertEquals(
            EventStatus.IN_CALENDAR,
            dao.getEventsForMemory(memoryId).single().status
        )
    }
}
