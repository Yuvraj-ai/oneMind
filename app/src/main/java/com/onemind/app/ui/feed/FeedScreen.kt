package com.onemind.app.ui.feed

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onNavigateToEvents: () -> Unit,
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
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = { viewModel.onSearchQueryChanged(it) },
                onClear = { viewModel.clearSearch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Browsing controls are hidden while searching. The locked product
            // decisions rule out manual filters in the search experience: context
            // belongs in the query text, not in chips beside it. They stay for
            // browsing, which is a different activity.
            if (!uiState.isSearchActive) {
                ViewModeToggle(
                    currentMode = uiState.viewMode,
                    onModeChanged = { viewModel.setViewMode(it) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SourceFilterRow(
                    options = uiState.availableSources,
                    selectedFilter = uiState.sourceFilter,
                    onFilterSelected = { viewModel.setSourceFilter(it) }
                )
            }

            when {
                uiState.isSearchActive -> SearchResultsSection(
                    uiState = uiState,
                    onMemoryClick = { onNavigateToMemory(it) },
                    onMemoryLongClick = { viewModel.requestDelete(it) }
                )

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

                        uiState.viewMode == ViewMode.FEED -> MemoryFeedList(
                            memories = filteredMemories,
                            onMemoryClick = { onNavigateToMemory(it.id) },
                            onMemoryLongClick = { viewModel.requestDelete(it) },
                            onRetryProcessing = { viewModel.retryProcessing(it) },
                            modifier = Modifier.fillMaxSize()
                        )

                        else -> TimelineView(
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
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search your memories...") },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search"
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

/**
 * Search results, or an explanation of why there are none.
 *
 * The three states are kept distinct because they call for different things from
 * the user: wait, refine, or carry on. Collapsing them into one "no results"
 * message would tell someone their search failed while it was still running.
 */
@Composable
private fun SearchResultsSection(
    uiState: FeedUiState,
    onMemoryClick: (Long) -> Unit,
    onMemoryLongClick: (com.onemind.app.domain.model.Memory) -> Unit
) {
    when {
        uiState.isSearching && uiState.searchResults.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        uiState.searchResults.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No memories found",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try describing it differently",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = uiState.searchResults,
                key = { it.memory.id }
            ) { result ->
                SearchResultCard(
                    result = result,
                    queryTerms = uiState.searchTerms,
                    onClick = { onMemoryClick(result.memory.id) },
                    onLongClick = { onMemoryLongClick(result.memory) }
                )
            }
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
