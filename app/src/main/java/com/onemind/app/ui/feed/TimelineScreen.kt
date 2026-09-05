package com.onemind.app.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * How far down the list the entrance stagger keeps counting.
 *
 * At 40 ms a step, an uncapped index would make the fiftieth card wait two seconds to
 * appear after the user scrolled to it. The stagger is there to make the first screenful
 * arrive with rhythm; past that, immediate is correct.
 */
private const val STAGGER_CAP = 7

/**
 * The same Memories the feed shows, in date sections on a rail.
 *
 * Reuses [FeedViewModel] rather than getting its own. It needs precisely the feed's
 * stream and nothing else, and a `TimelineViewModel` would be a second subscription to the
 * same Room Flow plus a second copy of the delete and retry plumbing. `hiltViewModel()`
 * scopes per navigation entry, so this destination gets its own instance — one more
 * collector on a Flow the feed already collects.
 *
 * Every card is `MEDIUM`, as the reference specifies: the bento rhythm is the feed's
 * signature, and a chronological list wants a steady beat instead.
 */
@Composable
fun TimelineScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhoneFrame {
        HeroHeader(eyebrow = "Chronological", title = "Back in time")

        SectionNav(
            selected = SectionDestination.TIMELINE,
            onSelect = onNavigateToSection,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
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
                // groups.
                val stagger = remember(groups) {
                    groups.asSequence()
                        .flatMap { (_, memories) -> memories.asSequence() }
                        .withIndex()
                        .associate { (index, memory) -> memory.id to minOf(index, STAGGER_CAP) }
                }
                val railColor = MaterialTheme.colorScheme.outlineVariant
                val ringColor = MaterialTheme.colorScheme.background
                val dotColor = MaterialTheme.colorScheme.primary

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)
                ) {
                    groups.forEach { (group, groupMemories) ->
                        item(key = "header-${group.name}") {
                            SectionHeader(label = group.label)
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

    uiState.memoryToDelete?.let {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() }
        )
    }
}

/** `section-tag` + `section-rule`: an uppercase pill and a hairline across the rest. */
@Composable
private fun SectionHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(shape = PillShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = Tracking.Chip,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

/**
 * One item hung off the rail: a 2 dp vertical line with a dot beside each card.
 *
 * The dot carries a background-coloured ring so the rail appears to pass behind it rather
 * than through it — `box-shadow: 0 0 0 4px var(--background)` in the reference. That is the
 * whole visual trick, and is why the ring is drawn rather than the line being broken.
 *
 * The line is a `drawBehind` on the row rather than a `fillMaxHeight` child. In a
 * `LazyColumn` an item's height constraint is unbounded, so a `fillMaxHeight` child
 * measures to nothing and the rail would silently not draw at all; `drawBehind` runs after
 * measurement and knows the height. It also draws before the children, which is exactly
 * what puts it behind the opaque ring.
 *
 * `.rail` in the reference is one `border-left` per *section* rather than per item. Drawn
 * per item the lines abut, because each row's height includes the 12 dp gap below its card,
 * so the result is the same continuous line.
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
                    // 24 dp puts the 12 dp dot's top at 28 dp, which is `top: 28px`.
                    .padding(top = 24.dp)
                    .size(20.dp)
                    .clip(PillShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(PillShape)
                        .background(dotColor)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) { content() }
    }
}
