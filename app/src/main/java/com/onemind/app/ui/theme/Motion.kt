package com.onemind.app.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/*
 * The expressive motion scheme's springs, as public constants.
 *
 * This file exists because `MaterialTheme.motionScheme` does not — not to us. In
 * material3 1.4.0 the whole expressive motion surface is Kotlin `internal`:
 * `MotionScheme`, `MotionScheme.standard()`, `.expressive()`,
 * `MaterialTheme.motionScheme`, and even the `ExperimentalMaterial3ExpressiveApi`
 * marker that would opt in to them. See `OneMindTheme`'s KDoc for the longer note,
 * including why `javap` said otherwise.
 *
 * The plan's instruction was that custom animations read their springs from
 * `MaterialTheme.motionScheme` so that a hand-written morph and a Material component
 * pressed beside it move with the same physics rather than two hand-tuned
 * approximations of it. That intent survives; only the mechanism changes. The numbers
 * below are not invented and not eyeballed from the spec — they were read out of the
 * resolved AAR's `androidx.compose.material3.tokens.ExpressiveMotionTokens`, which is
 * the same class `MotionScheme.ExpressiveMotionSchemeImpl` builds its own specs from.
 * So these *are* Material's expressive springs, reached the long way round.
 *
 * If material3 ever makes the motion scheme public, delete this file and switch the
 * call sites back. Until then, every custom animation in the app reads from here, and
 * none writes a spring inline — one place is the whole point.
 */
object OneMindMotion {

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
