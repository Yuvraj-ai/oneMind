package com.onemind.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.onemind.app.capture.ACTION_OPEN_MEMORY
import com.onemind.app.capture.EXTRA_MEMORY_ID
import com.onemind.app.ui.OneMindApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialMemoryId = extractMemoryId(intent)

        setContent {
            OneMindApp(openMemoryId = initialMemoryId)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // When the Activity already exists (single-top), a notification tap
        // delivers here rather than recreating. Navigation handles it via the
        // recomposition triggered by the new intent — but since Compose navigation
        // is declarative, a future improvement can observe intents as state.
        // For now the tap at least brings the app to the foreground.
        setIntent(intent)
    }

    private fun extractMemoryId(intent: Intent?): Long? {
        if (intent?.action != ACTION_OPEN_MEMORY) return null
        val id = intent.getLongExtra(EXTRA_MEMORY_ID, -1L)
        return if (id > 0L) id else null
    }
}
