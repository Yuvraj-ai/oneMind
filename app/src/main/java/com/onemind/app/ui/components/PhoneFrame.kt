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
