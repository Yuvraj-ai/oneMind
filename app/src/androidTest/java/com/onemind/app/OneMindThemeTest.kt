package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.ui.theme.Figtree
import com.onemind.app.ui.theme.OneMindBackground
import com.onemind.app.ui.theme.OneMindPrimary
import com.onemind.app.ui.theme.OneMindTheme
import com.onemind.app.ui.theme.Outfit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * That the brand theme is what actually reaches a Composable.
 *
 * Every assertion here is a decision that fails silently if reverted. Dynamic colour was
 * on by default, which on any Android 12+ device — so, at minSdk 30, effectively all of
 * them — replaced the ember palette with the user's wallpaper and left the app looking
 * correct-but-wrong with nothing in the code saying so. A typography slot left at its
 * default keeps Roboto in a screen otherwise set in Figtree. Neither shows up in a
 * build.
 */
@RunWith(AndroidJUnit4::class)
class OneMindThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private class Captured {
        var primary: Color = Color.Unspecified
        var surface: Color = Color.Unspecified
        var displayLargeFamily: FontFamily? = null
        var labelSmallFamily: FontFamily? = null
        var displayLargeSize: Float = 0f
        var largeShape: RoundedCornerShape? = null
        var systemInDarkTheme: Boolean = false
    }

    private fun capture(
        darkTheme: Boolean? = null,
        dynamicColor: Boolean? = null
    ): Captured {
        val captured = Captured()
        composeRule.setContent {
            val systemDark = isSystemInDarkTheme()
            val body: @Composable () -> Unit = {
                // Read in composition, record in the effect. `MaterialTheme.colorScheme`
                // and friends are `@Composable` getters, so they cannot be touched from
                // inside `SideEffect`, whose lambda is a plain `() -> Unit`. Writing to
                // non-snapshot state still belongs in the effect rather than the
                // composable body, hence the split.
                val primary = MaterialTheme.colorScheme.primary
                val surface = MaterialTheme.colorScheme.surface
                val displayLarge = MaterialTheme.typography.displayLarge
                val labelSmall = MaterialTheme.typography.labelSmall
                val largeShape = MaterialTheme.shapes.large as? RoundedCornerShape
                SideEffect {
                    captured.systemInDarkTheme = systemDark
                    captured.primary = primary
                    captured.surface = surface
                    captured.displayLargeFamily = displayLarge.fontFamily
                    captured.labelSmallFamily = labelSmall.fontFamily
                    captured.displayLargeSize = displayLarge.fontSize.value
                    captured.largeShape = largeShape
                }
            }
            when {
                darkTheme == null && dynamicColor == null -> OneMindTheme(content = body)
                darkTheme == null -> OneMindTheme(dynamicColor = dynamicColor!!, content = body)
                dynamicColor == null -> OneMindTheme(darkTheme = darkTheme, content = body)
                else -> OneMindTheme(darkTheme, dynamicColor, content = body)
            }
        }
        composeRule.waitForIdle()
        return captured
    }

    @Test
    fun theBrandPaletteSurvivesByDefault() {
        val captured = capture()

        // Not "a warm colour" — this exact ember. Material You would substitute the
        // wallpaper palette here, and the app would still look deliberate.
        assertEquals(OneMindPrimary, captured.primary)
        assertEquals(OneMindBackground, captured.surface)
    }

    @Test
    fun darkIsTheDefaultRatherThanTheSystemPreference() {
        val captured = capture()

        assertFalse(
            "This device is in dark mode, so a dark default and a system-following " +
                "default are indistinguishable here and this test would pass for the " +
                "wrong reason. Put the emulator in light mode and re-run.",
            captured.systemInDarkTheme
        )
        // The system says light; the theme says ember dark anyway.
        assertEquals(OneMindBackground, captured.surface)
    }

    @Test
    fun theLightSchemeIsStillReachable() {
        val captured = capture(darkTheme = false)

        // The parameter has to mean something, or it is a lie in the signature.
        assertNotEquals(OneMindBackground, captured.surface)
    }

    @Test
    fun typographyUsesTheBundledFacesAndTheReferenceSizes() {
        val captured = capture()

        assertEquals(Outfit, captured.displayLargeFamily)
        assertEquals(Figtree, captured.labelSmallFamily)
        // DESIGN-GUIDE §5.5: hero title 42 sp, a "keep verbatim" number.
        assertEquals(42f, captured.displayLargeSize, 0.01f)
    }

    @Test
    fun shapesCarryTheBrandRadius() {
        val captured = capture()

        assertEquals(RoundedCornerShape(24.dp), captured.largeShape)
    }
}
