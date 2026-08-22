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

        manager.createNotificationChannel(captures)
    }
}
