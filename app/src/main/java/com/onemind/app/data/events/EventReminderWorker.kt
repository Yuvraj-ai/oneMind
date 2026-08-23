package com.onemind.app.data.events

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onemind.app.capture.CaptureNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Posts a reminder notification for an upcoming event.
 *
 * Fired by WorkManager at the scheduled time (2 days before, then 2 hours before).
 * The notification opens the Memory the event was detected from.
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
        val reminderType = inputData.getString(KEY_REMINDER_TYPE) ?: "2_HOURS"
        val eventId = inputData.getLong(KEY_EVENT_ID, 0L)

        val timeStr = formatTime(eventTime)
        val message = when (reminderType) {
            "2_DAYS" -> "In 2 days: $title ($timeStr)"
            "2_HOURS" -> "In 2 hours: $title ($timeStr)"
            else -> "Upcoming: $title ($timeStr)"
        }

        // Use notifyMessage for event reminders (no specific memoryId action needed
        // since the user may just want the heads-up without opening the app).
        notifier.notifyMessage(message)

        return Result.success()
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
