package com.onemind.app.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the notification channels once, on app start.
 *
 * Calling [create] when they already exist is a no-op by design — Android
 * silently ignores a channel whose id already exists and keeps the user's
 * customisations (mute, importance). That means it is safe to call this on
 * every launch rather than gating it behind a "first run" flag.
 */
@Singleton
class NotificationChannels @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        /** Confirmations after Screen Capture, Share, and Clipboard saves. */
        const val CHANNEL_CAPTURES = "memory_captures"

        /**
         * The mandatory foreground-service notification shown while a screen
         * capture is in flight.
         *
         * Separate from [CHANNEL_CAPTURES] and deliberately IMPORTANCE_LOW: it is
         * a transient technical requirement that lives for about a second, not
         * something the user wants announced. Keeping it on its own channel also
         * means muting it does not mute the save confirmations they do want.
         */
        const val CHANNEL_CAPTURE_SERVICE = "capture_service"
    }

    fun create() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val captures = NotificationChannel(
            CHANNEL_CAPTURES,
            "Memory Captures",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Confirmations when a memory is captured via screenshot, share, or clipboard."
        }

        val captureService = NotificationChannel(
            CHANNEL_CAPTURE_SERVICE,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown briefly while a screenshot is being taken."
            setShowBadge(false)
        }

        manager.createNotificationChannel(captures)
        manager.createNotificationChannel(captureService)
    }
}
