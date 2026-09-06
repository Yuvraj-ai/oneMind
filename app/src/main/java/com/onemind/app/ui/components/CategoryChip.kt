package com.onemind.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemind.app.ui.theme.Tracking

/**
 * One of a Memory's categories.
 *
 * No click semantics. The card a chip sits on is already clickable and goes to the same
 * place, so a nested clickable would give a screen reader a second target that does the
 * same thing — and filtering by tapping a chip is not a behaviour this app has.
 *
 * 12 sp and a 12 dp corner, both from DESIGN-GUIDE §5.4, and both smaller than any
 * `Typography` slot offers — hence the explicit `fontSize` rather than a slot.
 *
 * Uses `primaryContainer`. This supersedes the private `CategoryChip` the events work added
 * to `EventsScreen.kt`, which used `secondaryContainer` as interim phase-2 styling; the
 * Events restyle deletes that one.
 */
@Composable
fun CategoryChip(name: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            letterSpacing = Tracking.Chip,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
