package com.onemind.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * M3 Expressive Spatial Spring for layout changes, card expansions, and dialog entrances.
 * dampingRatio = 0.75f, stiffness = 380f.
 */
val SpatialSpring: SpringSpec<Float> = spring(
    dampingRatio = 0.75f,
    stiffness = 380f
)

/**
 * M3 Expressive Effects Spring for press scale feedback and icon morphs.
 * dampingRatio = 0.85f, stiffness = 1500f.
 */
val EffectsSpring: SpringSpec<Float> = spring(
    dampingRatio = 0.85f,
    stiffness = 1500f
)

/**
 * Generic Spatial SpringSpec for any animated type (Float, Dp, Offset, etc.).
 */
fun <T> spatialSpring(
    dampingRatio: Float = 0.75f,
    stiffness: Float = 380f
): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

/**
 * Generic Effects SpringSpec for any animated type (Float, Dp, Color, etc.).
 */
fun <T> effectsSpring(
    dampingRatio: Float = 0.85f,
    stiffness: Float = 1500f
): SpringSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)

/**
 * Checks whether reduced motion is enabled at the system level via [Settings.Global.ANIMATOR_DURATION_SCALE].
 * When animator duration scale is 0, animations should transition instantly or use snap().
 */
fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        ) == 0f
    } catch (_: Throwable) {
        false
    }
}

/**
 * Composable check for whether reduced motion is enabled on the current device.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReducedMotionEnabled(context) }
}

/**
 * Composable helper remembering whether reduced motion is enabled on the current device.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReducedMotionEnabled(context) }
}

/*
 * Legacy motion helper object retained for existing call sites.
 */
object OneMindMotion {
    val SpatialSpring: SpringSpec<Float> = com.onemind.app.ui.theme.SpatialSpring
    val EffectsSpring: SpringSpec<Float> = com.onemind.app.ui.theme.EffectsSpring

    // Spatial: anything that moves or changes size. Underdamped on purpose — the
    // slight overshoot is what reads as "expressive" rather than merely animated.
    private const val DefaultSpatialDamping = 0.8f
    private const val DefaultSpatialStiffness = 380f
    private const val FastSpatialDamping = 0.6f
    private const val FastSpatialStiffness = 800f
    private const val SlowSpatialDamping = 0.8f
    private const val SlowSpatialStiffness = 200f

    // Effects: alpha, colour, elevation. Critically damped (ratio 1.0), because an
    // overshoot on a fade would mean overshooting past opaque, which is invisible at
    // best and a flicker at worst.
    private const val DefaultEffectsDamping = 1f
    private const val DefaultEffectsStiffness = 1600f
    private const val FastEffectsDamping = 1f
    private const val FastEffectsStiffness = 3800f
    private const val SlowEffectsDamping = 1f
    private const val SlowEffectsStiffness = 800f

    /*
     * Generic functions rather than vals, mirroring `MotionScheme`'s own
     * `slowSpatialSpec<T>()` shape, because one spec has to serve `Dp`, `Float`,
     * `Offset` and `Color` call sites and a single non-generic val cannot.
     *
     * Each call allocates a `SpringSpec` — two floats and a null threshold. That is
     * per animation setup, not per frame, so it is not worth the unchecked cast
     * Material uses internally to cache one instance and reinterpret it.
     */

    /** Material's expressive `defaultSpatialSpec` — the everyday move. */
    fun <T> defaultSpatialSpec(): SpringSpec<T> =
        spring(dampingRatio = DefaultSpatialDamping, stiffness = DefaultSpatialStiffness)

    /** Material's expressive `fastSpatialSpec` — small, immediate responses. */
    fun <T> fastSpatialSpec(): SpringSpec<T> =
        spring(dampingRatio = FastSpatialDamping, stiffness = FastSpatialStiffness)

    /** Material's expressive `slowSpatialSpec` — press morphs and entrances. */
    fun <T> slowSpatialSpec(): SpringSpec<T> =
        spring(dampingRatio = SlowSpatialDamping, stiffness = SlowSpatialStiffness)

    /** Material's expressive `defaultEffectsSpec` — the everyday fade. */
    fun <T> defaultEffectsSpec(): SpringSpec<T> =
        spring(dampingRatio = DefaultEffectsDamping, stiffness = DefaultEffectsStiffness)

    /** Material's expressive `fastEffectsSpec`. */
    fun <T> fastEffectsSpec(): SpringSpec<T> =
        spring(dampingRatio = FastEffectsDamping, stiffness = FastEffectsStiffness)

    /** Material's expressive `slowEffectsSpec`. */
    fun <T> slowEffectsSpec(): SpringSpec<T> =
        spring(dampingRatio = SlowEffectsDamping, stiffness = SlowEffectsStiffness)
}
