package com.onemind.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The three destinations the segmented group switches between.
 *
 * Carries labels and nothing else. Mapping a destination to a route is navigation policy
 * and lives in the navigation layer — which also means this component compiles before
 * `NavRoutes` has learned about Timeline.
 */
enum class SectionDestination(val label: String) {
    FEED("Feed"),
    TIMELINE("Timeline"),
    EVENTS("Events")
}

/**
 * The connected button group across the top of Feed, Timeline and Events.
 *
 * `SingleChoiceSegmentedButtonRow` because the reference's "connected button group" is
 * exactly that, and because material3 1.4.0 ships no `ButtonGroup` — verified against the
 * AAR, which contains no such class. DESIGN-GUIDE §5.4 already maps it here.
 *
 * Two details that came out of comparing a render against `design-reference/`, and that the
 * plan had wrong:
 *
 * - **The selected segment is `primary`, not `primaryContainer`.** The guide says so three
 *   times — §5.4's mapping table (`selected = primary`), its motion section ("selected
 *   segment is `primary`"), and its feed screen breakdown ("Feed (active, primary) /
 *   Timeline / Events (surface-2)") — and `styles.css` agrees:
 *   `.btn-group a.active { background: var(--primary) }`. `primaryContainer` is the muted
 *   `#593124`, so the selected segment read as a dim brown rather than the ember pop the
 *   whole control is built around. Inactive stays `surfaceContainer`, which *is* the
 *   `--surface-2` the CSS asks for.
 * - **No leading icon.** `SegmentedButton` defaults its `icon` slot to a checkmark. The
 *   reference's segments are three bare labels, so the slot is emptied rather than left to
 *   the default. `space = 4.dp` for the same reason: "Gap 4 px, connected joints".
 */
@Composable
fun SectionNav(
    selected: SectionDestination,
    onSelect: (SectionDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = SectionDestination.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth(), space = 4.dp) {
        entries.forEachIndexed { index, destination ->
            SegmentedButton(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = {},
                label = { Text(destination.label, style = MaterialTheme.typography.titleSmall) }
            )
        }
    }
}
