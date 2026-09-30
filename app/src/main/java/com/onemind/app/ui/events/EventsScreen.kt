package com.onemind.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.ui.components.ExpressiveIconButton
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance
import com.onemind.app.ui.theme.Tracking

/**
 * The Events destination.
 *
 * Two lists:
 * - Upcoming holds events still ahead, with the nearest event styled as a prominent Hero card
 *   in `primaryContainer` and remaining items as standard agenda cards.
 * - Expired and rejected events share a past/resolved list, because rejecting is reversible
 *   and an event nothing renders cannot be undone.
 *
 * The back arrow is integrated via [ExpressiveIconButton] alongside [SectionNav] for swift tab switching.
 */
@Composable
fun EventsScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSection: (SectionDestination) -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    PhoneFrame {
        HeroHeader(
            eyebrow = eyebrow(uiState.upcomingEvents.size),
            title = "Things coming up",
            leading = {
                ExpressiveIconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        SectionNav(
            selected = SectionDestination.EVENTS,
            onSelect = onNavigateToSection,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)
        )

        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.upcomingEvents.isEmpty() && uiState.expiredEvents.isEmpty() ->
                EmptyEventsState()

            else -> EventList(
                uiState = uiState,
                onTapEvent = onNavigateToMemory,
                onAddToCalendar = { event ->
                    // Mark only if the calendar app actually opened. A device with no
                    // calendar app should not leave an event claiming to be in one.
                    runCatching { context.startActivity(viewModel.exportToCalendar(event)) }
                        .onSuccess { viewModel.markAddedToCalendar(event.id) }
                },
                onReject = { viewModel.reject(it.id) },
                onUndoReject = { viewModel.undoReject(it.id) }
            )
        }
    }
}

/** "3 upcoming · detected", per `events.html`. */
private fun eyebrow(upcoming: Int): String = "$upcoming upcoming · detected"

@Composable
private fun EventList(
    uiState: EventsUiState,
    onTapEvent: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit
) {
    val order = remember(uiState.upcomingEvents, uiState.expiredEvents) {
        (uiState.upcomingEvents.asSequence() + uiState.expiredEvents.asSequence())
            .withIndex()
            .associate { (index, card) -> card.event.id to index }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (uiState.upcomingEvents.isNotEmpty()) {
            item(key = "heading-upcoming") {
                SectionHeading(
                    label = "Upcoming",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            itemsIndexed(uiState.upcomingEvents, key = { _, card -> card.event.id }) { index, card ->
                StaggeredEntrance(index = order[card.event.id] ?: 0) {
                    EventCard(
                        card = card,
                        isHero = (index == 0),
                        onTap = onTapEvent,
                        onAddToCalendar = onAddToCalendar,
                        onReject = onReject,
                        onUndoReject = onUndoReject
                    )
                }
            }
        }

        if (uiState.expiredEvents.isNotEmpty()) {
            item(key = "heading-past") {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeading(
                    label = "Expired & rejected",
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(uiState.expiredEvents, key = { it.event.id }) { card ->
                StaggeredEntrance(index = order[card.event.id] ?: 0) {
                    EventCard(
                        card = card,
                        isHero = false,
                        onTap = onTapEvent,
                        onAddToCalendar = onAddToCalendar,
                        onReject = onReject,
                        onUndoReject = onUndoReject
                    )
                }
            }
        }
    }
}

/** Tonal section header pill with horizontal rule divider. */
@Composable
private fun SectionHeading(label: String, container: Color, content: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The pill's own text is what `EventsScreenTest` looks for.
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = container
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = Tracking.Chip,
                color = content,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun EmptyEventsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No upcoming events",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "When you save something with a future date,\nit will appear here automatically",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
