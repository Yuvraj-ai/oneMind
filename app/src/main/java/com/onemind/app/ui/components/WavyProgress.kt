package com.onemind.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object WavyProgressDefaults {
    /** `.wavy-track.w-8` — the width the state chip uses. */
    val Width: Dp = 32.dp

    /** `height: 4px`. */
    val Height: Dp = 4.dp

    /** `primary 0 6px, transparent 6px 12px` — one dash plus one gap. */
    val Period: Dp = 12.dp
    val Dash: Dp = 6.dp
}

/**
 * The scrolling dash strip that marks work in progress.
 *
 * A `Canvas` because `LinearWavyProgressIndicator` does not exist in material3 1.4.0 —
 * the artifact ships its token class and no composable. DESIGN-GUIDE §3.3 sanctions this
 * fallback.
 *
 * `styles.css` animates `background-position-x` by 24 px over 1.6 s across a repeating
 * gradient whose own period is 12 px. Two periods per cycle is indistinguishable from
 * one period at half the duration, so this shifts by one 12 dp period over 800 ms —
 * same 15 dp/s, one fewer number to keep in step.
 *
 * Indeterminate on purpose: enrichment has no measurable fraction complete, and a bar
 * that crept to 90% and waited would be a claim the pipeline cannot make.
 */
@Composable
fun WavyProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "wavy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "wavyPhase"
    )

    Canvas(
        modifier = modifier
            .width(WavyProgressDefaults.Width)
            .height(WavyProgressDefaults.Height)
    ) {
        val period = WavyProgressDefaults.Period.toPx()
        val dash = WavyProgressDefaults.Dash.toPx()
        val centreY = size.height / 2f

        // Start one period off-screen so a dash scrolls in rather than appearing.
        var x = -period + phase * period
        while (x < size.width) {
            val start = x.coerceAtLeast(0f)
            val end = (x + dash).coerceAtMost(size.width)
            if (end > start) {
                drawLine(
                    color = color,
                    start = Offset(start, centreY),
                    end = Offset(end, centreY),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
            x += period
        }
    }
}
