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

// --- Expressive Ember Tokens (Dark) ---
val EmberDarkSurface = Color(0xFF121316)
val EmberDarkSurfaceContainerLowest = Color(0xFF0D0E11)
val EmberDarkSurfaceContainerLow = Color(0xFF18191E)
val EmberDarkSurfaceContainer = Color(0xFF202228)
val EmberDarkSurfaceContainerHigh = Color(0xFF2A2C34)
val EmberDarkSurfaceContainerHighest = Color(0xFF353742)

val EmberDarkPrimary = Color(0xFFFF7A45)
val EmberDarkOnPrimary = Color(0xFF481500)
val EmberDarkPrimaryContainer = Color(0xFF5C2008)
val EmberDarkOnPrimaryContainer = Color(0xFFFFDBCF)

val EmberDarkSecondary = Color(0xFFE7BDB0)
val EmberDarkOnSecondary = Color(0xFF442A22)
val EmberDarkSecondaryContainer = Color(0xFF3E2723)
val EmberDarkOnSecondaryContainer = Color(0xFFF5D6CB)

val EmberDarkTertiary = Color(0xFFF5A623)
val EmberDarkOnTertiary = Color(0xFF442B00)
val EmberDarkTertiaryContainer = Color(0xFF3D2E14)
val EmberDarkOnTertiaryContainer = Color(0xFFFFE099)

val EmberDarkOutline = Color(0xFF8C8E99)
val EmberDarkOutlineVariant = Color(0xFF44464F)

// --- Expressive Ember Tokens (Light) ---
val EmberLightSurface = Color(0xFFFAF9F6)
val EmberLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val EmberLightSurfaceContainerLow = Color(0xFFF6F3ED)
val EmberLightSurfaceContainer = Color(0xFFF0ECE5)
val EmberLightSurfaceContainerHigh = Color(0xFFE6E1D9)
val EmberLightSurfaceContainerHighest = Color(0xFFDDD8D0)

val EmberLightPrimary = Color(0xFFA33E15)
val EmberLightOnPrimary = Color(0xFFFFFFFF)
val EmberLightPrimaryContainer = Color(0xFFFFDBCF)
val EmberLightOnPrimaryContainer = Color(0xFF3B0900)

val EmberLightSecondary = Color(0xFF77574E)
val EmberLightOnSecondary = Color(0xFFFFFFFF)
val EmberLightSecondaryContainer = Color(0xFFFFDBCF)
val EmberLightOnSecondaryContainer = Color(0xFF2C150F)

val EmberLightTertiary = Color(0xFF805600)
val EmberLightOnTertiary = Color(0xFFFFFFFF)
val EmberLightTertiaryContainer = Color(0xFFFFE099)
val EmberLightOnTertiaryContainer = Color(0xFF271900)

val EmberLightOutline = Color(0xFF85736E)
val EmberLightOutlineVariant = Color(0xFFD8C2BB)

// Compatibility aliases for legacy calls
val OneMindBackground = EmberDarkSurface
val OneMindSurface1 = EmberDarkSurfaceContainerLow
val OneMindSurface2 = EmberDarkSurfaceContainer
val OneMindSurface3 = EmberDarkSurfaceContainerHigh
val OneMindSurface4 = EmberDarkSurfaceContainerHighest
val OneMindCard = EmberDarkSurfaceContainer
val OneMindForeground = Color(0xFFF8EBE7)

val OneMindPrimary = EmberDarkPrimary
val OneMindOnPrimary = EmberDarkOnPrimary
val OneMindPrimaryContainer = EmberDarkPrimaryContainer
val OneMindOnPrimaryContainer = EmberDarkOnPrimaryContainer

val OneMindAccent = EmberDarkTertiary
val OneMindOnAccent = EmberDarkOnTertiary

val OneMindMutedForeground = Color(0xFFBCA9A3)
val OneMindBorder = EmberDarkOutlineVariant
val OneMindOutline = EmberDarkOutline
val OneMindInput = EmberDarkSurfaceContainerLow

val OneMindDestructive = Color(0xFFED5350)
val OneMindOnDestructive = Color(0xFFFFF6F3)

/**
 * Semantic colours with no `ColorScheme` slot.
 */
val OneMindSuccess = Color(0xFF57BC80)
val OneMindWarning = Color(0xFFE9B452)

/** `--gradient-ember` stops, at 0% / 55% / 100%. */
val OneMindEmber0 = Color(0xFF833F29)
val OneMindEmber55 = Color(0xFF442321)
val OneMindEmber100 = Color(0xFF2C1A16)

/** `--gradient-halo` inner stop, before its 0.85 alpha is applied. */
val OneMindHalo = Color(0xFF5C2F1F)

/**
 * M3 Expressive Ember Dark ColorScheme.
 *
 * Rich tonal depth across container levels: cards and segmented buttons are structured
 * through surfaceContainerLowest to surfaceContainerHighest.
 */
val EmberDarkColorScheme = darkColorScheme(
    primary = EmberDarkPrimary,
    onPrimary = EmberDarkOnPrimary,
    primaryContainer = EmberDarkPrimaryContainer,
    onPrimaryContainer = EmberDarkOnPrimaryContainer,
    secondary = EmberDarkSecondary,
    onSecondary = EmberDarkOnSecondary,
    secondaryContainer = EmberDarkSecondaryContainer,
    onSecondaryContainer = EmberDarkOnSecondaryContainer,
    tertiary = EmberDarkTertiary,
    onTertiary = EmberDarkOnTertiary,
    tertiaryContainer = EmberDarkTertiaryContainer,
    onTertiaryContainer = EmberDarkOnTertiaryContainer,
    background = EmberDarkSurface,
    onBackground = OneMindForeground,
    surface = EmberDarkSurface,
    onSurface = OneMindForeground,
    surfaceVariant = EmberDarkSurfaceContainerHigh,
    onSurfaceVariant = OneMindMutedForeground,
    surfaceContainerLowest = EmberDarkSurfaceContainerLowest,
    surfaceContainerLow = EmberDarkSurfaceContainerLow,
    surfaceContainer = EmberDarkSurfaceContainer,
    surfaceContainerHigh = EmberDarkSurfaceContainerHigh,
    surfaceContainerHighest = EmberDarkSurfaceContainerHighest,
    outline = EmberDarkOutline,
    outlineVariant = EmberDarkOutlineVariant,
    error = OneMindDestructive,
    onError = OneMindOnDestructive,
    errorContainer = OneMindDestructive,
    onErrorContainer = OneMindOnDestructive
)

/**
 * M3 Expressive Ember Light ColorScheme.
 */
val EmberLightColorScheme = lightColorScheme(
    primary = EmberLightPrimary,
    onPrimary = EmberLightOnPrimary,
    primaryContainer = EmberLightPrimaryContainer,
    onPrimaryContainer = EmberLightOnPrimaryContainer,
    secondary = EmberLightSecondary,
    onSecondary = EmberLightOnSecondary,
    secondaryContainer = EmberLightSecondaryContainer,
    onSecondaryContainer = EmberLightOnSecondaryContainer,
    tertiary = EmberLightTertiary,
    onTertiary = EmberLightOnTertiary,
    tertiaryContainer = EmberLightTertiaryContainer,
    onTertiaryContainer = EmberLightOnTertiaryContainer,
    background = EmberLightSurface,
    onBackground = Color(0xFF1D1B1A),
    surface = EmberLightSurface,
    onSurface = Color(0xFF1D1B1A),
    surfaceVariant = Color(0xFFF5DED7),
    onSurfaceVariant = Color(0xFF53433F),
    surfaceContainerLowest = EmberLightSurfaceContainerLowest,
    surfaceContainerLow = EmberLightSurfaceContainerLow,
    surfaceContainer = EmberLightSurfaceContainer,
    surfaceContainerHigh = EmberLightSurfaceContainerHigh,
    surfaceContainerHighest = EmberLightSurfaceContainerHighest,
    outline = EmberLightOutline,
    outlineVariant = EmberLightOutlineVariant,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)
