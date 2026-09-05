package com.onemind.app.ui.feed

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.Memory
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.PhoneFrameDefaults
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.FabShadowColor

/**
 * The browse-first home.
 *
 * A two-column bento grid: one large card for the newest Memory with an image, then medium
 * and small alternating. Sizing is derived from position by [BentoSizing] rather than read
 * off a field, because `Memory` has no size and should not gain one.
 *
 * The search bar is a pill that navigates; search is its own destination. Events is reached
 * through the segmented group, not a top-bar icon — one affordance per action.
 */
@Composable
fun FeedScreen(
    onNavigateToComposer: () -> Unit,
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        PhoneFrame {
            HeroHeader(
                eyebrow = eyebrow(uiState.memories.size),
                title = "Everything you kept",
                trailing = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                }
            )

            SearchPill(
                onClick = onNavigateToSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )

            SectionNav(
                selected = SectionDestination.FEED,
                onSelect = onNavigateToSection,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            SourceFilterRow(
                options = uiState.availableSources,
                selectedFilter = uiState.sourceFilter,
                onFilterSelected = { viewModel.setSourceFilter(it) }
            )

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                else -> {
                    val filtered = remember(uiState.memories, uiState.sourceFilter) {
                        filterMemories(uiState.memories, uiState.sourceFilter)
                    }
                    if (filtered.isEmpty()) {
                        EmptyState(
                            message = if (uiState.sourceFilter != null &&
                                uiState.memories.isNotEmpty()
                            ) {
                                "Nothing captured here yet."
                            } else {
                                "No memories yet — tap + to save your first."
                            }
                        )
                    } else {
                        BentoGrid(
                            memories = filtered,
                            onMemoryClick = { onNavigateToMemory(it.id) },
                            onMemoryLongClick = { viewModel.requestDelete(it) },
                            onRetryProcessing = { viewModel.retryProcessing(it) }
                        )
                    }
                }
            }
        }

        CaptureFab(
            onClick = onNavigateToComposer,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    uiState.memoryToDelete?.let {
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() }
        )
    }
}

/** "10 memories · on device" — the count, and where they are. */
private fun eyebrow(count: Int): String {
    val noun = if (count == 1) "memory" else "memories"
    return "$count $noun · on device"
}

/**
 * The one element in the whole design that casts a shadow.
 *
 * `.fab-dock` in the reference is `max-width: 440px; margin-inline: auto`, so the FAB is
 * pinned to the *frame's* bottom-right rather than the window's. The plan aligned it to the
 * full-screen `Box`, which is identical on a phone and wrong on anything wider — the button
 * would drift away from the content it belongs to. Hence the same 440 dp cap as
 * [PhoneFrame], applied here so the dock and the content agree.
 *
 * Shape morphs 20 dp → 32 dp on press with the shared scale, which is `.fab`'s own
 * transition: `border-radius 1.25rem` at rest, `2rem` and `scale(0.95)` while active. The
 * plan pinned it at `shapes.extraLarge` — the *pressed* radius, statically — so the morph
 * the reference spends a transition on would not have happened at all. The shared
 * `rememberPressMorph` scales to 0.96 rather than 0.95; that is one hundredth of a
 * difference against having a second, nearly-identical press spring in the codebase.
 *
 * **64 dp, not `LargeFloatingActionButton`.** DESIGN-GUIDE §5.4 maps this to
 * `LargeFloatingActionButton`, and the plan repeats it, but that component is 96 dp while
 * `.fab` is `width: 64px; height: 64px`. Built at 96 dp it covered two cards and read as the
 * loudest thing on the screen rather than one affordance among several — visible the moment
 * it was rendered beside `index.html`. The guide names a component; the stylesheet gives the
 * size, and where they disagree about something this visible the stylesheet is what a user
 * would compare against. 64 dp also sits between Material's own 56 and 80, so no stock size
 * matches and one had to be chosen either way.
 */
@Composable
private fun CaptureFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(
        interactionSource = interaction,
        restCorner = 20.dp,
        pressedCorner = 32.dp
    )

    Box(
        modifier = modifier
            .widthIn(max = PhoneFrameDefaults.MaxWidth)
            .fillMaxWidth()
            .padding(end = 24.dp, bottom = 32.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        FloatingActionButton(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(morph.corner),
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
            modifier = Modifier
                .size(64.dp)
                .pressScale(morph)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(morph.corner),
                    ambientColor = FabShadowColor,
                    spotColor = FabShadowColor
                )
        ) {
            Icon(Icons.Default.Add, "Create memory")
        }
    }
}

@Composable
private fun BentoGrid(
    memories: List<Memory>,
    onMemoryClick: (Memory) -> Unit,
    onMemoryLongClick: (Memory) -> Unit,
    onRetryProcessing: (Memory) -> Unit
) {
    val sizes = remember(memories) { BentoSizing.sizes(memories) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = memories,
            key = { _, memory -> memory.id },
            // The large card carries a 160 dp banner and needs the full width; the rest
            // take one column each.
            span = { index, _ ->
                if (sizes[index] == BentoSize.LARGE) GridItemSpan(2) else GridItemSpan(1)
            }
        ) { index, memory ->
            StaggeredEntrance(index = index) {
                BentoCard(
                    memory = memory,
                    size = sizes[index],
                    onClick = { onMemoryClick(memory) },
                    onLongClick = { onMemoryLongClick(memory) },
                    onRetryProcessing = { onRetryProcessing(memory) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp)
        )
    }
}

/**
 * A search affordance that is not a text field.
 *
 * Looks like the bar it replaces and behaves like a button. Search is its own destination
 * now, and a field here would put a second copy of the query state on a screen that no
 * longer owns any.
 */
@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ask for anything you saved…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Shared with [TimelineScreen], which is why it is not private.
 *
 * Two destinations now offer delete on long-press, and two copies of a confirmation dialog
 * is how the two drift into saying different things about the same irreversible action.
 */
@Composable
internal fun DeleteConfirmationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete memory?") },
        text = { Text("This memory and its contents will be permanently removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Client-side filter over the already-loaded memories.
 *
 * Filtering happens client-side because the feed is already loaded into memory and is at
 * most a few thousand rows — querying the DB again for each filter change would thrash the
 * reactive Flow for no benefit.
 */
internal fun filterMemories(memories: List<Memory>, filter: SourceFilter?): List<Memory> {
    if (filter == null) return memories
    return memories.filter { memory ->
        memory.sourceType == filter.sourceType &&
            (filter.sourcePackage == null || memory.sourcePackage == filter.sourcePackage)
    }
}

