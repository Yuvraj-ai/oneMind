package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    onNavigateToComposer: () -> Unit,
    onNavigateToMemory: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
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
            // Search bar placeholder
            SearchBarPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // View mode toggle (Feed / Timeline)
            ViewModeToggle(
                currentMode = uiState.viewMode,
                onModeChanged = { viewModel.setViewMode(it) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            // Source filter chips
            SourceFilterRow(
                options = uiState.availableSources,
                selectedFilter = uiState.sourceFilter,
                onFilterSelected = { viewModel.setSourceFilter(it) }
            )

            // Content
            val filteredMemories = filterMemories(uiState.memories, uiState.sourceFilter)

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                filteredMemories.isEmpty() -> {
                    if (uiState.sourceFilter != null && uiState.memories.isNotEmpty()) {
                        EmptyFilterState(modifier = Modifier.fillMaxSize())
                    } else {
                        EmptyFeedState(modifier = Modifier.fillMaxSize())
                    }
                }
                else -> {
                    when (uiState.viewMode) {
                        ViewMode.FEED -> MemoryFeedList(
                            memories = filteredMemories,
                            onMemoryClick = { onNavigateToMemory(it.id) },
                            onMemoryLongClick = { viewModel.requestDelete(it) },
                            onRetryProcessing = { viewModel.retryProcessing(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                        ViewMode.TIMELINE -> TimelineView(
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

@Composable
private fun SearchBarPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = "Search your memories...",
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

@Composable
private fun DeleteConfirmationDialog(
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

@Composable
private fun ViewModeToggle(
    currentMode: ViewMode,
    onModeChanged: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = currentMode == ViewMode.FEED,
            onClick = { onModeChanged(ViewMode.FEED) },
            label = { Text("Feed") }
        )
        FilterChip(
            selected = currentMode == ViewMode.TIMELINE,
            onClick = { onModeChanged(ViewMode.TIMELINE) },
            label = { Text("Timeline") }
        )
    }
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

/**
 * Timeline view: memories grouped under sticky date headers.
 */
@Composable
private fun TimelineView(
    memories: List<com.onemind.app.domain.model.Memory>,
    onMemoryClick: (com.onemind.app.domain.model.Memory) -> Unit,
    onMemoryLongClick: (com.onemind.app.domain.model.Memory) -> Unit,
    onRetryProcessing: (com.onemind.app.domain.model.Memory) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(memories) { DateGrouping.group(memories) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groups.forEach { (group, groupMemories) ->
            stickyHeader(key = group.name) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Text(
                        text = group.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(
                items = groupMemories,
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
}
