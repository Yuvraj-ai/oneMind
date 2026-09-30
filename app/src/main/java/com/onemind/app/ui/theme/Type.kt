package com.onemind.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.onemind.app.R

/*
 * Two variable faces rather than seven static ones, and not by preference: google/fonts
 * publishes `Outfit[wght].ttf` and `Figtree[wght].ttf` and no static instances at all.
 * The weight axis is driven explicitly per registered weight, which is well-supported
 * from API 26 and so unconditionally safe at this app's minSdk of 30.
 */

/*
 * The `variationSettings` overload of `Font` is public but marked `@ExperimentalTextApi`
 * in ui-text 1.12.0, so the opt-in is required to compile. Scoped to this one function
 * rather than added as a module-wide `-opt-in` compiler flag: the experimental surface is
 * a single factory call, and keeping the annotation here is what tells the next reader
 * which call is provisional. If the annotation is ever dropped upstream, this becomes a
 * warning about a redundant opt-in — a visible prompt to remove it, which a build flag
 * would not give.
 */
@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    // Explicit rather than relying on the factory's default: it makes the axis being
    // driven visible at the call site, which matters because a variable font with no
    // variation settings renders at its default weight and looks like a font that
    // simply failed to load.
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** Display face: headlines, titles, the composer's input. */
val Outfit = FontFamily(
    variable(R.font.outfit_variable, FontWeight.Normal),
    variable(R.font.outfit_variable, FontWeight.Medium),
    variable(R.font.outfit_variable, FontWeight.SemiBold),
    variable(R.font.outfit_variable, FontWeight.Bold)
)

/** Body and UI face. */
val Figtree = FontFamily(
    variable(R.font.figtree_variable, FontWeight.Normal),
    variable(R.font.figtree_variable, FontWeight.Medium),
    variable(R.font.figtree_variable, FontWeight.SemiBold)
)

/**
 * M3 Expressive Typography Scale.
 *
 * Configured with Outfit for Display, Headline, and Title tiers, and Figtree for Body
 * and Label tiers to maintain clear typographic hierarchy and readability.
 */
val OneMindTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Outfit,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Outfit,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = Outfit,
        fontSize = 36.sp,
        lineHeight = 44.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Outfit,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Outfit,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Outfit,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Outfit,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Outfit,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Figtree,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Figtree,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Figtree,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Figtree,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Figtree,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Figtree,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Figtree,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp
    )
)

/**
 * Per-class letter spacing from `styles.css` that no `Typography` slot can carry,
 * because the same slot is used with and without it.
 *
 * Applied explicitly at the call sites that need it — eyebrows are uppercase and widely
 * tracked, chips less so — and kept here so the numbers live in one place.
 */
object Tracking {
    /** `.eyebrow` — uppercase section label above a hero title. */
    val Eyebrow = 0.14.em

    /** Section tags and chip labels. */
    val Chip = 0.1.em
}
