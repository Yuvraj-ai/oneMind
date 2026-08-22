package com.onemind.app.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Asks for screen-capture consent, then hands the result to
 * [ScreenCaptureService].
 *
 * This exists because a [android.service.quicksettings.TileService] cannot call
 * `startActivityForResult`, and MediaProjection consent is only obtainable from an
 * Activity. It is invisible: no layout, translucent theme, finishes the moment the
 * system dialog is answered.
 *
 * Consent is requested **every time**, which is Android's design rather than an
 * oversight on our part — a granted projection is single-use. It also happens to
 * match what oneMind promises: the app can only see the screen at a moment the
 * user explicitly asked it to.
 */
@AndroidEntryPoint
class ScreenCapturePermissionActivity : ComponentActivity() {

    @Inject lateinit var notifier: CaptureNotifier

    private val requestCapture = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ContextCompat.startForegroundService(
                this,
                ScreenCaptureService.intent(this, result.resultCode, result.data!!)
            )
        } else {
            // Declining is a legitimate choice, not an error. Say so quietly.
            notifier.notifyMessage("Capture cancelled")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as? MediaProjectionManager

        if (manager == null) {
            notifier.notifyMessage("Screen capture unavailable")
            finish()
            return
        }

        requestCapture.launch(manager.createScreenCaptureIntent())
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, ScreenCapturePermissionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}
