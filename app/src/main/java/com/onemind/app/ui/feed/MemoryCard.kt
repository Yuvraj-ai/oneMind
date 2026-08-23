package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.processing.StageStatus
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * A card representing a single Memory in the feed.
 * Shows thumbnail (if image), text snippet, and timestamp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryCard(
    memory: Memory,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRetryProcessing: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                // Long-press was the only route to delete and carried no label, so a
                // screen-reader user had no way to discover or reach it. TalkBack
                // surfaces this as an explicit action.
                onLongClickLabel = "Delete memory",
                onClickLabel = "Open memory"
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Thumbnail (if image content exists)
            val thumbnailBlock = memory.contentBlocks.firstOrNull {
                it.type == ContentType.IMAGE && it.thumbnailPath != null
            }
            if (thumbnailBlock != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(thumbnailBlock.thumbnailPath!!))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Memory image",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Text content + timestamp
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Text snippet
                val textSnippet = getTextSnippet(memory)
                if (textSnippet.isNotEmpty()) {
                    Text(
                        text = textSnippet,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Category chips, between the snippet and the timestamp: they
                // qualify what the Memory is, so they read with the content
                // rather than with its metadata.
                CategoryChips(categories = memory.derived.categories)

                // Timestamp
                Text(
                    text = formatTimestamp(memory.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Source app / capture method
                SourceRow(memory = memory)

                ProcessingStatusRow(
                    state = memory.processingState,
                    onRetry = onRetryProcessing
                )
            }
        }
    }
}

/**
 * Category chips.
 *
 * Shared by the feed card and the detail view so both render the vocabulary
 * identically — a category that reads one way in a list and another way on a
 * detail screen is not recognisably the same category.
 *
 * Wraps rather than scrolls horizontally: a card is not a place to hide content
 * behind a gesture the user has no reason to suspect is available.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    categories: List<Category>,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEach { category ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Shows enrichment status only while it is worth showing: a quiet spinner during
 * processing, a retry affordance on failure, and nothing at all once the Memory
 * is READY.
 */
@Composable
private fun ProcessingStatusRow(
    state: ProcessingState,
    onRetry: () -> Unit
) {
    when (state) {
        ProcessingState.PROCESSING -> {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Enriching",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ProcessingState.FAILED -> {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Enrichment failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(24.dp)
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // DRAFT, SAVED, EDITED and READY need no indicator: the Memory is
        // already usable and the work is either done or not worth announcing.
        else -> Unit
    }
}

/**
 * Extract a text snippet from the memory's content blocks.
 *
 * Hierarchy: title → summary → raw content opening. The title says what the Memory
 * *is called*; the summary says what it is *about*; the raw content is the last
 * resort when enrichment hasn't run yet or no provider is configured.
 */
private fun getTextSnippet(memory: Memory): String {
    val summary = memory.derived.summary
    if (summary?.status == StageStatus.SUCCESS) {
        // Title + summary together, if both exist.
        val title = summary.title
        if (title != null && summary.summaryText.isNotBlank()) {
            return "$title — ${summary.summaryText}"
        }
        if (title != null) return title
        if (summary.summaryText.isNotBlank()) return summary.summaryText
    }

    val textBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.TEXT }
    if (textBlock != null) return textBlock.content

    val urlBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.URL }
    if (urlBlock != null) return urlBlock.content

    val imageBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.IMAGE }
    if (imageBlock != null) return "Image"

    return ""
}

/**
 * A Memory's timestamp, in the device's locale and timezone.
 *
 * Shared with [SearchResultCard] so a Memory's date reads identically wherever it
 * appears — a result and a feed entry are the same object.
 */
internal fun formatTimestamp(instant: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
