package com.onemind.app.capture

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.onemind.app.MainActivity
import com.onemind.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a "Saved to Memory" confirmation notification.
 *
 * Every capture path (Share, Clipboard, Screen Capture) calls this after
 * persisting, so the user always gets consistent feedback regardless of how the
 * Memory was created.
 *
 * Does nothing (silently) when notification permission has not been granted,
 * because a capture that worked should never appear to fail because of a missing
 * notification.
 */
@Singleton
class CaptureNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Max chars of preview text shown in the notification body. */
        private const val PREVIEW_MAX_CHARS = 80

        /** Notification id offset — keeps capture notifications from replacing each other. */
        private const val BASE_ID = 3000
    }

    /**
     * Notify the user that a Memory was saved.
     *
     * @param memoryId The persisted Memory's id. Tapping the notification opens it.
     * @param previewText First ~80 chars of the content, or null for image-only.
     * @param thumbnail The first image's thumbnail, or null.
     */
    fun notify(memoryId: Long, previewText: String? = null, thumbnail: Bitmap? = null) {
        if (!hasPermission()) return

        val contentText = previewText
            ?.take(PREVIEW_MAX_CHARS)
            ?.ifBlank { null }
            ?: "Image"

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_CAPTURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Saved to Memory")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setContentIntent(openMemoryIntent(memoryId))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (thumbnail != null) {
            builder.setLargeIcon(thumbnail)
        }

        NotificationManagerCompat.from(context)
            .notify(notificationId(memoryId), builder.build())
    }

    /**
     * Notify with an informational message that is not tied to a Memory.
     *
     * Used for "Nothing to save" (empty clipboard) or "Capture cancelled"
     * (permission denied).
     */
    fun notifyMessage(message: String) {
        if (!hasPermission()) return

        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_CAPTURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("oneMind")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        NotificationManagerCompat.from(context)
            .notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openMemoryIntent(memoryId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_MEMORY
            putExtra(EXTRA_MEMORY_ID, memoryId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            memoryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Stable per Memory, so a reprocessing notification replaces the first
     * rather than stacking a second one. Different Memories never collide.
     */
    private fun notificationId(memoryId: Long): Int = BASE_ID + memoryId.toInt()
}

/** Intent action that asks MainActivity to navigate to a specific Memory. */
const val ACTION_OPEN_MEMORY = "com.onemind.app.ACTION_OPEN_MEMORY"
const val EXTRA_MEMORY_ID = "com.onemind.app.EXTRA_MEMORY_ID"
