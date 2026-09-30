package com.onemind.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * An M3 Expressive icon button matching `.icon-btn` from `styles.css:208-222` and spec §3.1.
 *
 * Features:
 * - 40dp pill visual container with 20dp rest corner radius.
 * - 48dp minimum touch target for accessibility and touch precision.
 * - Scales and morphs on press via [rememberPressMorph].
 * - Uses `surfaceContainer` (or `primaryContainer` for primary actions).
 */
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    containerColor: Color = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    },
    contentColor: Color = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    },
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(interaction, restCorner = 20.dp, pressedCorner = 14.dp)

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            shape = RoundedCornerShape(morph.corner),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .size(40.dp)
                .pressScale(morph)
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                content()
            }
        }
    }
}

/**
 * Top bar matching `.topbar` from `styles.css:192-207` and spec §3.1.
 *
 * Consumes the status-bar inset, arranges leading, title and trailing components
 * with consistent vertical and horizontal rhythm.
 *
 * Supports both centered and small start-aligned title layouts with 64dp height,
 * `surfaceContainer` background, and optional scroll elevation.
 */
@Composable
fun ExpressiveTopBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    centered: Boolean = true,
    scrollElevated: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val navLeading: (@Composable () -> Unit)? = leading ?: onNavigateBack?.let { backAction ->
        {
            ExpressiveIconButton(onClick = backAction) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = if (scrollElevated) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (centered) {
                // Centered variant
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (navLeading != null) navLeading()
                }

                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (trailing != null) trailing()
                        actions()
                    }
                }
            } else {
                // Small start-aligned variant
                if (navLeading != null) {
                    Box(
                        modifier = Modifier.size(48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        navLeading()
                    }
                }

                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(
                                start = if (navLeading != null) 12.dp else 0.dp,
                                end = 8.dp
                            ),
                        textAlign = TextAlign.Start
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (trailing != null) trailing()
                    actions()
                }
            }
        }
    }
}

