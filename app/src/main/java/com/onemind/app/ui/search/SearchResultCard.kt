package com.onemind.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.domain.search.SearchResult
import com.onemind.app.domain.search.SnippetExtractor
import com.onemind.app.ui.components.CategoryChip
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.feed.SourceRow
import com.onemind.app.ui.feed.formatTimestamp
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking
import java.io.File

/**
 * An M3 Expressive search result card.
 *
 * Features:
 * - 16dp rounded corners with spring press feedback via [rememberPressMorph] and [pressScale].
 * - Warm surfaceContainer background with subtle outlineVariant border and elevation.
 * - Highlighted matched query terms in primary with bold weight.
 * - Thumbnail rendered with 12dp rounded corners.
 * - Expressive category chips ([CategoryChip]) and semantic match score pill chip.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchResultCard(
    result: SearchResult,
    queryTerms: List<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memory = result.memory

    // Recomputed only when inputs change: snippet extraction scans the document.
    val snippet = remember(result.matchedText, queryTerms) {
        result.matchedText?.let { SnippetExtractor.extract(it, queryTerms) }
    }

    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(
        interactionSource = interaction,
        restCorner = 16.dp,
        pressedCorner = 24.dp
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
                onClickLabel = "Open memory",
                onLongClickLabel = "Delete memory"
            ),
        shape = RoundedCornerShape(morph.corner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            val thumbnailBlock = memory.contentBlocks.firstOrNull {
                it.type == ContentType.IMAGE && (it.thumbnailPath != null || it.content.isNotBlank())
            }
            val imagePath = thumbnailBlock?.thumbnailPath ?: thumbnailBlock?.content
            if (imagePath != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(imagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Memory image",
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResultText(result = result, snippet = snippet, queryTerms = queryTerms)

                if (memory.derived.categories.isNotEmpty() || result.semanticScore > 0.0) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        memory.derived.categories.forEach { category ->
                            CategoryChip(name = category.name)
                        }

                        if (result.semanticScore > 0.0) {
                            SemanticMatchChip(
                                score = result.semanticScore,
                                isSemanticOnly = result.isSemanticOnly
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
}

/**
 * Semantic match score pill chip showing vector search similarity.
 */
@Composable
private fun SemanticMatchChip(
    score: Double,
    isSemanticOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val pct = (score * 100).toInt().coerceIn(1, 100)
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = if (isSemanticOnly) "$pct% semantic" else "$pct% match",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                letterSpacing = Tracking.Chip,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * The text of a result: a highlighted snippet where one exists, or the summary / fallback text
 * with matched query terms highlighted.
 */
@Composable
private fun ResultText(
    result: SearchResult,
    snippet: SnippetExtractor.Snippet?,
    queryTerms: List<String>
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

    val rawText = summary ?: fallbackText(result)
    val styledText = if (queryTerms.isNotEmpty()) {
        highlightTerms(rawText, queryTerms, MaterialTheme.colorScheme.primary)
    } else {
        AnnotatedString(rawText)
    }

    Text(
        text = styledText,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Emphasise matched snippet spans in primary color and bold font weight.
 */
@Composable
private fun highlighted(snippet: SnippetExtractor.Snippet): AnnotatedString {
    val emphasis = MaterialTheme.colorScheme.primary

    return buildAnnotatedString {
        var cursor = 0
        snippet.highlights.forEach { range ->
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
 * Emphasise matched query terms within raw text in primary color and bold font weight.
 */
private fun highlightTerms(
    text: String,
    queryTerms: List<String>,
    emphasisColor: Color
): AnnotatedString {
    val nonBlankTerms = queryTerms.filter { it.isNotBlank() }
    if (nonBlankTerms.isEmpty() || text.isEmpty()) {
        return AnnotatedString(text)
    }

    val matches = mutableListOf<IntRange>()
    for (term in nonBlankTerms) {
        var start = 0
        while (start < text.length) {
            val index = text.indexOf(term, start, ignoreCase = true)
            if (index < 0) break
            matches.add(index until (index + term.length))
            start = index + term.length
        }
    }

    if (matches.isEmpty()) {
        return AnnotatedString(text)
    }

    val sorted = matches.sortedBy { it.first }
    val merged = mutableListOf<IntRange>()
    for (range in sorted) {
        if (merged.isEmpty()) {
            merged.add(range)
        } else {
            val prev = merged.last()
            if (range.first <= prev.last + 1) {
                merged[merged.lastIndex] = prev.first..maxOf(prev.last, range.last)
            } else {
                merged.add(range)
            }
        }
    }

    return buildAnnotatedString {
        var cursor = 0
        merged.forEach { range ->
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(start, text.length)

            if (start > cursor) append(text.substring(cursor, start))
            withStyle(SpanStyle(color = emphasisColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(start, end))
            }
            cursor = end
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

/**
 * Fallback description when a Memory has neither a matching snippet nor a summary.
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
