package com.onemind.app.data.events

import android.content.Context
import androidx.work.*
import com.onemind.app.data.local.dao.EventDao
import com.onemind.app.domain.events.ReminderLead
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
     * Each reminder goes in as unique work named for its event and lead, replacing
     * anything already under that name. The mark-scheduled write in [scheduleAll]
     * happens after the enqueue and is not transactional with it — it cannot be,
     * across a database and WorkManager — so a process death between the two leaves
     * the event looking unscheduled and gets it enqueued again on next boot. With a
     * deterministic name that second pass converges on the same two jobs instead of
     * stacking a second pair, which is what made reminders fire twice. Same reason
     * `ProcessingScheduler.enqueue` names its chain.
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

            workManager.enqueueUniqueWork(
                uniqueWorkName(eventId, reminder.lead),
                // REPLACE rather than KEEP: a re-run means the event's time may have
                // been re-derived, so the delay in the pending job can be stale.
                // Keeping the old one would remind about a time that changed.
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    /** Cancel all reminders for an event (e.g. when the Memory is deleted). */
    fun cancelForEvent(eventId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG_PREFIX + eventId)
    }

    companion object {
        private const val TAG_PREFIX = "event_reminder_"

        /**
         * The name one event's one reminder is enqueued under.
         *
         * Both parts matter, and the separator between them does too. Without the
         * lead, an event's two reminders would replace each other and the user would
         * get one of the two they were promised. Without a separator,
         * `event_reminder_1` + `TWO_HOURS` and `event_reminder_1T` + `WO_HOURS`
         * would be the same string — unlikely with these particular leads, but the
         * kind of thing that becomes true the day someone adds a lead named for a
         * number.
         */
        fun uniqueWorkName(eventId: Long, lead: ReminderLead) =
            "$TAG_PREFIX${eventId}_${lead.name}"
    }
}
