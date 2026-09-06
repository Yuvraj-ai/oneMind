package com.onemind.app.ui.events

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.ui.components.CategoryChip
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StaggeredEntrance
import com.onemind.app.ui.components.StatusPill
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.CardShapeSmall
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Events destination.
 *
 * Two lists. Upcoming holds events still ahead, including ones exported to a calendar —
 * exporting is a copy, not a dismissal. Below it, expired and rejected events share a list,
 * because rejecting is reversible and an event nothing renders cannot be undone.
 *
 * The back arrow is kept alongside the segmented group and is not redundant with it. #37
 * exists because this screen shipped with no way back at all, alone among pushed
 * destinations; the group moves between peers, it does not leave. [HeroHeader] consumes the
 * status-bar inset that the old `Scaffold` + `TopAppBar` used to, which is the other half
 * of what #37 fixed.
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
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        SectionNav(
            selected = SectionDestination.EVENTS,
            onSelect = onNavigateToSection,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // Every branch stays inside the frame, loading and empty included. An early return
        // would take the header with it, and with it the way back — stranding a user who
        // opened Events before saving anything with a date.
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
                    // calendar app should not leave an event claiming to be in one. What
                    // the user does inside that app is not observable either way.
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
    // One flat numbering across both lists, precomputed and keyed by id rather than counted
    // inside the item lambda — that lambda runs per composition, not per item, so a `var`
    // there climbs without bound on scroll and re-keys StaggeredEntrance on the way back.
    // Same defect as the timeline had; same fix. The stagger runs down the whole screen
    // once rather than restarting at the second heading, which would read as two lists
    // appearing separately. Capping the delay is StaggeredEntrance's own business.
    val order = remember(uiState.upcomingEvents, uiState.expiredEvents) {
        (uiState.upcomingEvents.asSequence() + uiState.expiredEvents.asSequence())
            .withIndex()
            .associate { (index, card) -> card.event.id to index }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.upcomingEvents.isNotEmpty()) {
            item(key = "heading-upcoming") {
                SectionHeading(
                    label = "Upcoming",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            items(uiState.upcomingEvents, key = { it.event.id }) { card ->
                StaggeredEntrance(index = order[card.event.id] ?: 0) {
                    EventCard(card, onTapEvent, onAddToCalendar, onReject, onUndoReject)
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
                    EventCard(card, onTapEvent, onAddToCalendar, onReject, onUndoReject)
                }
            }
        }
    }
}

/** `section-tag` + `section-rule`: an uppercase pill, then a hairline across the rest. */
@Composable
private fun SectionHeading(label: String, container: Color, content: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // The pill's own text is what `EventsScreenTest` looks for, so the label reaches
        // the semantics tree unchanged apart from case.
        Surface(shape = PillShape, color = container) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = Tracking.Chip,
                color = content,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventCard(
    card: EventCardUi,
    onTap: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit
) {
    val event = card.event
    val status = event.status
    val isPast = status == EventStatus.EXPIRED || status == EventStatus.REJECTED

    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(interactionSource = interaction, restCorner = 32.dp)
    val pressed = morph.scale < 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(morph)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Open memory"
            ) { onTap(event.memoryId) },
        shape = if (pressed) RoundedCornerShape(morph.corner) else CardShapeSmall,
        color = if (isPast) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isPast) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                Text(
                    text = formatEventTime(event.eventTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPast) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.weight(1f)
                )
                StatusIndicator(status)
            }

            Text(
                text = event.eventTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Struck through only when the user rejected it. An expired event still
                // happened, and a line through it would claim they dismissed something
                // they never touched.
                textDecoration = if (status == EventStatus.REJECTED) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
                color = if (isPast) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (card.location != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        // Null: the place name beside it already says this, and a screen
                        // reader should not hear "place" twice.
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = card.location,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (card.categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.categories.forEach { CategoryChip(name = it.name) }
                }
            }

            EventActions(
                status = status,
                onAddToCalendar = { onAddToCalendar(event) },
                onReject = { onReject(event) },
                onUndoReject = { onUndoReject(event) }
            )
        }
    }
}

/**
 * The pill on the date row, for the states that have something to say there.
 *
 * `UPCOMING` gets none: it is the default, and a pill on every upcoming card would cost a
 * slot on all of them to say what the list heading already says. `events.html` does render
 * an "Upcoming" pill here, and this is a deliberate departure from it — rendering that pill
 * would also put a second "Upcoming" node beside the section heading, which the instrumented
 * contract (`onNodeWithText("Upcoming")`, expecting one match) is built on.
 */
@Composable
private fun StatusIndicator(status: EventStatus) {
    when (status) {
        EventStatus.UPCOMING -> Unit
        EventStatus.IN_CALENDAR -> StatusPill(
            label = "In calendar",
            container = MaterialTheme.colorScheme.tertiary,
            content = MaterialTheme.colorScheme.onTertiary
        )
        EventStatus.REJECTED -> StatusPill(
            label = "Rejected",
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant
        )
        EventStatus.EXPIRED -> StatusPill(
            label = "Expired",
            container = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
            content = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * What a card offers, by where its event stands.
 *
 * Exhaustive over [EventStatus] with no `else`, so a fifth status stops the build here
 * rather than quietly rendering a card nobody can act on.
 */
@Composable
private fun EventActions(
    status: EventStatus,
    onAddToCalendar: () -> Unit,
    onReject: () -> Unit,
    onUndoReject: () -> Unit
) {
    when (status) {
        EventStatus.UPCOMING -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButton(
                label = "Add to calendar",
                container = MaterialTheme.colorScheme.tertiary,
                content = MaterialTheme.colorScheme.onTertiary,
                onClick = onAddToCalendar,
                modifier = Modifier.weight(1f)
            )
            // Square, 48 dp, so it is a real tap target beside a full-width button.
            IconButton(
                onClick = onReject,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reject",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Its only remaining transition is expiry. The pill on the date row already says
        // where it stands, so there is nothing left to offer.
        EventStatus.IN_CALENDAR -> Unit

        EventStatus.REJECTED -> ActionButton(
            label = "Undo",
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = MaterialTheme.colorScheme.primary,
            onClick = onUndoReject,
            modifier = Modifier.fillMaxWidth()
        )

        EventStatus.EXPIRED -> Unit
    }
}

@Composable
private fun ActionButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.large,
        color = container
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = content
            )
        }
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

private fun formatEventTime(at: Instant): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
    return formatter.format(at)
}
