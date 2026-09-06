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
 * and device restart. Each carries two tags: one for its event, and one for the
 * Memory the event was detected in, which is what makes [cancelForMemory] possible.
 */
@Singleton
class EventReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventDao: EventDao
) {

    /**
     * Schedule reminders for all upcoming events that don't have them yet.
     *
     * Called from application start, after the stale-processing sweep, and from
     * `ProcessingWorker` after any pipeline run that completed. Both callers matter:
     * the worker is what gets a freshly detected event its reminders without waiting
     * for the app to be restarted, and app start is what repairs anything the worker
     * could not finish. From v0.1.2 until this was written only app start existed,
     * while this comment claimed both did, so an event detected in a session that
     * never ended got nothing.
     *
     * Scans every unscheduled event rather than one Memory's, which is what makes it
     * safe to call from anywhere and what makes app start a repair pass.
     */
    suspend fun scheduleAll() {
        val unscheduled = eventDao.getUnscheduledReminders()
        val now = Instant.now()

        unscheduled.forEach { event ->
            scheduleForEvent(event.id, event.memoryId, event.eventTime, event.eventTitle, now)
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
        memoryId: Long,
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
                .addTag(memoryTag(memoryId))
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

    /**
     * Drop every reminder belonging to [memoryId], for when that Memory is deleted.
     *
     * By tag rather than by lookup, and that is the whole point. A Memory's event
     * rows cascade away with it, so by the time anything wants to cancel there is
     * nothing left in the database to ask "which events did this have" — the answer
     * is always none, and a lookup-based cancel would do nothing while appearing to
     * work. The tag was written onto the job at enqueue time and outlives the row.
     *
     * Tag-based *here specifically*, and [cancelForEvent] is not: that one runs while
     * the row is still present, this one runs after it is gone.
     */
    fun cancelForMemory(memoryId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(memoryTag(memoryId))
    }

    /**
     * Drop the reminders belonging to one event, for when the user rejects it.
     *
     * Rejecting sets a status `getUnscheduledReminders` filters out, so the event earns
     * no *new* reminders — but the ones already enqueued would still fire, and the user
     * would be notified days later about something they had just declined.
     *
     * This existed in v0.1.2, was deleted in #33, and comes back because the reason it
     * went away does not apply here. It was called only from the delete path, where the
     * Memory's event rows have already cascaded away, making a lookup-based cancel a
     * no-op that looked like it worked. On reject the row is still there.
     *
     * Enumerating [ReminderLead] rather than cancelling a per-event tag keeps
     * [uniqueWorkName] the single source of naming; a lead that earned no job simply
     * cancels a name nothing was enqueued under, which WorkManager treats as a no-op.
     */
    fun cancelForEvent(eventId: Long) {
        val workManager = WorkManager.getInstance(context)
        ReminderLead.entries.forEach { lead ->
            workManager.cancelUniqueWork(uniqueWorkName(eventId, lead))
        }
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

        /**
         * Per-Memory tag, used to cancel every reminder a Memory owns at once.
         *
         * Mirrors `ProcessingScheduler.memoryTag`, and deliberately spells out
         * `memory` so it cannot collide with the per-event tag or with anything
         * [uniqueWorkName] produces — those interpolate a number where this has a
         * word.
         */
        fun memoryTag(memoryId: Long) = "${TAG_PREFIX}memory_$memoryId"
    }
}
