package com.onemind.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.EffectsSpring

/**
 * M3 Expressive Floating Action Dock matching spec §3.3.
 *
 * Pinned at the bottom center of discovery feeds, this toolbar houses secondary discovery
 * actions (such as quick search and filters) alongside a prominent Hero Large capture FAB.
 *
 * Specifications:
 * - Container: 64dp height, 32dp pill corners ([RoundedCornerShape] or [CircleShape]).
 * - Styling: `surfaceContainerHigh` background, 3dp subtle shadow elevation, hairline
 *   `outlineVariant` border.
 * - Secondary actions: 40dp pill button with 48dp touch target.
 * - Hero Large FAB: 56dp primary button with `+` icon, spring press feedback driven by [EffectsSpring].
 */
@Composable
fun FloatingActionDock(
    onFabClick: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraAction: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Secondary action: Search icon button (40dp pill, 48dp touch target)
            ExpressiveIconButton(
                onClick = onSearchClick,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            }

            // Optional extra secondary action slot (e.g. filter toggle)
            if (extraAction != null) {
                extraAction()
            }

            // Hero Large capture FAB: 56dp primary container with spring press feedback
            val fabInteraction = remember { MutableInteractionSource() }
            val isFabPressed by fabInteraction.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (isFabPressed) 0.92f else 1.0f,
                animationSpec = EffectsSpring,
                label = "fabPressScale"
            )

            Surface(
                onClick = onFabClick,
                interactionSource = fabInteraction,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer(scaleX = fabScale, scaleY = fabScale)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
