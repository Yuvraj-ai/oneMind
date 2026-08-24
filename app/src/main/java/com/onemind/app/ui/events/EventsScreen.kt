package com.onemind.app.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.DetectedEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Events tab.
 *
 * Shows upcoming events sorted by event time (soonest first), with an expandable
 * "Expired" section at the bottom for history. Each event has an "Add to Calendar"
 * button that exports it via ACTION_INSERT — no permissions needed, the user
 * confirms in their own calendar app.
 *
 * The [Scaffold] is not decoration. `MainActivity` calls `enableEdgeToEdge()`, so
 * every destination owns its own window insets, and a `Scaffold` with a
 * [TopAppBar] is how every other screen here handles them. Without one this screen
 * drew its first row under the status bar, on top of the system clock, and had no
 * back affordance at all — alone among pushed destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Events") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Every branch is inside the Scaffold, including loading and empty. An early
        // return would have taken the top bar with it, and with it the way back —
        // stranding a user who opened Events before saving anything with a date.
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            uiState.upcomingEvents.isEmpty() && uiState.expiredEvents.isEmpty() ->
                EmptyEventsState(modifier = Modifier.padding(paddingValues))

            else -> EventList(
                uiState = uiState,
                modifier = Modifier.padding(paddingValues),
                onTapEvent = onNavigateToMemory,
                onExportToCalendar = { event ->
                    context.startActivity(viewModel.exportToCalendar(event))
                }
            )
        }
    }
}

@Composable
private fun EventList(
    uiState: EventsUiState,
    modifier: Modifier = Modifier,
    onTapEvent: (Long) -> Unit,
    onExportToCalendar: (DetectedEvent) -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.upcomingEvents.isNotEmpty()) {
            item {
                Text(
                    text = "Upcoming",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            items(uiState.upcomingEvents, key = { it.id }) { event ->
                EventCard(
                    event = event,
                    isExpired = false,
                    onTap = { onTapEvent(event.memoryId) },
                    onExportToCalendar = { onExportToCalendar(event) }
                )
            }
        }

        if (uiState.expiredEvents.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Past events",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(uiState.expiredEvents, key = { it.id }) { event ->
                EventCard(
                    event = event,
                    isExpired = true,
                    onTap = { onTapEvent(event.memoryId) },
                    onExportToCalendar = null
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    event: DetectedEvent,
    isExpired: Boolean,
    onTap: () -> Unit,
    onExportToCalendar: (() -> Unit)?
) {
    val alpha = if (isExpired) 0.6f else 1f

    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpired) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.eventTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatEventTime(event.eventTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                )
            }

            if (onExportToCalendar != null) {
                IconButton(onClick = onExportToCalendar) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Add to calendar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyEventsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "No upcoming events",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "When you save something with a future date,\nit will appear here automatically",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatEventTime(at: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(at)
}
