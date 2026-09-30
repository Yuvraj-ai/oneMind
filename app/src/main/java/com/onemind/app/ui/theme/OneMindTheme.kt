package com.onemind.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive Theme for oneMind.
 *
 * - **`dynamicColor` is enabled by default (API 31+):** Extracts harmonious tonal palettes
 *   from the user's wallpaper.
 * - **Expressive Ember fallback:** When `dynamicColor` is disabled or on API < 31,
 *   [EmberDarkColorScheme] and [EmberLightColorScheme] provide the high-contrast expressive
 *   brand palette.
 * - **`darkTheme` is on by default:** The reference experience is dark-first.
 * - **`MaterialExpressiveTheme` note:** In Compose Material 3 1.4.0, MaterialExpressiveTheme
 *   is Kotlin internal. Standard [MaterialTheme] is used with [OneMindShapes] and
 *   [OneMindTypography], while expressive physics springs are provided via [SpatialSpring]
 *   and [EffectsSpring].
 */
@Composable
fun OneMindTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
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
