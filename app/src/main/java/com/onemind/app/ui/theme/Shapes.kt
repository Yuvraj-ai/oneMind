package com.onemind.app.ui.theme

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp

/*
 * M3 Expressive Shapes & Corner Scales.
 *
 * Semantic corner radii:
 * - extraSmall: 4dp (Status dots, indicators)
 * - small: 8dp (Tags, subtle chips)
 * - medium: 16dp (Standard memory cards, text input fields)
 * - large: 24dp (Prominent containers, hero media frames)
 * - extraLarge: 28dp (Dialogs, bottom sheets, search pill)
 * - full: 100dp (Floating toolbar, FAB, pill chips)
 */

val ShapeExtraSmall = RoundedCornerShape(4.dp)
val ShapeSmall = RoundedCornerShape(8.dp)
val ShapeMedium = RoundedCornerShape(16.dp)
val ShapeLarge = RoundedCornerShape(24.dp)
val ShapeExtraLarge = RoundedCornerShape(28.dp)
val ShapeFull = RoundedCornerShape(100.dp)

val OneMindShapes = Shapes(
    extraSmall = ShapeExtraSmall,
    small = ShapeSmall,
    medium = ShapeMedium,
    large = ShapeLarge,
    extraLarge = ShapeExtraLarge
)

/**
 * M3 Expressive Asymmetric Card Shape for top-emphasis visual memories.
 * RoundedCornerShape(topStart = 28.dp, topEnd = 16.dp, bottomEnd = 28.dp, bottomStart = 16.dp)
 */
val AsymmetricCardShape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 16.dp,
    bottomEnd = 28.dp,
    bottomStart = 16.dp
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
