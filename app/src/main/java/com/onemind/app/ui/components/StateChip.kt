package com.onemind.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.theme.OneMindSuccess
import com.onemind.app.ui.theme.OneMindWarning
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * A Memory's processing state, as a pill.
 *
 * The `when` is exhaustive over [ProcessingState] with no `else`, so a seventh state stops
 * the build here instead of silently rendering as one of the six.
 *
 * Only `PROCESSING` carries the wavy strip. `SAVED` and `EDITED` are also "not done yet",
 * but nothing is running for them — showing motion would claim work is happening when the
 * Memory is sitting in a queue.
 */
@Composable
fun StateChip(state: ProcessingState, modifier: Modifier = Modifier) {
    val label: String
    val container: Color
    val content: Color

    when (state) {
        ProcessingState.DRAFT -> {
            label = "Draft"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        ProcessingState.SAVED -> {
            label = "Saved"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        ProcessingState.PROCESSING -> {
            label = "Processing"
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        ProcessingState.READY -> {
            label = "Ready"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = OneMindSuccess
        }
        ProcessingState.EDITED -> {
            label = "Edited"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = OneMindWarning
        }
        ProcessingState.FAILED -> {
            label = "Failed"
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(modifier = modifier, shape = PillShape, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = Tracking.Chip,
                color = content
            )
            if (state == ProcessingState.PROCESSING) {
                WavyProgress(color = content)
            }
        }
    }
}
