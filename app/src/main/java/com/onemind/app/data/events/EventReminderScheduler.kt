package com.onemind.app.data.events

import android.content.Context
import androidx.work.*
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.domain.events.ReminderPlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the reminders an event has earned into WorkManager jobs.
 *
 * Deliberately thin, in the same spirit as `ProcessingWorker`: *which* reminders an
 * event gets, and how long until each fires, is decided by [ReminderPlanner], which
 * is pure and unit-tested. This class knows only how to enqueue what it is handed.
 * The two used to be one, and the seam is why v0.1.2 could ship a "happening soon"
 * notification that was documented but never sent.
 *
 * Reminders are one-time jobs with an initial delay, so they survive process death
 * and device restart. Tags are per-event so an event's reminders can be cancelled
 * without touching anyone else's.
 */
@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventDao: EventDao
) {

    /**
     * Schedule reminders for all upcoming events that don't have them yet.
     *
     * Called from app start (after the stale-processing sweep) and after each
     * pipeline run that detects events.
     */
    suspend fun scheduleAll() {
        val unscheduled = eventDao.getUnscheduledReminders()
        val now = Instant.now()

        unscheduled.forEach { event ->
            scheduleForEvent(event.id, event.eventTime, event.eventTitle, now)
            eventDao.markRemindersScheduled(event.id, now.toEpochMilli())
        }
    }

    /**
     * Enqueue whatever [ReminderPlanner] says this event has earned.
     *
     * An event can legitimately earn nothing — it is past, or so close that a
     * notification would land in the same breath as the save. That case enqueues
     * no work and is not an error.
     */
    private fun scheduleForEvent(
        eventId: Long,
        eventTimeMillis: Long,
        title: String,
        now: Instant
    ) {
        val workManager = WorkManager.getInstance(context)

        ReminderPlanner.plan(Instant.ofEpochMilli(eventTimeMillis), now).forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInputData(
                    workDataOf(
                        EventReminderWorker.KEY_EVENT_ID to eventId,
                        EventReminderWorker.KEY_EVENT_TITLE to title,
                        EventReminderWorker.KEY_EVENT_TIME to eventTimeMillis,
                        EventReminderWorker.KEY_REMINDER_TYPE to reminder.lead.name
                    )
                )
                .setInitialDelay(reminder.delay.toMillis(), TimeUnit.MILLISECONDS)
                .addTag(TAG_PREFIX + eventId)
                .build()
            workManager.enqueue(request)
        }
    }

    /** Cancel all reminders for an event (e.g. when the Memory is deleted). */
    fun cancelForEvent(eventId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PREFIX + eventId)
    }

    companion object {
        private const val TAG_PREFIX = "event_reminder_"
    }
}
