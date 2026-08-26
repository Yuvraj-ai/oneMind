package com.onemind.app.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Events tab.
 *
 * Two lists. Upcoming holds events still ahead, including ones the user has exported
 * to their calendar — exporting is a copy, not a dismissal. Below it, expired events
 * and rejected ones share a list, because a rejection is reversible and an event
 * nothing renders cannot be undone.
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
                onAddToCalendar = { event ->
                    // Mark only if the calendar app actually opened. A device with no
                    // calendar app at all should not end up with an event claiming to
                    // be in one. What the user does *inside* that app is not
                    // observable either way.
                    runCatching { context.startActivity(viewModel.exportToCalendar(event)) }
                        .onSuccess { viewModel.markAddedToCalendar(event.id) }
                },
                onReject = { event -> viewModel.reject(event.id) },
                onUndoReject = { event -> viewModel.undoReject(event.id) }
            )
        }
    }
}

@Composable
private fun EventList(
    uiState: EventsUiState,
    modifier: Modifier = Modifier,
    onTapEvent: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit
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

            items(uiState.upcomingEvents, key = { it.event.id }) { card ->
                EventCard(
                    card = card,
                    onTap = { onTapEvent(card.event.memoryId) },
                    onAddToCalendar = { onAddToCalendar(card.event) },
                    onReject = { onReject(card.event) },
                    onUndoReject = { onUndoReject(card.event) }
                )
            }
        }

        if (uiState.expiredEvents.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Expired & rejected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(uiState.expiredEvents, key = { it.event.id }) { card ->
                EventCard(
                    card = card,
                    onTap = { onTapEvent(card.event.memoryId) },
                    onAddToCalendar = { onAddToCalendar(card.event) },
                    onReject = { onReject(card.event) },
                    onUndoReject = { onUndoReject(card.event) }
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    card: EventCardUi,
    onTap: () -> Unit,
    onAddToCalendar: () -> Unit,
    onReject: () -> Unit,
    onUndoReject: () -> Unit
) {
    val status = card.event.status
    val isPast = status == EventStatus.EXPIRED || status == EventStatus.REJECTED
    val alpha = if (isPast) 0.6f else 1f

    Card(
        onClick = onTap,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPast) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.event.eventTitle,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatEventTime(card.event.eventTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    )

                    if (card.location != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Place,
                                // Null: the place name beside it already says this,
                                // and a screen reader should not hear "place" twice.
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = card.location,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                        }
                    }
                }

                EventActions(
                    status = status,
                    onAddToCalendar = onAddToCalendar,
                    onReject = onReject,
                    onUndoReject = onUndoReject
                )
            }

            if (card.categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.categories.forEach { category ->
                        CategoryChip(name = category.name, alpha = alpha)
                    }
                }
            }
        }
    }
}

/**
 * What a card offers, by where its event stands.
 *
 * Exhaustive over [EventStatus] without an `else`, deliberately: a fifth status should
 * stop the build here rather than quietly render a card nobody can act on.
 */
@Composable
private fun EventActions(
    status: EventStatus,
    onAddToCalendar: () -> Unit,
    onReject: () -> Unit,
    onUndoReject: () -> Unit
) {
    when (status) {
        EventStatus.UPCOMING -> Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAddToCalendar) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Add to calendar",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onReject) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reject",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Its only remaining transition is expiry, so there is nothing to offer.
        EventStatus.IN_CALENDAR -> StatusPill(
            label = "In calendar",
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer
        )

        EventStatus.REJECTED -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(
                label = "Rejected",
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onUndoReject) { Text("Undo") }
        }

        EventStatus.EXPIRED -> Unit
    }
}

@Composable
private fun StatusPill(label: String, container: Color, content: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * A category, as a label rather than an `AssistChip`.
 *
 * No click semantics on purpose: the whole card is already clickable, and a nested
 * clickable would give a screen reader a second target that does the same thing.
 */
@Composable
private fun CategoryChip(name: String, alpha: Float) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = alpha)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
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
