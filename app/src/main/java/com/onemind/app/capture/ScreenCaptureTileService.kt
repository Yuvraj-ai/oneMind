package com.onemind.app.capture

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.TextUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Quick Settings tile that captures the screen.
 *
 * Does not do the capturing itself — that requires an Accessibility Service, which
 * is a background service rather than something a tile can invoke directly. Instead
 * it sends a command to [ScreenCaptureAccessibilityService] via `startService`.
 *
 * If the service is not enabled yet, the tile opens Android's Accessibility Settings
 * so the user can toggle it on. This is a one-time setup; once enabled, every
 * subsequent tap is instant.
 */
@AndroidEntryPoint
class ScreenCaptureTileService : TileService() {

    @Inject lateinit var notifier: CaptureNotifier

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = if (isAccessibilityServiceEnabled()) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            tile.label = "Capture Screen"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        if (!isAccessibilityServiceEnabled()) {
            // Guide the user to enable the service. One-time setup.
            notifier.notifyMessage("Enable oneMind in Accessibility Settings to capture screenshots")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivityAndCollapseCompat(intent)
            return
        }

        // Send command to the running accessibility service.
        val intent = Intent(this, ScreenCaptureAccessibilityService::class.java).apply {
            action = ScreenCaptureAccessibilityService.ACTION_TAKE_SCREENSHOT
        }
        startService(intent)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ScreenCaptureAccessibilityService::class.java)
            .flattenToString()

        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return TextUtils.SimpleStringSplitter(':').let { splitter ->
            splitter.setString(enabledServices)
            splitter.any { it.equals(expected, ignoreCase = true) }
        }
    }

    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            // The PendingIntent overload does not exist before API 34. minSdk is 30,
            // so this branch is required rather than merely tolerated.
            startActivityAndCollapse(intent)
        }
    }
}
