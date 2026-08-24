# Expressive Theme Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the app the warm ember dark-first palette, Outfit/Figtree typography, asymmetric brand shapes, expressive motion, and the shared components every redesigned screen is built from.

**Architecture:** `ui/theme/` grows from one file to five — colours baked from the reference's oklch tokens to sRGB constants, a `Typography` over two bundled variable fonts, `Shapes` plus the three asymmetric card shapes and the cookie blob, and the ember/halo brushes. `OneMindTheme` switches to `MaterialExpressiveTheme` so Material components get the expressive motion scheme, and stops handing the palette to Material You. A new `ui/components/` package holds the ten pieces the screens share.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (BOM 2026.06.01 → material3 1.4.0, ui-text 1.12.0), variable TTFs in `res/font`, minSdk 30 / compileSdk 36, JDK 17.

## Global Constraints

- **Branch: `my-extra-work`.** Never commit to `main`; never merge or fast-forward from `main`.
- **Commit attribution is the user alone.** No `Co-Authored-By`, no `Generated with`, no AI trailer of any kind.
- **No release.** No version bump, tag, APK, or GitHub release unless explicitly asked.
- **One commit per issue**, `(#N)` in the subject, issue filed before implementation and closed afterwards with an AI-attributed comment.
- **GitHub access is curl/python, not `gh`.** Token from `/home/imyuvi/projects/codingagents/.env`, never echoed. **Never run `git remote -v`** — the token is in origin's URL.
- **Build invocation**, always from `/home/imyuvi/projects/codingagents/oneMind`:
  `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew <task>`. `java` is not on `PATH`.
- **Presentation only.** No file under `domain/`, `data/`, or `capture/` changes in this plan. If something appears to require it, stop and report rather than reaching across the seam.
- **minSdk stays 30.** No API in this plan needs raising it; every one used below is available at 30.
- **`440.dp`** is the frame width cap, applied once at a screen root via `PhoneFrame`, never repeated per component.
- **Tap targets are 48 dp** (56 dp for the composer attach button) and are never shrunk.
- Instrumented tests are runnable here — boot the `onemind_test` AVD.

## Verified API facts — three of them correct the design spec

Checked by extracting the resolved AARs from the Gradle cache and running `javap`. Do not
re-litigate these from memory; they were wrong in memory once already.

| Claim | Reality |
|---|---|
| `MotionScheme.expressive()` | **Internal.** `javap` shows `expressive$material3()` — Kotlin `internal`, name-mangled. The design spec's `MaterialTheme(colorScheme, motionScheme = MotionScheme.expressive(), …)` **will not compile.** |
| `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography, content)` | **Public, and not opt-in gated** — `MaterialThemeKt` carries no `Experimental…` annotation. `motionScheme` has a default, which is the expressive scheme. This is the entry point to use. |
| `MaterialTheme.motionScheme` | **Public**, returning a `MotionScheme` with `defaultSpatialSpec()`, `fastSpatialSpec()`, `slowSpatialSpec()`, `defaultEffectsSpec()`, `fastEffectsSpec()`, `slowEffectsSpec()`. Custom animations should read their springs from here rather than hardcoding one. |
| `SingleChoiceSegmentedButtonRow`, `SegmentedButton`, `LargeFloatingActionButton` | Public, no opt-in. Use directly. |
| `LinearWavyProgressIndicator`, `ButtonGroup`, `LoadingIndicator`, `SplitButton` | **Absent** from material3 1.4.0 — no such class in the AAR. Wavy progress is a custom `Canvas`; the connected group is `SingleChoiceSegmentedButtonRow`. |
| Static Outfit/Figtree faces | **Not published.** `google/fonts` ships `Outfit[wght].ttf` (110 KB) and `Figtree[wght].ttf` (62 KB) only — variable, no static instances. The design spec's "seven static files, ~300 KB" is not obtainable from the canonical source. |
| `Font(resId, weight, style, loadingStrategy, variationSettings)` | Public in ui-text 1.12.0 (`FontKt.Font-F3nL8kk`), and `FontVariation.Settings(vararg Setting)` is a public constructor. Two variable files with explicit weight axes replace seven static ones: **174 KB instead of ~300 KB, and better.** |

## File Structure

| File | Responsibility |
|---|---|
`app/src/main/res/font/outfit_variable.ttf` | Display face, variable weight axis |
`app/src/main/res/font/figtree_variable.ttf` | Body/UI face, variable weight axis |
`app/src/main/assets/licenses/OFL-Outfit.txt` | SIL Open Font License, required by the licence |
`app/src/main/assets/licenses/OFL-Figtree.txt` | Same, for Figtree |
`app/src/main/java/com/onemind/app/ui/theme/Type.kt` | Font families + `OneMindTypography` |
`app/src/main/java/com/onemind/app/ui/theme/Colors.kt` | Baked sRGB tokens + the two `ColorScheme`s |
`app/src/main/java/com/onemind/app/ui/theme/Shapes.kt` | `OneMindShapes`, three card shapes, `PillShape`, `CookieShape` |
`app/src/main/java/com/onemind/app/ui/theme/Brushes.kt` | `EmberGradient`, `HaloGradient`, FAB shadow colour |
`app/src/main/java/com/onemind/app/ui/theme/OneMindTheme.kt` | Rewritten entry point |
`app/src/main/java/com/onemind/app/ui/components/PressMorph.kt` | Corner + scale press response |
`app/src/main/java/com/onemind/app/ui/components/WavyProgress.kt` | Scrolling dash strip |
`app/src/main/java/com/onemind/app/ui/components/StateChip.kt` | `ProcessingState` as a chip |
`app/src/main/java/com/onemind/app/ui/components/CategoryChip.kt` | One category |
`app/src/main/java/com/onemind/app/ui/components/StatusPill.kt` | Generic labelled pill |
`app/src/main/java/com/onemind/app/ui/components/HeroHeader.kt` | Eyebrow + 42 sp title over the halo |
`app/src/main/java/com/onemind/app/ui/components/SectionNav.kt` | Feed / Timeline / Events segmented group |
`app/src/main/java/com/onemind/app/ui/components/CookieThumb.kt` | Ember-filled blob thumbnail |
`app/src/main/java/com/onemind/app/ui/components/StaggeredEntrance.kt` | Index-delayed rise |
`app/src/main/java/com/onemind/app/ui/components/PhoneFrame.kt` | 440 dp centred frame |
`app/src/androidTest/java/com/onemind/app/OneMindThemeTest.kt` | The theme's four load-bearing decisions |
`app/src/androidTest/java/com/onemind/app/SharedComponentsTest.kt` | Component behaviour and the 440 dp cap |

Two issues, two commits:

| Issue | Tasks | Subject |
|---|---|---|
| D | 1–5 | Ember theme, expressive motion, bundled Outfit/Figtree |
| E | 6–10 | Shared expressive components |

---

## Task 1: Bundle the two variable fonts

**Files:**
- Create: `app/src/main/res/font/outfit_variable.ttf`, `app/src/main/res/font/figtree_variable.ttf`
- Create: `app/src/main/assets/licenses/OFL-Outfit.txt`, `app/src/main/assets/licenses/OFL-Figtree.txt`

**Interfaces:**
- Consumes: nothing.
- Produces: `R.font.outfit_variable`, `R.font.figtree_variable`.

**Why bundled, not the downloadable-fonts provider.** The provider needs a network fetch
and a provider app present at runtime, which contradicts oneMind's offline,
nothing-leaves-the-device stance, and it reflows text on cold start while the request is
in flight. 174 KB of APK, paid once, is the better trade.

- [ ] **Step 1: Fetch the fonts and their licences**

`res/font` resource names must be lowercase letters, digits and underscores — hence the
rename.

```bash
mkdir -p app/src/main/res/font app/src/main/assets/licenses

curl -sSL -o app/src/main/res/font/outfit_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/outfit/Outfit%5Bwght%5D.ttf"
curl -sSL -o app/src/main/res/font/figtree_variable.ttf \
  "https://github.com/google/fonts/raw/main/ofl/figtree/Figtree%5Bwght%5D.ttf"
curl -sSL -o app/src/main/assets/licenses/OFL-Outfit.txt \
  "https://github.com/google/fonts/raw/main/ofl/outfit/OFL.txt"
curl -sSL -o app/src/main/assets/licenses/OFL-Figtree.txt \
  "https://github.com/google/fonts/raw/main/ofl/figtree/OFL.txt"
```

- [ ] **Step 2: Verify what actually landed**

A truncated or HTML-error download produces a font that fails at runtime, not at build
time, so check before trusting it.

```bash
ls -l app/src/main/res/font/ app/src/main/assets/licenses/
file app/src/main/res/font/*.ttf
```

Expected: `outfit_variable.ttf` ≈ 110 KB, `figtree_variable.ttf` ≈ 62 KB, both reported
by `file` as `TrueType Font data`, and both `OFL.txt` files around 4.4 KB starting with
"Copyright". If `file` says `HTML document`, the URL redirected to an error page — stop
and re-fetch rather than continuing.

- [ ] **Step 3: Confirm the resources compile**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. This is the step that catches an invalid resource name or a
corrupt TTF — AAPT2 parses font resources.

---

## Task 2: `Type.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/theme/Type.kt`

**Interfaces:**
- Consumes: `R.font.outfit_variable`, `R.font.figtree_variable` (Task 1).
- Produces: `val Outfit: FontFamily`, `val Figtree: FontFamily`, `val OneMindTypography: Typography`.

**Where the numbers come from.** DESIGN-GUIDE §5.2 for the eight named slots, §5.5 for
the sizes that must not drift. §5.4 describes the hero as `displayMedium` 40 sp while
§5.5 lists "hero title 42 sp"; §5.5 is the section headed "numbers to keep verbatim", so
the hero uses `displayLarge` at 42 sp and `displayMedium` keeps 40 sp for anything that
wants the smaller cut.

- [ ] **Step 1: Write the file**

```kotlin
package com.onemind.app.ui.theme

import androidx.compose.material3.Typography
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
```

- [ ] **Step 2: Confirm it compiles**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Nothing reads `OneMindTypography` yet — Task 5 wires it in.

---

## Task 3: `Colors.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/theme/Colors.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: the token `Color` vals below, plus `val EmberDarkColorScheme: ColorScheme` and `val EmberLightColorScheme: ColorScheme`.

**Every constant below was computed** from the `styles.css` oklch value through
oklab → linear sRGB → sRGB, not eyeballed, and each carries its source oklch in a
trailing comment so the derivation stays checkable.

- [ ] **Step 1: Write the file**

```kotlin
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
```

- [ ] **Step 2: Confirm it compiles**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

## Task 4: `Shapes.kt` and `Brushes.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/theme/Shapes.kt`
- Create: `app/src/main/java/com/onemind/app/ui/theme/Brushes.kt`

**Interfaces:**
- Consumes: the colour tokens (Task 3).
- Produces: `OneMindShapes`, `CardShapeLarge`, `CardShapeMedium`, `CardShapeSmall`, `PillShape`, `CookieShape`, `EmberGradient`, `HaloGradient`, `FabShadowColor`.

- [ ] **Step 1: Write `Shapes.kt`**

```kotlin
package com.onemind.app.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp

/*
 * `--radius: 1.5rem` (24 dp) and its derivations, plus the asymmetric card corners that
 * are the brand's signature.
 */

val OneMindShapes = Shapes(
    extraLarge = RoundedCornerShape(32.dp),
    large = RoundedCornerShape(24.dp),
    medium = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(12.dp),
    extraSmall = RoundedCornerShape(8.dp)
)

/*
 * The three card silhouettes, verbatim from DESIGN-GUIDE §5.3.
 *
 * Each has exactly one corner that disagrees with the others, and which corner it is
 * changes per size. That asymmetry is the thing a viewer recognises without noticing, so
 * these numbers are copied rather than derived and must not be tidied into a formula.
 */
val CardShapeLarge = RoundedCornerShape(
    topStart = 40.dp, topEnd = 16.dp, bottomEnd = 40.dp, bottomStart = 40.dp
)
val CardShapeMedium = RoundedCornerShape(
    topStart = 32.dp, topEnd = 32.dp, bottomEnd = 12.dp, bottomStart = 32.dp
)
val CardShapeSmall = RoundedCornerShape(
    topStart = 12.dp, topEnd = 32.dp, bottomEnd = 32.dp, bottomStart = 32.dp
)

/** `border-radius: 9999px` — pills, switches, the wavy track. */
val PillShape = RoundedCornerShape(percent = 50)

/**
 * `--shape-cookie: 42% 58% 54% 46% / 48% 42% 58% 52%`.
 *
 * CSS elliptical border-radius: the first four percentages are horizontal radii as a
 * fraction of width (top-left, top-right, bottom-right, bottom-left), the second four
 * are vertical radii as a fraction of height. `RoundedCornerShape` cannot express
 * per-corner *elliptical* radii, so this is four quarter-ellipse arcs.
 *
 * The percentages pair to exactly 100% on every edge — 42+58 across the top, 54+46
 * across the bottom, 48+52 down the left, 42+58 down the right — so no edge has any
 * straight segment and the four arcs meet without a join. That is what makes it read as
 * a blob rather than a rounded rectangle, and it is also why the path closes exactly:
 * the last arc's end point is the first arc's start point.
 *
 * Not unit-tested. `Path` bounds need a real graphics stack, and a test that only
 * asserted the fractions would be asserting the source it was copied from. Verified by
 * eye against `index.html`.
 */
val CookieShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height

    val tlX = 0.42f * w; val tlY = 0.48f * h
    val trX = 0.58f * w; val trY = 0.42f * h
    val brX = 0.54f * w; val brY = 0.58f * h
    val blX = 0.46f * w; val blY = 0.52f * h

    // Each arc's bounding rect is the full ellipse it belongs to, so the rect can extend
    // past the shape's own bounds where a radius exceeds half the side. That is correct:
    // only the swept quarter is drawn.
    moveTo(0f, tlY)
    arcTo(Rect(0f, 0f, 2 * tlX, 2 * tlY), 180f, 90f, false)
    arcTo(Rect(w - 2 * trX, 0f, w, 2 * trY), 270f, 90f, false)
    arcTo(Rect(w - 2 * brX, h - 2 * brY, w, h), 0f, 90f, false)
    arcTo(Rect(0f, h - 2 * blY, 2 * blX, h), 90f, 90f, false)
    close()
}
```

- [ ] **Step 2: Write `Brushes.kt`**

```kotlin
package com.onemind.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * `--gradient-ember` and `--gradient-halo`.
 */

/**
 * `linear-gradient(135deg, …)` — image placeholders and thumbnails.
 *
 * Compose's `linearGradient` defaults to `start = Offset.Zero, end = Offset.Infinite`,
 * which resolves to the drawing area's opposite corner: top-left to bottom-right, which
 * is what CSS 135deg means. No explicit offsets needed.
 */
val EmberGradient: Brush = Brush.linearGradient(
    0f to OneMindEmber0,
    0.55f to OneMindEmber55,
    1f to OneMindEmber100
)

/**
 * `radial-gradient(120% 90% at 50% 0%, …)` — the warm wash behind a hero.
 *
 * One deliberate deviation: CSS specifies an *ellipse* 120% of the width by 90% of the
 * height, and Compose's `radialGradient` is circular only. Making it elliptical needs a
 * shader local matrix that `RadialGradientShader` does not expose. On a soft wash that
 * fades to nothing by 70% the difference is not visible, so the radius takes the width
 * term and the height term is dropped.
 *
 * The outer stop is the background at zero alpha rather than the inner colour at zero
 * alpha, matching the CSS: fading toward the page colour and fading toward transparent
 * are the same result only when the interpolation is premultiplied, and this way it does
 * not depend on that.
 */
val HaloGradient: Brush = Brush.radialGradient(
    0f to OneMindHalo.copy(alpha = 0.85f),
    0.7f to OneMindBackground.copy(alpha = 0f),
    1f to OneMindBackground.copy(alpha = 0f),
    center = androidx.compose.ui.geometry.Offset.Unspecified,
    radius = Float.POSITIVE_INFINITY
)

/**
 * `--shadow-fab: 0 14px 34px -10px primary/0.45`.
 *
 * Passed to `Modifier.shadow(ambientColor =, spotColor =)`, which honours colour from
 * API 28 — always, at this app's minSdk of 30. Cards deliberately get none of this:
 * DESIGN-GUIDE §2 is explicit that cards rely on tonal elevation, and the FAB is the
 * only element that pops.
 */
val FabShadowColor: Color = OneMindPrimary.copy(alpha = 0.45f)
```

- [ ] **Step 3: Fix the halo's geometry**

`Brush.radialGradient` with `center = Offset.Unspecified` centres on the drawing area,
but the halo hangs from the *top* edge (`at 50% 0%`), and its radius is relative to the
drawn width, which a top-level `val` cannot know. Replace the `HaloGradient` value above
with a `ShaderBrush` that is handed the size at draw time:

```kotlin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

val HaloGradient: Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader = RadialGradientShader(
        // `at 50% 0%` — hanging from the top edge, not centred in the box.
        center = Offset(size.width * 0.5f, 0f),
        // The `120%` width term. See the note above on why the 90% height term is lost.
        radius = size.width * 1.2f,
        colors = listOf(
            OneMindHalo.copy(alpha = 0.85f),
            OneMindBackground.copy(alpha = 0f)
        ),
        colorStops = listOf(0f, 0.7f)
    )
}
```

Delete the `Brush.radialGradient(...)` version and the now-unused fully-qualified
`Offset.Unspecified` reference. The final file imports `Brush`, `Color`, `Offset`,
`Size`, `RadialGradientShader`, `Shader`, `ShaderBrush`.

- [ ] **Step 4: Confirm both compile**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

---

## Task 5: Rewrite `OneMindTheme` — Issue D lands here

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/theme/OneMindTheme.kt` (whole file)
- Test: `app/src/androidTest/java/com/onemind/app/OneMindThemeTest.kt` (create)

**Interfaces:**
- Consumes: `EmberDarkColorScheme`, `EmberLightColorScheme` (Task 3), `OneMindShapes` (Task 4), `OneMindTypography` (Task 2).
- Produces: `OneMindTheme(darkTheme: Boolean = true, dynamicColor: Boolean = false, content: @Composable () -> Unit)` — same name and shape, two changed defaults.

**The two default changes, and why each is load-bearing.**

- **`dynamicColor: true → false`.** Left on, Material You replaces the ember scheme on
  every Android 12+ device — which is effectively every device, since minSdk is 30.
  DESIGN-GUIDE §5.1 is explicit that the brand palette wins. The parameter stays so a
  future user-facing toggle has somewhere to land.
- **`darkTheme: isSystemInDarkTheme() → true`.** The reference is dark-first. A light
  scheme is still provided, but as the option rather than the default.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/onemind/app/OneMindThemeTest.kt`:

```kotlin
package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.ui.theme.Figtree
import com.onemind.app.ui.theme.OneMindBackground
import com.onemind.app.ui.theme.OneMindPrimary
import com.onemind.app.ui.theme.OneMindTheme
import com.onemind.app.ui.theme.Outfit
import org.junit.Assert.*
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
                SideEffect {
                    captured.systemInDarkTheme = systemDark
                    captured.primary = MaterialTheme.colorScheme.primary
                    captured.surface = MaterialTheme.colorScheme.surface
                    captured.displayLargeFamily = MaterialTheme.typography.displayLarge.fontFamily
                    captured.labelSmallFamily = MaterialTheme.typography.labelSmall.fontFamily
                    captured.displayLargeSize = MaterialTheme.typography.displayLarge.fontSize.value
                    captured.largeShape = MaterialTheme.shapes.large as? RoundedCornerShape
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
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.OneMindThemeTest
```

Expected: `theBrandPaletteSurvivesByDefault` fails — on an Android 12+ emulator
`dynamicColor` defaults to true, so `primary` is the wallpaper's, not `#EF8D67`. The
typography and shape tests fail too, since the current theme passes neither.

- [ ] **Step 3: Rewrite the theme**

Replace the whole of `app/src/main/java/com/onemind/app/ui/theme/OneMindTheme.kt`:

```kotlin
package com.onemind.app.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
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
 * [MaterialExpressiveTheme] rather than [androidx.compose.material3.MaterialTheme]: it is
 * the public entry point that installs the expressive `MotionScheme`, which is what gives
 * Material's own components their spring behaviour. `MotionScheme.expressive()` itself is
 * `internal` to material3 1.4.0 and cannot be passed explicitly, so the parameter is left
 * to its default. Custom animations read their springs from `MaterialTheme.motionScheme`,
 * which is public, so they match rather than approximate.
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

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        shapes = OneMindShapes,
        typography = OneMindTypography,
        content = content
    )
}
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.OneMindThemeTest
```

Expected: 5 tests, 0 failures. If `darkIsTheDefaultRatherThanTheSystemPreference` fails
on its `assertFalse`, the emulator is in dark mode — switch it to light and re-run; do
not weaken the assertion.

- [ ] **Step 5: Full verification**

The whole app now renders in the new palette, so every existing screen test is a
regression check on it.

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on both. A failure in `EventsScreenTest` here is meaningful
rather than incidental — the new typography changes text metrics, and that test asserts a
header clears the status bar.

- [ ] **Step 6: Look at it**

Install and open the app. Every screen should be warm dark with the ember accent, titles
in Outfit and body text in Figtree. This is the step that catches a font that downloaded
as an HTML error page: text falls back to Roboto and nothing fails.

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

- [ ] **Step 7: File Issue D**

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Ember theme, expressive motion, bundled Outfit/Figtree",
  "body": """The app ships the Compose template's theme: `darkColorScheme()` and
`lightColorScheme()` with no arguments, no typography, no shapes, and `dynamicColor`
defaulted to `true`. None of `design-reference/` reaches the screen.

`dynamicColor = true` is the one worth calling out. On any Android 12+ device — so, at
minSdk 30, effectively every device — Material You substitutes the user's wallpaper
palette. The app looks deliberate, just not like itself, which is why nothing catches it.
DESIGN-GUIDE §5.1 is explicit that the brand palette wins.

**Fix.** `ui/theme/` grows to five files: the `styles.css` oklch tokens baked to sRGB
constants with their source in trailing comments, a `Typography` over bundled Outfit and
Figtree, `Shapes` plus the three asymmetric card silhouettes and the cookie blob, and the
ember/halo brushes. `OneMindTheme` moves to `MaterialExpressiveTheme` so Material's own
components pick up the expressive motion scheme, and defaults to dark with dynamic colour
off.

Three things the design spec got wrong, verified against the resolved AARs with `javap`:

- `MotionScheme.expressive()` is `internal` in material3 1.4.0 and cannot be passed
  explicitly. `MaterialExpressiveTheme` is the public entry point and defaults to it.
  `MaterialTheme.motionScheme` *is* public, so custom animations can match.
- `LinearWavyProgressIndicator`, `ButtonGroup`, `LoadingIndicator` and `SplitButton` do
  not exist in 1.4.0 at all.
- `google/fonts` publishes no static Outfit or Figtree instances, only variable files. Two
  variable TTFs with explicit weight axes replace the seven static faces the spec called
  for: 174 KB instead of ~300 KB.

Design: `docs/superpowers/specs/2026-08-24-onemind-ui-redesign-design.md`
Plan: `docs/superpowers/plans/2026-08-24-expressive-theme-foundation.md`"""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Note the number; call it `D`.

- [ ] **Step 8: Commit Issue D**

```bash
git rev-parse --abbrev-ref HEAD   # must print my-extra-work

git add app/src/main/res/font/ \
        app/src/main/assets/licenses/ \
        app/src/main/java/com/onemind/app/ui/theme/ \
        app/src/androidTest/java/com/onemind/app/OneMindThemeTest.kt

git commit -F - <<'MSG'
feat(ui): ember theme, expressive motion, bundled Outfit and Figtree (#D)

The app was still on the Compose template's theme: argument-less color
schemes, no typography, no shapes, and dynamicColor defaulted to true. That
last one meant Material You replaced the palette on every Android 12+ device,
so at minSdk 30 the brand scheme reached essentially nobody — and the result
looked deliberate, which is why it went unnoticed. It is off now, with the
parameter kept so a future user-facing toggle has somewhere to land.

ui/theme/ is five files. Every styles.css oklch token is baked to an sRGB
constant with its source in a trailing comment: compile-time constants, and a
derivation that stays checkable without re-deriving the sheet. Typography is
Outfit and Figtree, with every unused slot re-pointed at one of the two so no
stray Roboto label survives. Shapes carry the three asymmetric card
silhouettes verbatim — the notch is the signature, so those numbers are copied
and not derived — plus the cookie blob as four quarter-ellipse arcs, since
RoundedCornerShape cannot express per-corner elliptical radii.

MaterialExpressiveTheme rather than MaterialTheme, because MotionScheme
.expressive() is internal in material3 1.4.0 and cannot be passed in.
MaterialTheme.motionScheme is public, so custom animations read their springs
from there instead of hardcoding a spring that drifts from Material's.

Fonts are two variable TTFs, not the seven static faces originally planned:
google/fonts publishes no static instances of either family. 174 KB, and the
weight axis is driven explicitly per registered weight so a missing variation
setting cannot silently render everything at one weight. OFL text ships
alongside, as the licence requires.

Halo gradient is one knowing deviation: CSS asks for a 120%x90% ellipse and
Compose's radial shader is circular only. On a wash that fades to nothing by
70% it is not visible, and the alternative is a shader local matrix that
RadialGradientShader does not expose.
MSG
```

Substitute the real number for `#D`.

- [ ] **Step 9: Close Issue D**

Post this comment, then PATCH the issue to `closed` (see the helper in Task 5 of the
events plan for the exact python shape):

```
Done.

`ui/theme/` is now Colors, Type, Shapes, Brushes and a rewritten `OneMindTheme`. Dynamic
colour is off by default and dark is the default; `OneMindThemeTest` pins both, along with
the two bundled faces, the 42 sp hero size, and the 24 dp base radius. Its dark-default
test asserts the *device* is in light mode first, so it cannot pass for the wrong reason.

Three corrections to the design spec, all verified with `javap` against the resolved AARs
rather than from memory:

- `MotionScheme.expressive()` is `internal` — the spec's five-argument `MaterialTheme`
  call would not have compiled. `MaterialExpressiveTheme` is the public entry point.
- `LinearWavyProgressIndicator`, `ButtonGroup`, `LoadingIndicator` and `SplitButton` are
  absent from material3 1.4.0.
- No static Outfit/Figtree faces are published. Two variable TTFs, 174 KB total.

One knowing deviation: the halo is circular rather than the CSS ellipse, because
`RadialGradientShader` exposes no local matrix. Invisible on a wash that fades out by 70%.

Verified: `assembleDebug`, `testDebugUnitTest`, full instrumented suite on the
`onemind_test` AVD, and installed on device to confirm the faces actually loaded — a font
that downloads as an HTML error page falls back to Roboto without failing anything.

*Implemented by an AI agent (Claude), reviewed against the design reference.*
```

---

## Task 6: `PressMorph.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/components/PressMorph.kt`

**Interfaces:**
- Consumes: `MaterialTheme.motionScheme` (verified public).
- Produces:
  - `data class PressMorphState(val corner: Dp, val scale: Float)`
  - `@Composable fun rememberPressMorph(interactionSource: InteractionSource, restCorner: Dp, pressedCorner: Dp = PressMorphDefaults.PressedCorner): PressMorphState`
  - `fun Modifier.pressScale(state: PressMorphState): Modifier`
  - `object PressMorphDefaults { val PressedCorner: Dp; const val PressedScale: Float }`

**Why a state object rather than a Modifier.** The morph needs the corner radius to reach
a `Card`'s `shape` parameter, and a `Modifier` cannot hand a value back to its caller. So
the animation is a composable that returns both values, and only the cheap half — the
scale — is applied as a modifier.

- [ ] **Step 1: Write the file**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * 96%, both on the expressive spatial spring.
 *
 * Returned as a value rather than applied as a `Modifier` because the corner has to reach
 * a `Card`'s `shape` parameter, and a `Modifier` cannot hand anything back to its caller.
 *
 * The spring comes from `MaterialTheme.motionScheme` rather than a literal
 * `spring(dampingRatio, stiffness)`. Inside `MaterialExpressiveTheme` that resolves to the
 * expressive scheme, so a custom morph and a Material component pressed beside it move
 * with the same physics — which is the whole point, and is exactly what drifts apart when
 * a spring is written out by hand in one place.
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
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "pressMorphCorner"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressMorphDefaults.PressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
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
```

- [ ] **Step 2: Confirm it compiles**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. `slowSpatialSpec()` is generic in its return type — if the
compiler cannot infer `Dp` and `Float` at these two call sites, write them as
`slowSpatialSpec<Dp>()` and `slowSpatialSpec<Float>()`.

---

## Task 7: `WavyProgress.kt` and `StateChip.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/components/WavyProgress.kt`
- Create: `app/src/main/java/com/onemind/app/ui/components/StateChip.kt`
- Test: `app/src/androidTest/java/com/onemind/app/SharedComponentsTest.kt` (create)

**Interfaces:**
- Consumes: `PillShape` (Task 4), `OneMindSuccess` / `OneMindWarning` / colour tokens (Task 3).
- Produces:
  - `@Composable fun WavyProgress(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary)`
  - `@Composable fun StateChip(state: ProcessingState, modifier: Modifier = Modifier)`
  - `WavyProgressDefaults.Width: Dp` (32 dp), `WavyProgressDefaults.Height: Dp` (4 dp)

**Why a `Canvas`.** `LinearWavyProgressIndicator` does not exist in material3 1.4.0 —
`javap` finds no such class. DESIGN-GUIDE §3.3 sanctions the fallback explicitly.

- [ ] **Step 1: Write the failing test**

Create `app/src/androidTest/java/com/onemind/app/SharedComponentsTest.kt`:

```kotlin
package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.components.StateChip
import com.onemind.app.ui.theme.OneMindTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared components' behaviour, not their looks.
 *
 * Visual fidelity is checked by comparison against the reference HTML — a test cannot
 * tell you a corner is 32 dp rather than 24 dp in a way that is cheaper than looking.
 * What a test *can* pin is the part that carries meaning: which label a state gets, and
 * that the frame really does cap its content.
 */
@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyProcessingStateGetsItsOwnLabel() {
        composeRule.setContent {
            OneMindTheme {
                androidx.compose.foundation.layout.Column {
                    ProcessingState.entries.forEach { StateChip(state = it) }
                }
            }
        }

        // One chip per state, each saying something different. A `when` that fell through
        // to a shared default would render six identical chips and look fine.
        val labels = listOf(
            "Draft", "Saved", "Processing", "Ready", "Edited", "Failed"
        )
        labels.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SharedComponentsTest
```

Expected: compilation failure, `Unresolved reference: StateChip`.

- [ ] **Step 3: Write `WavyProgress.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object WavyProgressDefaults {
    /** `.wavy-track.w-8` — the width the state chip uses. */
    val Width: Dp = 32.dp

    /** `height: 4px`. */
    val Height: Dp = 4.dp

    /** `primary 0 6px, transparent 6px 12px` — one dash plus one gap. */
    val Period: Dp = 12.dp
    val Dash: Dp = 6.dp
}

/**
 * The scrolling dash strip that marks work in progress.
 *
 * A `Canvas` because `LinearWavyProgressIndicator` does not exist in material3 1.4.0 —
 * the artifact ships its token class and no composable. DESIGN-GUIDE §3.3 sanctions this
 * fallback.
 *
 * `styles.css` animates `background-position-x` by 24 px over 1.6 s across a repeating
 * gradient whose own period is 12 px. Two periods per cycle is indistinguishable from
 * one period at half the duration, so this shifts by one 12 dp period over 800 ms —
 * same 15 dp/s, one fewer number to keep in step.
 *
 * Indeterminate on purpose: enrichment has no measurable fraction complete, and a bar
 * that crept to 90% and waited would be a claim the pipeline cannot make.
 */
@Composable
fun WavyProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val transition = rememberInfiniteTransition(label = "wavy")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "wavyPhase"
    )

    Canvas(
        modifier = modifier
            .width(WavyProgressDefaults.Width)
            .height(WavyProgressDefaults.Height)
    ) {
        val period = WavyProgressDefaults.Period.toPx()
        val dash = WavyProgressDefaults.Dash.toPx()
        val centreY = size.height / 2f

        // Start one period off-screen so a dash scrolls in rather than appearing.
        var x = -period + phase * period
        while (x < size.width) {
            val start = x.coerceAtLeast(0f)
            val end = (x + dash).coerceAtMost(size.width)
            if (end > start) {
                drawLine(
                    color = color,
                    start = Offset(start, centreY),
                    end = Offset(end, centreY),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round
                )
            }
            x += period
        }
    }
}
```

`animateFloat` is an extension on `InfiniteTransition` in
`androidx.compose.animation.core` — add
`import androidx.compose.animation.core.animateFloat`.

- [ ] **Step 4: Write `StateChip.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.theme.OneMindSuccess
import com.onemind.app.ui.theme.OneMindWarning
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * A Memory's processing state, as a pill.
 *
 * The `when` is exhaustive over [ProcessingState] with no `else`, so a seventh state stops
 * the build here instead of silently rendering as one of the six.
 *
 * Only `PROCESSING` carries the wavy strip. `SAVED` and `EDITED` are also "not done yet",
 * but nothing is running for them — showing motion would claim work is happening when the
 * Memory is sitting in a queue.
 */
@Composable
fun StateChip(state: ProcessingState, modifier: Modifier = Modifier) {
    val label: String
    val container: Color
    val content: Color

    when (state) {
        ProcessingState.DRAFT -> {
            label = "Draft"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        ProcessingState.SAVED -> {
            label = "Saved"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
        ProcessingState.PROCESSING -> {
            label = "Processing"
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }
        ProcessingState.READY -> {
            label = "Ready"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = OneMindSuccess
        }
        ProcessingState.EDITED -> {
            label = "Edited"
            container = MaterialTheme.colorScheme.surfaceContainer
            content = OneMindWarning
        }
        ProcessingState.FAILED -> {
            label = "Failed"
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(modifier = modifier, shape = PillShape, color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = Tracking.Chip,
                color = content
            )
            if (state == ProcessingState.PROCESSING) {
                WavyProgress(color = content)
            }
        }
    }
}
```

- [ ] **Step 5: Run the test and watch it pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SharedComponentsTest
```

Expected: 1 test, 0 failures.

---

## Task 8: `HeroHeader.kt`, `SectionNav.kt`, `PhoneFrame.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/components/HeroHeader.kt`
- Create: `app/src/main/java/com/onemind/app/ui/components/SectionNav.kt`
- Create: `app/src/main/java/com/onemind/app/ui/components/PhoneFrame.kt`
- Test: `app/src/androidTest/java/com/onemind/app/SharedComponentsTest.kt` (extend)

**Interfaces:**
- Consumes: `HaloGradient` (Task 4), `Tracking` (Task 2).
- Produces:
  - `@Composable fun HeroHeader(eyebrow: String, title: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)`
  - `enum class SectionDestination { FEED, TIMELINE, EVENTS }` with `val label: String`
  - `@Composable fun SectionNav(selected: SectionDestination, onSelect: (SectionDestination) -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun PhoneFrame(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`
  - `PhoneFrameDefaults.MaxWidth: Dp` (440 dp)

**`SectionNav` carries no routes.** It takes a `SectionDestination` and a callback; the
mapping to `NavRoutes` lives in the navigation layer, which is the next plan. That keeps
this component compilable now — `NavRoutes.TIMELINE` does not exist yet — and keeps
navigation policy out of a presentation component either way.

- [ ] **Step 1: Write the failing tests**

Append inside `SharedComponentsTest`:

```kotlin
    @Test
    fun theFrameCapsItsContentAtTheReferenceWidth() {
        composeRule.setContent {
            OneMindTheme {
                // 800 dp regardless of the device, so the cap is exercised on a phone
                // emulator whose screen is narrower than 440 dp and would otherwise make
                // this pass without the frame doing anything.
                Box(modifier = Modifier.requiredWidth(800.dp)) {
                    com.onemind.app.ui.components.PhoneFrame {
                        Box(
                            modifier = Modifier
                                .testTag("framed")
                                .androidx.compose.foundation.layout.fillMaxWidth()
                                .androidx.compose.foundation.layout.height(40.dp)
                        )
                    }
                }
            }
        }

        val width = composeRule.onNodeWithTag("framed").getBoundsInRoot().width
        assertTrue(
            "framed content is ${width.value}dp wide; the reference frame is 440dp",
            width.value <= 440f + 0.5f
        )
        // And not collapsed to nothing, which would also satisfy the bound above.
        assertTrue("framed content collapsed to ${width.value}dp", width.value > 100f)
    }

    @Test
    fun theHeroShowsItsEyebrowAndTitle() {
        composeRule.setContent {
            OneMindTheme {
                com.onemind.app.ui.components.HeroHeader(
                    eyebrow = "Your mind",
                    title = "Everything you saved"
                )
            }
        }

        composeRule.onNodeWithText("Your mind").assertIsDisplayed()
        composeRule.onNodeWithText("Everything you saved").assertIsDisplayed()
    }

    @Test
    fun theHeroKeepsItsTitleOutFromUnderTheStatusBar() {
        // The header replaces the Scaffold + TopAppBar that used to consume this inset.
        // MainActivity calls enableEdgeToEdge(), so nothing else will. EventsScreen
        // shipped without it once (#37) and drew its first row across the system clock;
        // four screens now depend on this one composable getting it right.
        composeRule.activity.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
        }
        composeRule.setContent {
            OneMindTheme {
                com.onemind.app.ui.components.HeroHeader(
                    eyebrow = "Your mind",
                    title = "Everything you saved"
                )
            }
        }
        composeRule.waitForIdle()

        var statusBarPx = 0
        composeRule.activity.runOnUiThread {
            statusBarPx = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())
                ?.top ?: 0
        }
        composeRule.waitForIdle()
        val statusBarDp = statusBarPx / composeRule.activity.resources.displayMetrics.density

        assertTrue(
            "This device reports no status bar inset, so the overlap cannot be observed " +
                "and this test would pass for the wrong reason",
            statusBarDp > 0f
        )
        val eyebrowTop = composeRule.onNodeWithText("YOUR MIND").getBoundsInRoot().top
        assertTrue(
            "eyebrow starts at ${eyebrowTop.value}dp, inside the ${statusBarDp}dp status bar",
            eyebrowTop.value >= statusBarDp
        )
    }

    @Test
    fun theSectionNavReportsWhatWasTapped() {
        var selected = com.onemind.app.ui.components.SectionDestination.FEED
        composeRule.setContent {
            OneMindTheme {
                com.onemind.app.ui.components.SectionNav(
                    selected = com.onemind.app.ui.components.SectionDestination.FEED,
                    onSelect = { selected = it }
                )
            }
        }

        composeRule.onNodeWithText("Timeline").performClick()
        composeRule.waitForIdle()

        assertEquals(com.onemind.app.ui.components.SectionDestination.TIMELINE, selected)
    }
```

Add the imports `androidx.compose.foundation.layout.fillMaxWidth`,
`androidx.compose.foundation.layout.height`, `androidx.compose.ui.test.performClick`,
`androidx.activity.enableEdgeToEdge`, `androidx.core.view.ViewCompat`,
`androidx.core.view.WindowInsetsCompat`, and replace the fully-qualified references above
with proper imports — `com.onemind.app.ui.components.HeroHeader`, `.PhoneFrame`,
`.SectionNav`, `.SectionDestination` — once they exist. They are spelled out here only so
the snippet is unambiguous about which symbol is meant.

The inset test asserts on `"YOUR MIND"` rather than `"Your mind"` because [HeroHeader]
uppercases the eyebrow. The status-bar guard mirrors `EventsScreenTest`'s: without it, a
device reporting no inset would pass the overlap assertion for the wrong reason.

- [ ] **Step 2: Run them and watch them fail**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SharedComponentsTest
```

Expected: compilation failure, `Unresolved reference: PhoneFrame`.

- [ ] **Step 3: Write `PhoneFrame.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object PhoneFrameDefaults {
    /** DESIGN-GUIDE §5.5, a "keep verbatim" number. */
    val MaxWidth: Dp = 440.dp
}

/**
 * Centre and width-cap a screen's content.
 *
 * Applied once at a screen's root, never per component — the reference is a 440 dp phone
 * frame, and repeating the constraint inside it would compound. On a phone the cap does
 * nothing; on a tablet or an unfolded foldable it is what stops a two-column bento grid
 * stretching into something the layout was never designed for.
 */
@Composable
fun PhoneFrame(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = PhoneFrameDefaults.MaxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            content = content
        )
    }
}
```

- [ ] **Step 4: Write `HeroHeader.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.HaloGradient
import com.onemind.app.ui.theme.Tracking

/**
 * The large header the feed, timeline, events and onboarding screens share.
 *
 * `displayLarge` at 42 sp, per DESIGN-GUIDE §5.5's "numbers to keep verbatim". §5.4
 * describes the same header as `displayMedium` 40 sp; §5.5 wins, being the section that
 * says it must not drift.
 *
 * The halo is drawn as this composable's background rather than the screen's, so it is
 * sized to the header and fades out within it. Painted behind the text and behind nothing
 * else, which is what keeps a scrolling list from carrying a warm cast down the page.
 *
 * **This header consumes the status-bar inset, and that is not cosmetic.** `MainActivity`
 * calls `enableEdgeToEdge()`, so every destination owns its own insets. Screens used to get
 * that from a `Scaffold` with a `TopAppBar`; the ones this header replaces no longer have
 * either. `EventsScreen` shipped without it once already and drew its first row on top of
 * the system clock — issue #37, and there is an instrumented test pinning it. The inset
 * padding is applied *after* the background on purpose: the halo fills the full area,
 * including behind the status bar, while the text starts below it.
 *
 * Two slots, and they are not interchangeable. [leading] sits on its own row *above* the
 * eyebrow, which is where the reference puts a back button on every screen that has one
 * (`search.html`, `events.html`, `onboarding.html`, `settings.html`). [trailing] sits to
 * the right of the title, which is where the feed and timeline put settings. Putting a back
 * arrow in [trailing] would place it under the user's thumb on the wrong side and read as
 * an action rather than a way out.
 */
@Composable
fun HeroHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HaloGradient)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
    ) {
        if (leading != null) {
            Row(modifier = Modifier.padding(bottom = 8.dp)) { leading() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = Tracking.Eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (trailing != null) trailing()
        }
    }
}
```

- [ ] **Step 5: Write `SectionNav.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The three destinations the segmented group switches between.
 *
 * Carries labels and nothing else. Mapping a destination to a route is navigation policy
 * and lives in the navigation layer — which also means this component compiles before
 * `NavRoutes` has learned about Timeline.
 */
enum class SectionDestination(val label: String) {
    FEED("Feed"),
    TIMELINE("Timeline"),
    EVENTS("Events")
}

/**
 * The connected button group across the top of Feed, Timeline and Events.
 *
 * `SingleChoiceSegmentedButtonRow` because the reference's "connected button group" is
 * exactly that, and because material3 1.4.0 ships no `ButtonGroup` — verified against the
 * AAR, which contains no such class. DESIGN-GUIDE §5.4 already maps it here.
 */
@Composable
fun SectionNav(
    selected: SectionDestination,
    onSelect: (SectionDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val entries = SectionDestination.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        entries.forEachIndexed { index, destination ->
            SegmentedButton(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = entries.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                label = { Text(destination.label, style = MaterialTheme.typography.titleSmall) }
            )
        }
    }
}
```

- [ ] **Step 6: Run the tests and watch them pass**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.onemind.app.SharedComponentsTest
```

Expected: 5 tests, 0 failures. If `SegmentedButtonDefaults.colors(...)` rejects those
parameter names, print the available ones with
`JAVA_HOME=… ./gradlew assembleDebug` and read the compiler's suggestion — the
`activeContainerColor` / `inactiveContainerColor` naming is what material3 1.4.0 uses, but
adapt rather than dropping the colour mapping.

---

## Task 9: `CategoryChip.kt`, `StatusPill.kt`, `CookieThumb.kt`, `StaggeredEntrance.kt`

**Files:**
- Create: `app/src/main/java/com/onemind/app/ui/components/CategoryChip.kt`
- Create: `app/src/main/java/com/onemind/app/ui/components/StatusPill.kt`
- Create: `app/src/main/java/com/onemind/app/ui/components/CookieThumb.kt`
- Create: `app/src/main/java/com/onemind/app/ui/components/StaggeredEntrance.kt`

**Interfaces:**
- Consumes: `PillShape`, `CookieShape`, `EmberGradient` (Task 4), `Tracking` (Task 2), `MaterialTheme.motionScheme`.
- Produces:
  - `@Composable fun CategoryChip(name: String, modifier: Modifier = Modifier)`
  - `@Composable fun StatusPill(label: String, container: Color, content: Color, modifier: Modifier = Modifier)`
  - `@Composable fun CookieThumb(modifier: Modifier = Modifier, size: Dp = CookieThumbDefaults.Size)`
  - `@Composable fun StaggeredEntrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit)`

**`CategoryChip` uses `primaryContainer`**, per DESIGN-GUIDE §5.4. It supersedes the
private `CategoryChip` the events plan added to `EventsScreen.kt`, which used
`secondaryContainer` as interim phase-2 styling; the Events restyle deletes that one.

- [ ] **Step 1: Write `CategoryChip.kt` and `StatusPill.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemind.app.ui.theme.Tracking

/**
 * One of a Memory's categories.
 *
 * No click semantics. The card a chip sits on is already clickable and goes to the same
 * place, so a nested clickable would give a screen reader a second target that does the
 * same thing — and filtering by tapping a chip is not a behaviour this app has.
 *
 * 12 sp and a 12 dp corner, both from DESIGN-GUIDE §5.4, and both smaller than any
 * `Typography` slot offers — hence the explicit `fontSize` rather than a slot.
 */
@Composable
fun CategoryChip(name: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            letterSpacing = Tracking.Chip,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
```

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * A labelled pill with no behaviour: "In calendar", "Rejected", a source name.
 *
 * Colours are parameters rather than derived from a status enum, because the same pill
 * serves several unrelated vocabularies. [StateChip] is the one that owns a mapping,
 * because `ProcessingState` has exactly one right set of colours.
 */
@Composable
fun StatusPill(
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = PillShape, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = Tracking.Chip,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
```

- [ ] **Step 2: Write `CookieThumb.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onemind.app.ui.theme.CookieShape
import com.onemind.app.ui.theme.EmberGradient

object CookieThumbDefaults {
    val Size: Dp = 56.dp
}

/**
 * The blob thumbnail on medium and small cards.
 *
 * A placeholder, not an image: it stands in for a Memory that has an image without
 * decoding one, which is what keeps a bento grid cheap to scroll. When a real thumbnail
 * is available it goes inside this same clip, so the silhouette does not change.
 */
@Composable
fun CookieThumb(
    modifier: Modifier = Modifier,
    size: Dp = CookieThumbDefaults.Size
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CookieShape)
            .background(EmberGradient)
    )
}
```

- [ ] **Step 3: Write `StaggeredEntrance.kt`**

```kotlin
package com.onemind.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val STAGGER_STEP_MS = 40L

/**
 * The `rise` entrance: fade in from 14 dp below at 97% scale, staggered by position.
 *
 * `styles.css` gives `.stagger > *` one animation and lets CSS delay each child; Compose
 * has no equivalent, so the index comes in as a parameter and becomes a delay.
 *
 * Keyed on [index] rather than run once per composition: a list item that scrolls out and
 * back is a new composition of the same index, and re-running the entrance every time
 * would make a scrolled list flicker. Keying on the index means the animation replays only
 * when an item's position actually changes.
 *
 * `graphicsLayer` keeps the offset and scale off the layout pass, so an entering card
 * cannot remeasure the grid around it.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember(index) { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val riseFrom = with(LocalDensity.current) { 14.dp.toPx() }

    LaunchedEffect(index) {
        delay(index * STAGGER_STEP_MS)
        progress.animateTo(1f, animationSpec = spec)
    }

    Box(
        modifier = modifier.graphicsLayer {
            val t = progress.value
            alpha = t
            translationY = riseFrom * (1f - t)
            val s = 0.97f + 0.03f * t
            scaleX = s
            scaleY = s
        }
    ) {
        content()
    }
}
```

- [ ] **Step 4: Verify everything**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on both, with the full instrumented suite green.

- [ ] **Step 5: File Issue E**

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Shared expressive components",
  "body": """Every redesigned screen needs the same handful of pieces: the press morph, the
wavy progress strip, the hero header, the segmented section nav, category chips, status
pills, the cookie thumbnail, the staggered entrance, and the 440 dp frame. Written per
screen they would drift — a 32 dp corner here and a 24 dp one there, a hand-written spring
in one file and Material's in another — so they land once, in `ui/components/`, before any
screen is touched.

Two of the design guide's component choices cannot be built as written. Verified with
`javap` against the resolved material3 1.4.0 AAR: `LinearWavyProgressIndicator`,
`ButtonGroup`, `LoadingIndicator` and `SplitButton` ship token classes only, with no
public composable.

- Wavy progress becomes a custom `Canvas` on an `infiniteTransition`. DESIGN-GUIDE §3.3
  sanctions the fallback explicitly.
- The connected button group becomes `SingleChoiceSegmentedButtonRow` + `SegmentedButton`,
  which is what §5.4 already maps it to.

Custom animations read their springs from `MaterialTheme.motionScheme`, which *is* public,
so a custom press morph and a Material component pressed next to it move with the same
physics rather than two hand-tuned approximations of it.

Plan: `docs/superpowers/plans/2026-08-24-expressive-theme-foundation.md`"""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Note the number; call it `E`.

- [ ] **Step 6: Commit Issue E**

```bash
git add app/src/main/java/com/onemind/app/ui/components/ \
        app/src/androidTest/java/com/onemind/app/SharedComponentsTest.kt

git commit -F - <<'MSG'
feat(ui): shared expressive components (#E)

Ten pieces every redesigned screen needs, landed once before any screen is
touched. Written per screen they would drift, and the drift is the kind nobody
files: a 32 dp corner here against a 24 dp one there, a hand-tuned spring in
one file against Material's in the next.

Two of the design guide's choices do not exist to be used.
LinearWavyProgressIndicator, ButtonGroup, LoadingIndicator and SplitButton
ship as token classes with no public composable in material3 1.4.0, checked
against the resolved AAR rather than assumed. So wavy progress is a Canvas on
an infiniteTransition, which §3.3 sanctions, and the connected group is
SingleChoiceSegmentedButtonRow, which §5.4 already mapped it to.

Springs come from MaterialTheme.motionScheme, which is public, so a custom
press morph and a Material component pressed beside it share physics instead
of approximating each other.

PressMorph returns a state rather than being a Modifier, because the corner
radius has to reach a Card's shape parameter and a Modifier cannot hand a
value back. StaggeredEntrance keys on its index so a list item scrolling back
into view does not replay its entrance. SectionNav carries labels and no
routes: mapping a destination to a route is navigation policy, and keeping it
out also means this compiles before NavRoutes has heard of Timeline.

Tested where a test can say something a glance cannot: every ProcessingState
gets its own label, the frame really caps at 440 dp when handed 800, the hero
renders both its lines, and the nav reports what was tapped. Corner radii and
gradients are verified against the reference HTML, because a test asserting
32 dp against a constant that says 32 dp asserts nothing.
MSG
```

- [ ] **Step 7: Close Issue E**

```
Done. `ui/components/` holds PressMorph, WavyProgress, StateChip, CategoryChip,
StatusPill, HeroHeader, SectionNav, CookieThumb, StaggeredEntrance and PhoneFrame.

Both unavailable APIs were replaced as the guide sanctions: wavy progress is a `Canvas` on
an `infiniteTransition`, the connected group is `SingleChoiceSegmentedButtonRow`. Custom
springs read from `MaterialTheme.motionScheme` so they match Material's own.

Tested for behaviour, not looks — each `ProcessingState` gets a distinct label, the frame
caps at 440 dp when given 800 (and is checked not to have collapsed instead, which the
bound alone would allow), the hero renders both lines, and the nav reports the tapped
destination. Visual fidelity is a comparison against the reference HTML; a test asserting
32 dp against the constant that says 32 dp would assert nothing.

`CategoryChip` uses `primaryContainer` per §5.4 and supersedes the interim private chip the
events work added to `EventsScreen.kt`; the Events restyle removes that one.

*Implemented by an AI agent (Claude), reviewed against the design reference.*
```

---

## Done when

- [ ] Issues D and E filed, implemented, closed with AI-attributed comments.
- [ ] Two commits on `my-extra-work`, `(#N)` in each subject, no `Co-Authored-By` and no
      generated-by trailer.
- [ ] `assembleDebug`, `testDebugUnitTest`, and the full instrumented suite green on the
      `onemind_test` AVD.
- [ ] Installed on device and looked at: warm dark everywhere, ember accent, Outfit
      titles, Figtree body. A font that failed to download falls back to Roboto without
      failing a build, so this check is not optional.
- [ ] No file under `domain/`, `data/` or `capture/` modified — `git diff --stat` on those
      three directories is empty.
- [ ] Nothing released.

## Deliberately not done here

- **No screen is redesigned.** Existing screens pick up the palette and typography and
  otherwise keep their current layout. Rewiring them is the next two plans.
- **`NavRoutes` is unchanged.** `SectionDestination` carries labels only; routes arrive
  with the navigation work.
- **The light scheme is derived, not designed.** The reference is dark-only. It exists so
  the `darkTheme` parameter is honest, and should not be treated as a second palette.
- **The halo is circular.** CSS asks for a 120%×90% ellipse; `RadialGradientShader`
  exposes no local matrix. Not visible on a wash that fades out by 70%, and recorded here
  so nobody spends an afternoon rediscovering why.
