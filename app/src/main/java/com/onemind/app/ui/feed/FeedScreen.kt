package com.onemind.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.Memory
import com.onemind.app.ui.components.FloatingActionDock
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance

/**
 * The browse-first home.
 *
 * A two-column bento grid: one large card for the newest Memory with an image, then medium
 * and small alternating.
 * Sizing is derived from position by [BentoSizing] rather than read
 * off a field, because `Memory` has no size and should not gain one.
 *
 * Search is triggered from the floating dock at the bottom; Events is reached
 * through the segmented group.
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

            SectionNav(
                selected = SectionDestination.FEED,
                onSelect = onNavigateToSection,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)
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
                            },
                            onCaptureClick = onNavigateToComposer
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

/** "10 memories · on device" — the count, and where they are. */
private fun eyebrow(count: Int): String {
    val noun = if (count == 1) "memory" else "memories"
    return "$count $noun · on device"
}

@Composable
private fun BentoGrid(
    memories: List<Memory>,
    onMemoryClick: (Memory) -> Unit,
    onMemoryLongClick: (Memory) -> Unit,
    onRetryProcessing: (Memory) -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        itemsIndexed(
            items = memories,
            key = { _, memory -> memory.id }
        ) { index, memory ->
            StaggeredEntrance(index = index) {
                FeedMemoryCard(
                    memory = memory,
                    onClick = { onMemoryClick(memory) },
                    onLongClick = { onMemoryLongClick(memory) },
                    onRetryProcessing = { onRetryProcessing(memory) }
                )
            }
        }
    }
}

/**
 * Warm tonal empty state card with an inviting capture CTA button.
 */
@Composable
private fun EmptyState(
    message: String,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = "Capture your first memory",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Button(
                    onClick = onCaptureClick,
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Capture memory",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
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

