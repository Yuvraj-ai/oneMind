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

private val Default = Typography()

/**
 * The eight slots DESIGN-GUIDE §5.2 specifies, plus every remaining slot re-pointed at
 * one of the two families.
 *
 * The re-pointing is not busywork: a slot left at its default keeps Roboto, and a single
 * stray Roboto label in a screen otherwise set in Figtree is exactly the kind of thing
 * that reads as "unfinished" without anyone being able to say why.
 */
val OneMindTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Outfit,
        fontSize = 42.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.035).em
    ),
    displayMedium = TextStyle(
        fontFamily = Outfit,
        fontSize = 40.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 42.sp
    ),
    displaySmall = Default.displaySmall.copy(fontFamily = Outfit),
    headlineLarge = Default.headlineLarge.copy(fontFamily = Outfit),
    headlineMedium = Default.headlineMedium.copy(fontFamily = Outfit),
    headlineSmall = Default.headlineSmall.copy(fontFamily = Outfit),
    titleLarge = TextStyle(
        fontFamily = Outfit,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 31.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Outfit,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).em
    ),
    titleSmall = TextStyle(
        fontFamily = Figtree,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(fontFamily = Figtree, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontSize = 15.sp, lineHeight = 24.sp),
    bodySmall = Default.bodySmall.copy(fontFamily = Figtree),
    labelLarge = Default.labelLarge.copy(fontFamily = Figtree),
    labelMedium = Default.labelMedium.copy(fontFamily = Figtree),
    labelSmall = TextStyle(
        fontFamily = Figtree,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
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
