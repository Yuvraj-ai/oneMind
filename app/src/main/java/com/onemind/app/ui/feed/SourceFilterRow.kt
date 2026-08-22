package com.onemind.app.ui.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrollable row of filter chips, one per source.
 *
 * "All" is always first; selecting it clears the filter.
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("All") }
        )

        options.forEach { option ->
            val thisFilter = SourceFilter(option.sourceType, option.sourcePackage)
            FilterChip(
                selected = selectedFilter == thisFilter,
                onClick = { onFilterSelected(thisFilter) },
                label = { Text("${option.label} (${option.count})") }
            )
        }
    }
}
