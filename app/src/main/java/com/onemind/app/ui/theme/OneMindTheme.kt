package com.onemind.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme.
 *
 * Two defaults here are deliberate reversals of the Compose template's, and both matter
 * more than they look:
 *
 * - **`dynamicColor` is off.** With it on, Material You substitutes the system palette on
 *   every Android 12+ device — which, at minSdk 30, is effectively every device — and the
 *   ember scheme never reaches a screen. The app still looks intentional, just not like
 *   itself, which is why nothing catches it. DESIGN-GUIDE §5.1 is explicit that the brand
 *   palette wins. The parameter stays so a future user-facing toggle has somewhere to
 *   land, and so the code says the choice was made rather than assumed.
 * - **`darkTheme` is on, not `isSystemInDarkTheme()`.** The reference is dark-first.
 *   [EmberLightColorScheme] exists so the parameter is honest, but it is derived from the
 *   dark palette rather than designed, and it is the option rather than the default.
 *
 * **On `MaterialExpressiveTheme`, which this deliberately does not use.** The design spec
 * and the plan both call for it, on the understanding that it is the public entry point
 * that installs the expressive `MotionScheme`. In material3 1.4.0 — the newest stable
 * release; everything past it is `1.5.0-alpha*` — the entire expressive surface is Kotlin
 * `internal`: `MaterialExpressiveTheme`, the five-argument `MaterialTheme` overload that
 * takes a `MotionScheme`, the `MotionScheme` interface itself, `MotionScheme.standard()`
 * and `.expressive()`, `MaterialTheme.motionScheme`, `LocalUsingExpressiveTheme`, and
 * even the `ExperimentalMaterial3ExpressiveApi` marker that would opt in to them. None of
 * it is reachable from outside the library, with or without an opt-in.
 *
 * The earlier check that called these public used `javap`, which reads JVM bytecode —
 * where Kotlin `internal` appears as `public`. Visibility lives in Kotlin metadata that
 * `javap` cannot see, so the only reliable test is whether a call compiles. It does not.
 *
 * What that costs is narrow: Material's own components animate on the standard motion
 * scheme rather than the expressive one. Every other expressive trait the design asks for
 * — the ember palette, the asymmetric card silhouettes, the 42 sp hero, the large FAB, the
 * connected segmented group, press-morph, staggered entrance, wavy progress — is public
 * API and lands unaffected. Custom animations define their own springs rather than reading
 * them from `MaterialTheme.motionScheme`; consistency there is a convention to hold, not
 * something the framework can enforce for us.
 */
@Composable
fun OneMindTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> EmberDarkColorScheme
        else -> EmberLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = OneMindShapes,
        typography = OneMindTypography,
        content = content
    )
}
