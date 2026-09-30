package com.onemind.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.components.CategoryChip
import com.onemind.app.ui.components.PressMorphState
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.EmberGradient
import java.io.File

/**
 * Material 3 Expressive Feed Memory Card.
 *
 * All memories are displayed in a long rectangular shape with big rounded corners (28dp).
 * - Memories with images: Entire card covered full-bleed by the image, with a protective
 *   gradient scrim and top-left foreground date + bold title.
 * - Memories with no images: Clean surface container with top-left date + bold title,
 *   followed by the truncated summary below it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedMemoryCard(
    memory: Memory,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetryProcessing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(
        interactionSource = interaction,
        restCorner = 28.dp
    )
    val cardShape = RoundedCornerShape(28.dp)

    val imageBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.IMAGE }
    val imagePath = imageBlock?.thumbnailPath ?: imageBlock?.content

    if (imagePath != null) {
        ImageMemoryCard(
            memory = memory,
            imagePath = imagePath,
            cardShape = cardShape,
            interaction = interaction,
            morph = morph,
            onClick = onClick,
            onLongClick = onLongClick,
            onRetryProcessing = onRetryProcessing,
            modifier = modifier
        )
    } else {
        TextMemoryCard(
            memory = memory,
            cardShape = cardShape,
            interaction = interaction,
            morph = morph,
            onClick = onClick,
            onLongClick = onLongClick,
            onRetryProcessing = onRetryProcessing,
            modifier = modifier
        )
    }
}

/**
 * Image Card: Full-bleed photo covering the entire long rectangle card, with top-left
 * date and bold title over a protective gradient scrim.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageMemoryCard(
    memory: Memory,
    imagePath: String,
    cardShape: RoundedCornerShape,
    interaction: MutableInteractionSource,
    morph: PressMorphState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetryProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 290.dp)
            .aspectRatio(0.72f)
            .pressScale(morph)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Open memory",
                onLongClickLabel = "Delete memory"
            ),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EmberGradient)
        ) {
            val context = LocalContext.current
            val imageModel = remember(imagePath) {
                if (imagePath.startsWith("/")) File(imagePath) else imagePath
            }

            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageModel)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Protective top gradient scrim so white typography is crisp and legible on any photo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.TopStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.38f),
                                Color.Black.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Top-left foreground: Date of upload + bold memory title
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatCardDate(memory.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1
                )
                Text(
                    text = MemoryDisplay.title(memory),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Retry action if processing failed
            if (memory.processingState == ProcessingState.FAILED) {
                Surface(
                    onClick = onRetryProcessing,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Retry processing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Text Card: Long rectangle card in surfaceContainer with big rounded corners (28dp).
 * Top-left date of upload, bold title, and truncated summary below it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextMemoryCard(
    memory: Memory,
    cardShape: RoundedCornerShape,
    interaction: MutableInteractionSource,
    morph: PressMorphState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetryProcessing: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = remember(memory) { MemoryDisplay.title(memory) }
    val snippet = remember(memory, title) {
        val rawSnippet = MemoryDisplay.snippet(memory)
        if (rawSnippet.startsWith(title, ignoreCase = true)) {
            rawSnippet.removePrefix(title).trimStart('\n', '\r', ' ', '—', '-')
        } else {
            rawSnippet
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .pressScale(morph)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = "Open memory",
                onLongClickLabel = "Delete memory"
            ),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = formatCardDate(memory.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (snippet.isNotBlank()) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            if (memory.derived.categories.isNotEmpty()) {
                CardCategoryRow(memory = memory, budget = 2)
            }

            if (memory.processingState == ProcessingState.FAILED) {
                Surface(
                    onClick = onRetryProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = "Retry processing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Category chips for cards (budgeted to avoid overflow).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardCategoryRow(memory: Memory, budget: Int) {
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
