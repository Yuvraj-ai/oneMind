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
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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

                // Timestamp
                Text(
                    text = formatTimestamp(memory.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Extract a text snippet from the memory's content blocks.
 * Prioritizes TEXT blocks, falls back to URL content.
 */
private fun getTextSnippet(memory: Memory): String {
    val textBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.TEXT }
    if (textBlock != null) return textBlock.content

    val urlBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.URL }
    if (urlBlock != null) return urlBlock.content

    val imageBlock = memory.contentBlocks.firstOrNull { it.type == ContentType.IMAGE }
    if (imageBlock != null) return "Image"

    return ""
}

private fun formatTimestamp(instant: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
