package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.search.SearchResult
import com.onemind.app.domain.search.SnippetExtractor
import java.io.File

/**
 * A search result.
 *
 * Differs from [MemoryCard] in one respect that matters: it shows *why* this Memory
 * matched. A card showing the opening words of a Memory leaves the user opening
 * every result to find out which one was right, which is the work the search was
 * supposed to save.
 *
 * Everything else — thumbnail, source, timestamp, category chips — is the same
 * components the feed uses, because a result and a feed entry are the same object
 * and should not look like two different things.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchResultCard(
    result: SearchResult,
    queryTerms: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memory = result.memory

    // Recomputed only when the inputs change, not on every recomposition: snippet
    // extraction scans the whole document.
    val snippet = remember(result.matchedText, queryTerms) {
        result.matchedText?.let { SnippetExtractor.extract(it, queryTerms) }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
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

            Column(modifier = Modifier.weight(1f)) {
                ResultText(result = result, snippet = snippet)

                Spacer(modifier = Modifier.height(4.dp))

                CategoryChips(categories = memory.derived.categories)

                Text(
                    text = formatTimestamp(memory.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SourceRow(memory = memory)
            }
        }
    }
}

/**
 * The text of a result: a highlighted snippet where one exists, the summary
 * otherwise.
 *
 * The fallback is the case where a Memory matched on *meaning* with no literal term
 * present. There is nothing to highlight, and inventing an emphasis would imply the
 * words matched when they did not.
 */
@Composable
private fun ResultText(
    result: SearchResult,
    snippet: SnippetExtractor.Snippet?
) {
    if (snippet != null && snippet.highlights.isNotEmpty()) {
        Text(
            text = highlighted(snippet),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        return
    }

    val summary = result.memory.derived.summary
        ?.takeIf { it.status == StageStatus.SUCCESS && it.summaryText.isNotBlank() }
        ?.summaryText

    Text(
        text = summary ?: fallbackText(result),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Emphasise the matched spans.
 *
 * Bold **and** colour, not colour alone. A user with a colour-vision deficiency, or
 * one looking at the screen in sunlight, would otherwise get no signal at all from
 * a highlight — and the highlight is the only thing on the card explaining why the
 * result is there.
 */
@Composable
private fun highlighted(snippet: SnippetExtractor.Snippet): AnnotatedString {
    val emphasis = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        var cursor = 0
        snippet.highlights.forEach { range ->
            // Defensive: a range past the end would throw inside substring, and the
            // text on screen is not worth crashing over.
            val start = range.first.coerceIn(0, snippet.text.length)
            val end = (range.last + 1).coerceIn(start, snippet.text.length)

            if (start > cursor) append(snippet.text.substring(cursor, start))
            withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.Bold)) {
                append(snippet.text.substring(start, end))
            }
            cursor = end
        }
        if (cursor < snippet.text.length) append(snippet.text.substring(cursor))
    }
}

/**
 * Last resort when a Memory has neither a matching snippet nor a summary.
 *
 * Says what the Memory *is* rather than showing nothing, so the card is never blank.
 */
private fun fallbackText(result: SearchResult): String {
    val blocks = result.memory.contentBlocks
    blocks.firstOrNull { it.type == ContentType.TEXT }?.let { return it.content }
    blocks.firstOrNull { it.type == ContentType.URL }?.let { return it.content }
    val imageCount = blocks.count { it.type == ContentType.IMAGE }
    return when {
        imageCount > 1 -> "$imageCount images"
        imageCount == 1 -> "Image"
        else -> "Memory"
    }
}
