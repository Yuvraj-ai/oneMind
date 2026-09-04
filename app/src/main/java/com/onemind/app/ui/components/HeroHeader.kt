package com.onemind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.HaloGradient
import com.onemind.app.ui.theme.Tracking

/**
 * The large header the feed, timeline, events and onboarding screens share.
 *
 * `displayLarge` at 42 sp, per DESIGN-GUIDE §5.5's "numbers to keep verbatim". §5.4
 * describes the same header as `displayMedium` 40 sp; §5.5 wins, being the section that
 * says it must not drift.
 *
 * The halo is drawn as this composable's background rather than the screen's, so it is
 * sized to the header and fades out within it. Painted behind the text and behind nothing
 * else, which is what keeps a scrolling list from carrying a warm cast down the page.
 *
 * **This header consumes the status-bar inset, and that is not cosmetic.** `MainActivity`
 * calls `enableEdgeToEdge()`, so every destination owns its own insets. Screens used to get
 * that from a `Scaffold` with a `TopAppBar`; the ones this header replaces no longer have
 * either. `EventsScreen` shipped without it once already and drew its first row on top of
 * the system clock — issue #37, and there is an instrumented test pinning it. The inset
 * padding is applied *after* the background on purpose: the halo fills the full area,
 * including behind the status bar, while the text starts below it.
 *
 * Two slots, and they are not interchangeable. [leading] sits on its own row *above* the
 * eyebrow, which is where the reference puts a back button on every screen that has one
 * (`search.html`, `events.html`, `onboarding.html`, `settings.html`). [trailing] sits to
 * the right of the title, which is where the feed and timeline put settings. Putting a back
 * arrow in [trailing] would place it under the user's thumb on the wrong side and read as
 * an action rather than a way out.
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
            .background(HaloGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
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
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = Tracking.Eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (trailing != null) trailing()
        }
    }
}
