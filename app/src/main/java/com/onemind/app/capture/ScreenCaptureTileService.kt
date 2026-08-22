package com.onemind.app.capture

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.android.AndroidEntryPoint

/**
 * Quick Settings tile that captures the screen.
 *
 * Separate tile from the clipboard one, so the user adds whichever they actually
 * want rather than being given both.
 *
 * The tile itself does almost nothing: consent needs an Activity, so it launches
 * [ScreenCapturePermissionActivity] and collapses the shade. Collapsing matters
 * for more than tidiness — an open notification shade would otherwise be the thing
 * captured.
 */
@AndroidEntryPoint
class ScreenCaptureTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Capture Screen"
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val intent = ScreenCapturePermissionActivity.intent(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // API 34 deprecated the Intent overload in favour of a PendingIntent.
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pending)
        } else {
            // The PendingIntent overload does not exist before API 34, and minSdk
            // is 30, so this branch is required rather than merely tolerated. Lint's
            // deprecation check does not account for the version gate above.
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}
