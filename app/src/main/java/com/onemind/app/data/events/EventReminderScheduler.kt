package com.onemind.app.data.events

import android.content.Context
import androidx.work.*
import com.onemind.app.data.local.dao.EventDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules reminder notifications for detected events.
 *
 * Two reminders per event:
 * - 2 days before the event time
 * - 2 hours before the event time
 *
 * If the event is less than 2 days away when detected, only the 2-hour reminder
 * is scheduled. If it's less than 2 hours away, only a "happening soon" is shown
 * immediately.
 *
 * Reminders are WorkManager one-time jobs with initial delay, so they survive
 * process death and device restart. Tags are per-event so they can be cancelled
 * individually if the event is removed.
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

    private fun scheduleForEvent(
        eventId: Long,
        eventTimeMillis: Long,
        title: String,
        now: Instant
    ) {
        val eventTime = Instant.ofEpochMilli(eventTimeMillis)
        val workManager = WorkManager.getInstance(context)

        val twoDaysBefore = eventTime.minus(Duration.ofDays(2))
        val twoHoursBefore = eventTime.minus(Duration.ofHours(2))

        val data = workDataOf(
            EventReminderWorker.KEY_EVENT_ID to eventId,
            EventReminderWorker.KEY_EVENT_TITLE to title,
            EventReminderWorker.KEY_EVENT_TIME to eventTimeMillis
        )

        // 2-day reminder
        if (twoDaysBefore.isAfter(now)) {
            val delay = Duration.between(now, twoDaysBefore).toMillis()
            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInputData(
                    workDataOf(
                        EventReminderWorker.KEY_EVENT_ID to eventId,
                        EventReminderWorker.KEY_EVENT_TITLE to title,
                        EventReminderWorker.KEY_EVENT_TIME to eventTimeMillis,
                        EventReminderWorker.KEY_REMINDER_TYPE to "2_DAYS"
                    )
                )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(TAG_PREFIX + eventId)
                .build()
            workManager.enqueue(request)
        }

        // 2-hour reminder
        if (twoHoursBefore.isAfter(now)) {
            val delay = Duration.between(now, twoHoursBefore).toMillis()
            val request = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInputData(
                    workDataOf(
                        EventReminderWorker.KEY_EVENT_ID to eventId,
                        EventReminderWorker.KEY_EVENT_TITLE to title,
                        EventReminderWorker.KEY_EVENT_TIME to eventTimeMillis,
                        EventReminderWorker.KEY_REMINDER_TYPE to "2_HOURS"
                    )
                )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
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
