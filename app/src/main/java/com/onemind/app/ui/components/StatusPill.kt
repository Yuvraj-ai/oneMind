package com.onemind.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * A labelled pill with no behaviour: "In calendar", "Rejected", a source name.
 *
 * Colours are parameters rather than derived from a status enum, because the same pill
 * serves several unrelated vocabularies. [StateChip] is the one that owns a mapping,
 * because `ProcessingState` has exactly one right set of colours.
 */
@Composable
fun StatusPill(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = PillShape, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = Tracking.Chip,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
