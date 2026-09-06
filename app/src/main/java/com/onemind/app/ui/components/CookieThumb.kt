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
