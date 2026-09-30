package com.onemind.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * The large header the feed, timeline, events, settings and onboarding screens share.
 *
 * M3 Expressive header:
 * - Eyebrow: `labelMedium` enclosed in an M3 tonal container pill badge (`surfaceContainerHighest`).
 * - Title: `headlineMedium` (28sp semi-bold) for balanced hierarchy.
 *
 * This header consumes the status-bar inset so content begins cleanly below the status bar.
 */
@Composable
fun HeroHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
    ) {
        if (leading != null) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) { leading() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                if (eyebrow.isNotBlank()) {
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = eyebrow.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = Tracking.Eyebrow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (trailing != null) trailing()
        }
    }
}
