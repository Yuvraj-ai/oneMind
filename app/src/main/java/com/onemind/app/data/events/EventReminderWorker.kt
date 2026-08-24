package com.onemind.app.data.events

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onemind.app.capture.CaptureNotifier
import com.onemind.app.domain.events.ReminderLead
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Posts a reminder notification for an upcoming event.
 *
 * Fired by WorkManager at the time [ReminderLead] named: two days out, two hours
 * out, or immediately for an event too close for either lead to still be ahead of
 * it. The notification opens the Memory the event was detected from.
 */
@HiltWorker
class EventReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: CaptureNotifier
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_EVENT_TITLE) ?: return Result.failure()
        val eventTime = inputData.getLong(KEY_EVENT_TIME, 0L)
        val lead = readLead()

        val timeStr = formatTime(eventTime)
        val message = when (lead) {
            ReminderLead.TWO_DAYS -> "In 2 days: $title ($timeStr)"
            ReminderLead.TWO_HOURS -> "In 2 hours: $title ($timeStr)"
            ReminderLead.IMMEDIATE -> "Happening soon: $title ($timeStr)"
            null -> "Upcoming: $title ($timeStr)"
        }

        // Use notifyMessage for event reminders (no specific memoryId action needed
        // since the user may just want the heads-up without opening the app).
        notifier.notifyMessage(message)

        return Result.success()
    }

    /**
     * Which lead this job was enqueued for, or null if it cannot be established.
     *
     * Null rather than a default, because a wrong lead is a lie about *when* the
     * event is. It also covers reminders enqueued by v0.1.3, which wrote `"2_DAYS"`
     * and `"2_HOURS"` before the leads became an enum: those still fire, with the
     * generic wording, rather than claiming a timing nobody checked.
     */
    private fun readLead(): ReminderLead? {
        val name = inputData.getString(KEY_REMINDER_TYPE) ?: return null
        return ReminderLead.entries.firstOrNull { it.name == name }
    }

    private fun formatTime(epochMillis: Long): String {
        if (epochMillis == 0L) return ""
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
        return formatter.format(Instant.ofEpochMilli(epochMillis))
    }

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_EVENT_TITLE = "event_title"
        const val KEY_EVENT_TIME = "event_time"
        const val KEY_REMINDER_TYPE = "reminder_type"
    }
}
