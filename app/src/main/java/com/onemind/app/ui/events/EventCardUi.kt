package com.onemind.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.Category
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EntityType
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.model.ExtractedEntity
import com.onemind.app.ui.components.CategoryChip
import com.onemind.app.ui.components.ExpressiveIconButton
import com.onemind.app.ui.components.StatusPill
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.PillShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One event as the Events screen draws it.
 *
 * Carries the [DetectedEvent] whole rather than flattening it into strings, because
 * every action on the card needs its id and its status. The two extra fields are not
 * on `DetectedEvent` and deliberately are not being added to it: they belong to the
 * Memory the event is a lens on, and reading them is a presentation concern.
 */
data class EventCardUi(
    val event: DetectedEvent,
    /** The Memory's first PLACE entity, or null when it named no place. */
    val location: String? = null,
    /** At most [EventCardAssembly.MAX_CHIPS] of the Memory's categories. */
    val categories: List<Category> = emptyList()
)

/**
 * Joins events to the Memory data their cards show.
 *
 * Pure and separate from the ViewModel so it can be checked on the JVM, the same
 * reasoning that put `ReminderPlanner` and `DateGrouping` in their own files. It takes
 * maps rather than a repository because deciding *what* a card shows and deciding
 * *how many queries that costs* are different problems.
 */
object EventCardAssembly {

    /** How many category chips fit a card before it starts wrapping. */
    const val MAX_CHIPS = 3

    fun assemble(
        events: List<DetectedEvent>,
        entities: Map<Long, List<ExtractedEntity>>,
        categories: Map<Long, List<Category>>
    ): List<EventCardUi> = events.map { event ->
        EventCardUi(
            event = event,
            // First rather than best: entities carry a confidence, but it is nullable
            // and often absent, so ranking on it would mostly be ranking on nothing.
            location = entities[event.memoryId]
                ?.firstOrNull { it.entityType == EntityType.PLACE }
                ?.name,
            categories = categories[event.memoryId].orEmpty().take(MAX_CHIPS)
        )
    }
}

/**
 * Hero Event Card for the nearest upcoming event matching spec §4.3.
 *
 * Rendered in `primaryContainer` with `onPrimaryContainer` typography,
 * an expressive calendar pill badge, location/category snippets, clear link to source memory,
 * and prominent action buttons.
 */
@Composable
fun HeroEventCard(
    card: EventCardUi,
    onTap: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val event = card.event
    val status = event.status
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(interactionSource = interaction, restCorner = 20.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(morph)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Open memory"
            ) { onTap(event.memoryId) },
        shape = RoundedCornerShape(morph.corner),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: Expressive calendar pill badge + StatusIndicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = formatEventTime(event.eventTime),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                StatusIndicator(status = status)
            }

            // Title
            Text(
                text = event.eventTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (status == EventStatus.REJECTED) TextDecoration.LineThrough else null
            )

            // Location snippet
            if (card.location != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        text = card.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Categories
            if (card.categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.categories.forEach { CategoryChip(name = it.name) }
                }
            }

            // Link to source memory
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "View memory",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            // Action buttons
            EventActions(
                status = status,
                isHero = true,
                onAddToCalendar = { onAddToCalendar(event) },
                onReject = { onReject(event) },
                onUndoReject = { onUndoReject(event) }
            )
        }
    }
}

/**
 * Standard agenda event card matching spec §4.3.
 *
 * Wrapped in 16dp `surfaceContainer` card with time pill badge, location snippet,
 * clear link to source memory, and status-driven actions.
 */
@Composable
fun StandardEventCard(
    card: EventCardUi,
    onTap: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val event = card.event
    val status = event.status
    val isPast = status == EventStatus.EXPIRED || status == EventStatus.REJECTED

    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(interactionSource = interaction, restCorner = 16.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(morph)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Open memory"
            ) { onTap(event.memoryId) },
        shape = RoundedCornerShape(morph.corner),
        color = if (isPast) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: Time pill badge + StatusIndicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = PillShape,
                    color = if (isPast) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
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
                            }
                        )
                    }
                }

                StatusIndicator(status = status)
            }

            // Title
            Text(
                text = event.eventTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (status == EventStatus.REJECTED) {
                    TextDecoration.LineThrough
                } else null,
                color = if (isPast) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            // Location snippet
            if (card.location != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
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

            // Categories
            if (card.categories.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.categories.forEach { CategoryChip(name = it.name) }
                }
            }

            // Clear link to source memory
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "View memory",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Action buttons
            EventActions(
                status = status,
                isHero = false,
                onAddToCalendar = { onAddToCalendar(event) },
                onReject = { onReject(event) },
                onUndoReject = { onUndoReject(event) }
            )
        }
    }
}

/** Generic entry point routing between hero and standard cards. */
@Composable
fun EventCard(
    card: EventCardUi,
    isHero: Boolean,
    onTap: (Long) -> Unit,
    onAddToCalendar: (DetectedEvent) -> Unit,
    onReject: (DetectedEvent) -> Unit,
    onUndoReject: (DetectedEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isHero) {
        HeroEventCard(card, onTap, onAddToCalendar, onReject, onUndoReject, modifier)
    } else {
        StandardEventCard(card, onTap, onAddToCalendar, onReject, onUndoReject, modifier)
    }
}

/** Pill badge on the event card for states that warrant notice. */
@Composable
fun StatusIndicator(status: EventStatus) {
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

/** Action buttons offered by event status. */
@Composable
fun EventActions(
    status: EventStatus,
    isHero: Boolean,
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
                container = if (isHero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                content = if (isHero) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onTertiary,
                onClick = onAddToCalendar,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onReject,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Reject",
                    tint = if (isHero) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

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
fun ActionButton(
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = label },
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

fun formatEventTime(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withZone(zone)
    return formatter.format(at)
}
