package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.components.CategoryChip
import com.onemind.app.ui.components.CookieThumb
import com.onemind.app.ui.components.StateChip
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.AsymmetricCardShape
import com.onemind.app.ui.theme.EmberGradient
import com.onemind.app.ui.theme.PillShape
import java.io.File

/** Category chips a card of each size has room for — `max 2 except lg = 5`, per §4. */
private fun chipBudget(size: BentoSize) = if (size == BentoSize.LARGE) 5 else 2

private fun restShape(size: BentoSize): Shape = when (size) {
    BentoSize.LARGE -> AsymmetricCardShape
    BentoSize.MEDIUM -> RoundedCornerShape(16.dp)
    BentoSize.SMALL -> RoundedCornerShape(12.dp)
}

/**
 * Container colour per size: surfaceContainerHigh for primary large card,
 * surfaceContainer for medium and small cards.
 */
@Composable
private fun containerColor(size: BentoSize): Color = when (size) {
    BentoSize.LARGE -> MaterialTheme.colorScheme.surfaceContainerHigh
    BentoSize.MEDIUM -> MaterialTheme.colorScheme.surfaceContainer
    BentoSize.SMALL -> MaterialTheme.colorScheme.surfaceContainer
}

/**
 * One Memory as a bento tile.
 *
 * Not a `Card`: the container is a `Surface` with an explicit shape, because the shape has
 * to change between the size's asymmetric silhouette at rest and a uniform morphing corner
 * while pressed, and `CardDefaults` elevation would add a shadow the design does not use —
 * DESIGN-GUIDE §2 is explicit that cards read by tonal step, not by shadow.
 *
 * The morph is uniform on purpose. Each size's rest shape has exactly one corner that
 * disagrees with the others, and animating four corners independently would dissolve that
 * notch into a generic rounded square on the way down — losing the signature at the one
 * moment the user is looking straight at it.
 *
 * The title is `titleMedium` with only its size overridden, not `titleSmall`/`titleLarge`
 * as the plan had it. `.mem-summary` in `styles.css` is Outfit 600 at `-0.02em`, 24 px on
 * `lg` and 17 px elsewhere, and `titleMedium` is the one slot carrying that face and that
 * tracking. `titleSmall` is Figtree by DESIGN-GUIDE §5.2's own mapping, so it would have
 * set every card title in the body face and dropped the tracking §5.2 calls "the
 * expressive part".
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun BentoCard(
    memory: Memory,
    size: BentoSize,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetryProcessing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(
        interactionSource = interaction,
        restCorner = when (size) {
            BentoSize.LARGE -> 28.dp
            BentoSize.MEDIUM -> 16.dp
            BentoSize.SMALL -> 12.dp
        }
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(morph)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                // Long-press was the only route to delete and carried no label, so a
                // screen-reader user could neither discover nor reach it. Kept.
                onClickLabel = "Open memory",
                onLongClickLabel = "Delete memory"
            ),
        shape = restShape(size),
        color = containerColor(size)
    ) {
        Column {
            if (size == BentoSize.LARGE) {
                val imageBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.IMAGE }
                val imagePath = imageBlock?.thumbnailPath ?: imageBlock?.content

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(EmberGradient)
                ) {
                    if (imagePath != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(File(imagePath))
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Subtle gradient scrim over image backdrop
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f)
                                    )
                                )
                            )
                    )

                    // Relative timestamp pill badge in surfaceContainerHighest
                    Surface(
                        shape = PillShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = formatRelativeTimestamp(memory.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = MemoryDisplay.title(memory),
                        style = if (size == BentoSize.LARGE) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Medium and small cards carry the blob instead of a banner. Large
                    // already has one, so a second image mark would just be noise.
                    if (size != BentoSize.LARGE && memory.imageBlocks().isNotEmpty()) {
                        CookieThumb()
                    }
                }

                CategoryRow(memory = memory, budget = chipBudget(size))
                CardFooter(
                    memory = memory,
                    showTimestamp = size != BentoSize.LARGE,
                    onRetryProcessing = onRetryProcessing
                )
            }
        }
    }
}

/**
 * Up to [budget] chips, then `+N` for the rest.
 *
 * `FlowRow`, because `.mem-cats` is `flex-wrap: wrap` and five chips on a large card do not
 * fit one line — a plain `Row` would clip them off the edge without saying so. The `+N`
 * is `.cat-more` in the reference: dropping it would lose the fact that a Memory has more
 * categories than the card shows, which is information rather than decoration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryRow(memory: Memory, budget: Int) {
    val all = memory.derived.categories
    if (all.isEmpty()) return
    val shown = all.take(budget)
    val hidden = all.size - shown.size

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        shown.forEach { CategoryChip(name = it.name) }
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * Source, date, a link mark, and the state chip when the state is worth saying.
 *
 * `READY` prints nothing: it is the state almost every card is in, and a chip on every
 * card would say nothing while costing a row of height on all of them.
 *
 * The date is here because `.mem-meta` in the reference is `source icon + app label · date
 * · Link2 · StateChip`, and the plan's footer had no date at all. On a Memory the user
 * typed themselves, `resolveSource` returns null by design — the user knows they typed it —
 * so without the date that footer rendered completely empty, and every manual card lost the
 * timestamp the old list card showed. The middot only appears when there is a source to
 * separate from, and is hidden from semantics the way the reference marks it `aria-hidden`.
 *
 * `FlowRow` for the same reason as the chips — `.mem-meta` wraps, with a wider column gap
 * than row gap, and a small card with a long app name plus a state chip does not fit one
 * line.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFooter(
    memory: Memory,
    showTimestamp: Boolean = true,
    onRetryProcessing: () -> Unit
) {
    val source = resolveSource(memory)

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (source != null) {
            SourceRow(memory = memory, modifier = Modifier.align(Alignment.CenterVertically))
            if (showTimestamp) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .clearAndSetSemantics { }
                )
            }
        }

        if (showTimestamp) {
            Text(
                text = formatRelativeTimestamp(memory.createdAt),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        if (memory.derived.urls.isNotEmpty()) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = "Has links",
                modifier = Modifier
                    .height(14.dp)
                    .align(Alignment.CenterVertically),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (memory.processingState != ProcessingState.READY) {
            StateChip(state = memory.processingState)
        }
    }

    if (memory.processingState == ProcessingState.FAILED) {
        Surface(
            onClick = onRetryProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = "Retry processing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
            )
        }
    }
}
