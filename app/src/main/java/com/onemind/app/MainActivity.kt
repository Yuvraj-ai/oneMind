package com.onemind.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
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
        enableEdgeToEdge()

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
