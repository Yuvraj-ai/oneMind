package com.onemind.app.ui.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.PillShape

/**
 * Horizontally scrollable row of filter chips, one per source.
 *
 * "All" is always first; selecting it clears the filter.
 *
 * The plan called for converting this `Row` to a `LazyRow` "because the reference scrolls
 * this horizontally". It already did, via `horizontalScroll` — and for the handful of chips
 * a source list produces, laziness buys nothing a `Row` does not already give. Left alone.
 *
 * What did change is the shape and the colour. `.filter-chip` in the reference is
 * `surface-2` at a 12 px radius when inactive and `primary` at 2 rem when active, which is
 * a shape morph on selection rather than a fill change alone. `FilterChip`'s defaults give
 * one shape for both states and `secondaryContainer` when selected — the same mismatch the
 * section nav had, where Material's idea of "selected" is a tone quieter than the brand's.
 */
@Composable
fun SourceFilterRow(
    options: List<SourceFilterOption>,
    selectedFilter: SourceFilter?,
    onFilterSelected: (SourceFilter?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SourceChip(
            selected = selectedFilter == null,
            label = "All",
            onClick = { onFilterSelected(null) }
        )

        options.forEach { option ->
            val thisFilter = SourceFilter(option.sourceType, option.sourcePackage)
            SourceChip(
                selected = selectedFilter == thisFilter,
                label = "${option.label} (${option.count})",
                onClick = { onFilterSelected(thisFilter) }
            )
        }
    }
}

/** Expressive M3 filter pill: 100dp pill shape with secondaryContainer active state. */
@Composable
private fun SourceChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
        },
        shape = PillShape,
        // Null rather than a transparent border: clean pill fill with no outline.
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    )
}
