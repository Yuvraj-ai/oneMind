package com.onemind.app.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

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
 * A [ShaderBrush] rather than [Brush.radialGradient], because both of the numbers that
 * matter depend on the size being drawn into, which a top-level `val` cannot know:
 * `at 50% 0%` hangs the centre off the *top edge* rather than the middle of the box, and
 * the `120%` radius is relative to the drawn width. `createShader` is handed the size at
 * draw time, so both resolve correctly at any width.
 *
 * One deliberate deviation: CSS specifies an *ellipse* 120% of the width by 90% of the
 * height, and Compose's radial shader is circular only. Making it elliptical needs a
 * shader local matrix that [RadialGradientShader] does not expose. On a soft wash that
 * fades to nothing by 70% the difference is not visible, so the radius takes the width
 * term and the height term is dropped.
 *
 * The outer stop is the background at zero alpha rather than the inner colour at zero
 * alpha, matching the CSS: fading toward the page colour and fading toward transparent
 * are the same result only when the interpolation is premultiplied, and this way it does
 * not depend on that.
 */
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

/**
 * `--shadow-fab: 0 14px 34px -10px primary/0.45`.
 *
 * Passed to `Modifier.shadow(ambientColor =, spotColor =)`, which honours colour from
 * API 28 — always, at this app's minSdk of 30. Cards deliberately get none of this:
 * DESIGN-GUIDE §2 is explicit that cards rely on tonal elevation, and the FAB is the
 * only element that pops.
 */
val FabShadowColor: Color = OneMindPrimary.copy(alpha = 0.45f)
