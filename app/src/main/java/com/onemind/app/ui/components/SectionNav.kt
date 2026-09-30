package com.onemind.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.spatialSpring

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
 * The expressive connected navigation bar across the top of Feed, Timeline and Events.
 *
 * M3 Expressive segmented navigation:
 * - Contained inside a unified capsule track (`surfaceContainerLow`) with subtle border.
 * - Generous breathing room: segments are spaced by 6dp inside a 4dp padded container.
 * - Active segment is highlighted with an expressive pill in `primary` container and `onPrimary` text,
 *   animated via [spatialSpring].
 * - Inactive segments sit comfortably within the track with `onSurfaceVariant` text.
 */
@Composable
fun SectionNav(
    selected: SectionDestination,
    onSelect: (SectionDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = SectionDestination.entries

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            entries.forEach { destination ->
                val isSelected = destination == selected

                val containerColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    animationSpec = spatialSpring(),
                    label = "sectionNavContainer"
                )

                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spatialSpring(),
                    label = "sectionNavContent"
                )

                Surface(
                    onClick = { onSelect(destination) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = containerColor,
                    contentColor = contentColor
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}

