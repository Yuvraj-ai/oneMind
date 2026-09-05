package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onNavigateToComposer: () -> Unit,
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEvents: () -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    onNavigateToSearch: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = onNavigateToEvents) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Events"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToComposer,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create memory"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchPill(
                onClick = onNavigateToSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            SectionNav(
                selected = SectionDestination.FEED,
                onSelect = onNavigateToSection,
                modifier = Modifier.padding(horizontal = 16.dp)
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
                ) {
                    CircularProgressIndicator()
                }

                else -> {
                    val filteredMemories = filterMemories(uiState.memories, uiState.sourceFilter)
                    when {
                        filteredMemories.isEmpty() ->
                            if (uiState.sourceFilter != null && uiState.memories.isNotEmpty()) {
                                EmptyFilterState(modifier = Modifier.fillMaxSize())
                            } else {
                                EmptyFeedState(modifier = Modifier.fillMaxSize())
                            }

                        else -> MemoryFeedList(
                            memories = filteredMemories,
                            onMemoryClick = { onNavigateToMemory(it.id) },
                            onMemoryLongClick = { viewModel.requestDelete(it) },
                            onRetryProcessing = { viewModel.retryProcessing(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    uiState.memoryToDelete?.let { memory ->
        DeleteConfirmationDialog(
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDelete() }
        )
    }
}

/**
 * A search affordance that is not a text field.
 *
 * Looks like the bar it replaces and behaves like a button, which is the point: search is
 * its own destination now, and a field here would put a second copy of the query state on
 * a screen that no longer owns any.
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

@Composable
private fun EmptyFeedState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No memories yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tap + to save your first memory",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MemoryFeedList(
    memories: List<com.onemind.app.domain.model.Memory>,
    onMemoryClick: (com.onemind.app.domain.model.Memory) -> Unit,
    onMemoryLongClick: (com.onemind.app.domain.model.Memory) -> Unit,
    onRetryProcessing: (com.onemind.app.domain.model.Memory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = memories,
            key = { it.id }
        ) { memory ->
            MemoryCard(
                memory = memory,
                onClick = { onMemoryClick(memory) },
                onLongClick = { onMemoryLongClick(memory) },
                onRetryProcessing = { onRetryProcessing(memory) }
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
internal fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete memory?") },
        text = { Text("This memory and its contents will be permanently removed.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Client-side filter over the already-loaded memories.
 *
 * Filtering happens client-side because the feed is already loaded into memory
 * and is at most a few thousand rows — querying the DB again for each filter
 * change would thrash the reactive Flow for no benefit.
 */
private fun filterMemories(
    memories: List<com.onemind.app.domain.model.Memory>,
    filter: SourceFilter?
): List<com.onemind.app.domain.model.Memory> {
    if (filter == null) return memories
    return memories.filter { memory ->
        memory.sourceType == filter.sourceType &&
            (filter.sourcePackage == null || memory.sourcePackage == filter.sourcePackage)
    }
}

@Composable
private fun EmptyFilterState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No memories from this source",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
