package com.onemind.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.OneMindMotion

/** What a pressed surface looks like: a rounder corner and a slight shrink. */
data class PressMorphState(val corner: Dp, val scale: Float)

object PressMorphDefaults {
    /** Every tappable surface morphs toward the same corner, whatever it rests at. */
    val PressedCorner: Dp = 40.dp

    /** `transform: scale(0.96)` in `styles.css`. */
    const val PressedScale: Float = 0.96f
}

/**
 * The signature press response: corner radius grows toward 40 dp, the surface shrinks to
 * 96%, both on the expressive slow spatial spring.
 *
 * Returned as a value rather than applied as a `Modifier` because the corner has to reach
 * a `Card`'s `shape` parameter, and a `Modifier` cannot hand anything back to its caller.
 * Only the cheap half — the scale — is applied as a modifier, via [pressScale].
 *
 * The spring comes from [OneMindMotion] rather than a literal
 * `spring(dampingRatio, stiffness)` written out here, so a custom morph and a Material
 * component pressed beside it move with the same physics. That is the whole point, and it
 * is exactly what drifts apart when a spring is written by hand in one file and left to
 * the framework in the next. The plan asked for `MaterialTheme.motionScheme`; that is
 * `internal` in material3 1.4.0, and [OneMindMotion] carries the same numbers read out of
 * the AAR's own token class. See its file comment.
 *
 * Usage:
 * ```
 * val interaction = remember { MutableInteractionSource() }
 * val morph = rememberPressMorph(interaction, restCorner = 16.dp)
 * Card(
 *     onClick = onClick,
 *     interactionSource = interaction,
 *     shape = RoundedCornerShape(morph.corner),
 *     modifier = Modifier.pressScale(morph)
 * ) { … }
 * ```
 */
@Composable
fun rememberPressMorph(
    interactionSource: InteractionSource,
    restCorner: Dp,
    pressedCorner: Dp = PressMorphDefaults.PressedCorner
): PressMorphState {
    val pressed by interactionSource.collectIsPressedAsState()

    val corner by animateDpAsState(
        targetValue = if (pressed) pressedCorner else restCorner,
        animationSpec = OneMindMotion.slowSpatialSpec(),
        label = "pressMorphCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressMorphDefaults.PressedScale else 1f,
        animationSpec = OneMindMotion.slowSpatialSpec(),
        label = "pressMorphScale"
    )

    return PressMorphState(corner = corner, scale = scale)
}

/**
 * Apply the shrink half of [rememberPressMorph].
 *
 * `graphicsLayer` rather than `scale`: the scale is animated every frame while a finger is
 * down, and `graphicsLayer` keeps that off the layout pass so pressing a card cannot
 * remeasure the list it sits in.
 */
fun Modifier.pressScale(state: PressMorphState): Modifier =
    graphicsLayer(scaleX = state.scale, scaleY = state.scale)
