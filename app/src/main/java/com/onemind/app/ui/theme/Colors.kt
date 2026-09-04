package com.onemind.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The reference token sheet, baked.
 *
 * `styles.css` states every colour in oklch. Compose has no oklch parser, so each value
 * was converted once — oklch → oklab → linear sRGB → sRGB — and committed with its
 * source in the trailing comment. Baking rather than converting at runtime keeps these
 * as compile-time constants and keeps the conversion out of a hot path; keeping the
 * oklch is what makes a value checkable later without re-deriving the whole sheet.
 *
 * Never hardcode a colour in a Composable. Alias it here.
 */

val OneMindBackground = Color(0xFF140A08) // oklch(0.16 0.018 35)
val OneMindSurface1 = Color(0xFF1F1310)   // oklch(0.2 0.021 33)
val OneMindSurface2 = Color(0xFF2A1B18)   // oklch(0.24 0.024 33)
val OneMindSurface3 = Color(0xFF352421)   // oklch(0.28 0.027 32)
val OneMindSurface4 = Color(0xFF44302B)   // oklch(0.33 0.031 32)
val OneMindCard = Color(0xFF271916)       // oklch(0.23 0.024 33)
val OneMindForeground = Color(0xFFF8EBE7) // oklch(0.95 0.015 40)

val OneMindPrimary = Color(0xFFEF8D67)           // oklch(0.74 0.13 42)
val OneMindOnPrimary = Color(0xFF290C06)         // oklch(0.2 0.05 35)
val OneMindPrimaryContainer = Color(0xFF593124)  // oklch(0.36 0.062 38)
val OneMindOnPrimaryContainer = Color(0xFFFFE1D1) // oklch(0.93 0.04 50)

val OneMindAccent = Color(0xFFF7CBC7)   // oklch(0.88 0.05 25)
val OneMindOnAccent = Color(0xFF2A130F) // oklch(0.22 0.04 32)

val OneMindMutedForeground = Color(0xFFBCA9A3) // oklch(0.75 0.024 40)
val OneMindBorder = Color(0xFF433431)          // oklch(0.34 0.022 33)
val OneMindOutline = Color(0xFF62514C)         // oklch(0.45 0.025 34)
val OneMindInput = Color(0xFF392A26)           // oklch(0.3 0.024 33)

val OneMindDestructive = Color(0xFFED5350)   // oklch(0.65 0.19 25)
val OneMindOnDestructive = Color(0xFFFFF6F3) // oklch(0.98 0.01 40)

/**
 * Semantic colours with no `ColorScheme` slot.
 *
 * M3 has `error` and nothing for "went well" or "be careful", so these are exposed as
 * plain tokens and used directly. Kept here rather than invented at a call site.
 */
val OneMindSuccess = Color(0xFF57BC80) // oklch(0.72 0.13 155)
val OneMindWarning = Color(0xFFE9B452) // oklch(0.8 0.13 80)

/** `--gradient-ember` stops, at 0% / 55% / 100%. */
val OneMindEmber0 = Color(0xFF833F29)   // oklch(0.45 0.1 38)
val OneMindEmber55 = Color(0xFF442321)  // oklch(0.3 0.05 25)
val OneMindEmber100 = Color(0xFF2C1A16) // oklch(0.24 0.03 33)

/** `--gradient-halo` inner stop, before its 0.85 alpha is applied. */
val OneMindHalo = Color(0xFF5C2F1F) // oklch(0.36 0.07 40)

/**
 * The canonical expressive scheme, mapped per DESIGN-GUIDE §5.1.
 *
 * The tonal stepping is the point: cards and chips are distinguished by surface level,
 * not by shadow, so `surfaceContainer*` carries real design weight here rather than
 * being a set of near-identical greys.
 */
val EmberDarkColorScheme = darkColorScheme(
    primary = OneMindPrimary,
    onPrimary = OneMindOnPrimary,
    primaryContainer = OneMindPrimaryContainer,
    onPrimaryContainer = OneMindOnPrimaryContainer,

    // `--card`. Cards and the chips that sit on them read as one tonal family.
    secondaryContainer = OneMindCard,
    onSecondaryContainer = OneMindForeground,

    // The expressive pop: FAB, attach button, "Add to calendar".
    tertiary = OneMindAccent,
    onTertiary = OneMindOnAccent,
    tertiaryContainer = OneMindAccent,
    onTertiaryContainer = OneMindOnAccent,

    background = OneMindBackground,
    onBackground = OneMindForeground,
    surface = OneMindBackground,
    onSurface = OneMindForeground,
    surfaceVariant = OneMindSurface2,
    onSurfaceVariant = OneMindMutedForeground,
    surfaceContainerLowest = OneMindBackground,
    surfaceContainerLow = OneMindSurface1,
    surfaceContainer = OneMindSurface2,
    surfaceContainerHigh = OneMindSurface3,
    surfaceContainerHighest = OneMindSurface4,

    outline = OneMindOutline,
    outlineVariant = OneMindBorder,

    error = OneMindDestructive,
    onError = OneMindOnDestructive,
    errorContainer = OneMindDestructive,
    onErrorContainer = OneMindOnDestructive
)

/**
 * Light, provided so the `darkTheme` parameter is honest rather than decorative.
 *
 * Derived, not designed. The reference is dark-only, so these values are the ember hues
 * re-anchored to a light background; they are not a second designed palette and should
 * not be treated as one.
 */
val EmberLightColorScheme = lightColorScheme(
    primary = Color(0xFF8F4021),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF351000),
    secondaryContainer = Color(0xFFFFEDE5),
    onSecondaryContainer = Color(0xFF2B1710),
    tertiary = Color(0xFF7D4F49),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF231916),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF231916),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF53433D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFFEBE2),
    surfaceContainerHigh = Color(0xFFFAE5DC),
    surfaceContainerHighest = Color(0xFFF4DFD6),
    outline = Color(0xFF85736C),
    outlineVariant = Color(0xFFD8C2B9),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)
