package com.onemind.app.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
import com.onemind.app.data.local.entity.DetectedEventEntity
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
 */
@Composable
fun EventsScreen(
    onNavigateToMemory: (Long) -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (uiState.upcomingEvents.isEmpty() && uiState.expiredEvents.isEmpty()) {
        EmptyEventsState()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                    onTap = { onNavigateToMemory(event.memoryId) },
                    onExportToCalendar = {
                        context.startActivity(viewModel.exportToCalendar(event))
                    }
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
                    onTap = { onNavigateToMemory(event.memoryId) },
                    onExportToCalendar = null
                )
            }
        }
    }
}

@Composable
private fun EventCard(
    event: DetectedEventEntity,
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
private fun EmptyEventsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
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

private fun formatEventTime(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
