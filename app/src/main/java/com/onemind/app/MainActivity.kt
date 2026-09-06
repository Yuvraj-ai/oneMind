package com.onemind.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onemind.app.capture.ACTION_OPEN_MEMORY
import com.onemind.app.capture.EXTRA_MEMORY_ID
import com.onemind.app.ui.OneMindApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * A pending request to open a specific Memory, from a notification tap.
     *
     * Observable state, and that is the point. The id used to be read once in
     * `onCreate` into a plain local, with `onNewIntent` only calling `setIntent()` —
     * which nothing observes, so a tap that arrived at a live Activity could not
     * navigate at all. Deep links appeared to work only because
     * `FLAG_ACTIVITY_CLEAR_TOP` against a `standard` launch mode forced the Activity
     * to be recreated, which discarded every bit of UI state on the way.
     *
     * Declaring `singleTop` in the manifest plus holding this in state fixes both:
     * the Activity is reused, and the new intent reaches composition.
     */
    private var pendingMemoryId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Both bars are styled for a dark background rather than left to
        // `systemDefault()`, which picks its icon tint from the system's night setting.
        // `OneMindTheme` is dark-first and ignores that setting, so on a device in light
        // mode the default gave dark icons over the ember background — a clock and a
        // battery meter that were very nearly invisible. Transparent scrims because the
        // app draws its own background behind both bars.
        //
        // This is coupled to `OneMindTheme`'s `darkTheme = true` default by hand.
        // `enableEdgeToEdge` runs in `onCreate`, outside composition, so it cannot read
        // the theme; if the theme ever gains a real light mode, the bars have to be
        // driven from composition instead.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        pendingMemoryId = extractMemoryId(intent)

        setContent {
            OneMindApp(
                openMemoryId = pendingMemoryId,
                // Cleared once acted on. Without this the request survived rotation
                // and re-navigated into the Memory the user had just backed out of.
                onMemoryOpened = { pendingMemoryId = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractMemoryId(intent)?.let { pendingMemoryId = it }
    }

    private fun extractMemoryId(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_MEMORY) return null
        val id = intent.getLongExtra(EXTRA_MEMORY_ID, -1L)
        return if (id > 0L) id else null
    }
}
