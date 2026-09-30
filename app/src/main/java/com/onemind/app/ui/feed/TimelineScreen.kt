package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.ui.components.FloatingActionDock
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance

/**
 * The same Memories the feed shows, in date sections on a continuous vertical spine rail.
 *
 * Reuses [FeedViewModel] rather than getting its own. It needs precisely the feed's
 * stream and nothing else, and a `TimelineViewModel` would be a second subscription to the
 * same Room Flow plus a second copy of the delete and retry plumbing. `hiltViewModel()`
 * scopes per navigation entry, so this destination gets its own instance — one more
 * collector on a Flow the feed already collects.
 *
 * Every card is `MEDIUM` (16dp rounded corners, `surfaceContainer`), as the reference specifies:
 * the bento rhythm is the feed's signature, and a chronological list wants a steady beat instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    onNavigateToComposer: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        PhoneFrame {
            HeroHeader(eyebrow = "Chronological", title = "Back in time")

            SectionNav(
                selected = SectionDestination.TIMELINE,
                onSelect = onNavigateToSection,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                uiState.memories.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nothing captured here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val groups = remember(uiState.memories) { DateGrouping.group(uiState.memories) }

                    // Precomputed and keyed by id, not counted with a `var` inside the item
                    // lambda. That lambda runs per composition rather than per item, so
                    // scrolling would climb the counter without bound and a card scrolled back
                    // into view would get a new index — which re-keys StaggeredEntrance and
                    // replays its entrance. The stagger has to run down the whole screen
                    // rather than restarting at each section, hence one flat numbering across
                    // groups. Capping the delay is StaggeredEntrance's own business.
                    val stagger = remember(groups) {
                        groups.asSequence()
                            .flatMap { (_, memories) -> memories.asSequence() }
                            .withIndex()
                            .associate { (index, memory) -> memory.id to index }
                    }
                    val railColor = MaterialTheme.colorScheme.outlineVariant
                    val ringColor = MaterialTheme.colorScheme.surface
                    val dotColor = MaterialTheme.colorScheme.primary

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp)
                    ) {
                        groups.forEach { (group, groupMemories) ->
                            stickyHeader(key = "header-${group.name}") {
                                SectionHeader(
                                    label = DateGrouping.formatHeader(group, groupMemories),
                                    railColor = railColor
                                )
                            }

                            items(groupMemories, key = { it.id }) { memory ->
                                RailRow(
                                    railColor = railColor,
                                    ringColor = ringColor,
                                    dotColor = dotColor
                                ) {
                                    StaggeredEntrance(index = stagger[memory.id] ?: 0) {
                                        BentoCard(
                                            memory = memory,
                                            size = BentoSize.MEDIUM,
                                            onClick = { onNavigateToMemory(memory.id) },
                                            onLongClick = { viewModel.requestDelete(memory) },
                                            onRetryProcessing = { viewModel.retryProcessing(memory) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        FloatingActionDock(
            onFabClick = onNavigateToComposer,
            onSearchClick = onNavigateToSearch,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    uiState.memoryToDelete?.let {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() }
        )
    }
}

/** Sticky anchoring date badge styled with `surfaceContainerHighest` and 12dp rounded corners. */
@Composable
private fun SectionHeader(
    label: String,
    railColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val x = 10.dp.toPx()
                drawLine(
                    color = railColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .padding(top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(railColor)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = railColor
        )
    }
}

/**
 * One item hung off the rail: a 2 dp continuous vertical line with a dot connector beside each card.
 *
 * The dot carries a surface-coloured ring so the rail appears to pass behind it rather
 * than through it. The line is drawn via `drawBehind` to ensure it draws continuously across items.
 */
@Composable
private fun RailRow(
    railColor: Color,
    ringColor: Color,
    dotColor: Color,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val x = 10.dp.toPx()
                drawLine(
                    color = railColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
    ) {
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) { content() }
    }
}
