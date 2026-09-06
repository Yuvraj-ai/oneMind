package com.onemind.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.OneMindMotion
import kotlinx.coroutines.delay

private const val STAGGER_STEP_MS = 40L

/**
 * How far down a list the stagger keeps counting.
 *
 * At 40 ms a step an uncapped index would make the fiftieth card wait two seconds to appear
 * after the user scrolled to it. The stagger exists to give the first screenful a rhythm;
 * past that, arriving immediately is the correct behaviour.
 */
private const val STAGGER_CAP = 7

/**
 * The `rise` entrance: fade in from 14 dp below at 97% scale, staggered by position.
 *
 * `styles.css` gives `.stagger > *` one animation and lets CSS delay each child; Compose
 * has no equivalent, so the index comes in as a parameter and becomes a delay.
 *
 * Keyed on [index] rather than run once per composition: a list item that scrolls out and
 * back is a new composition of the same index, and re-running the entrance every time
 * would make a scrolled list flicker. Keying on the index means the animation replays only
 * when an item's position actually changes.
 *
 * The delay is capped but the key is not: two items at positions 8 and 20 both start
 * immediately, and both still hold their own animation state.
 *
 * `graphicsLayer` keeps the offset and scale off the layout pass, so an entering card
 * cannot remeasure the grid around it.
 *
 * The spring comes from [OneMindMotion], not `MaterialTheme.motionScheme` as the plan
 * specified — that is `internal` in material3 1.4.0. Same numbers, public route.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember(index) { Animatable(0f) }
    val spec = OneMindMotion.slowSpatialSpec<Float>()
    val riseFrom = with(LocalDensity.current) { 14.dp.toPx() }

    LaunchedEffect(index) {
        delay(minOf(index, STAGGER_CAP) * STAGGER_STEP_MS)
        progress.animateTo(1f, animationSpec = spec)
    }

    Box(
        modifier = modifier.graphicsLayer {
            val t = progress.value
            alpha = t
            translationY = riseFrom * (1f - t)
            val s = 0.97f + 0.03f * t
            scaleX = s
            scaleY = s
        }
    ) {
        content()
    }
}
