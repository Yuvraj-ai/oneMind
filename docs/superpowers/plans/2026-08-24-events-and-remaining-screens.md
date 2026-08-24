# Events and Remaining Screens Restyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the last five screens onto the expressive design — Events rebuilt around its four card states, and Composer, Memory detail, Onboarding and Settings restyled.

**Architecture:** Every screen here swaps `Scaffold` + `TopAppBar` for `PhoneFrame` + `HeroHeader` or keeps a small top bar where the reference does, and replaces locally-defined chips and pills with the shared components. No view model, repository or DAO is touched: the state each screen reads already exists, and this plan only changes how it is drawn.

**Tech Stack:** Kotlin 2.1.0, Jetpack Compose (material3 1.4.0), Hilt 2.53.1, Compose UI test.

## Global Constraints

- **Branch: `my-extra-work`.** Never commit to `main`; never merge or fast-forward from `main`.
- **Commit attribution is the user alone.** No `Co-Authored-By`, no generated-by trailer.
- **No release.** No version bump, tag, APK, or GitHub release unless explicitly asked.
- **One commit per issue**, `(#N)` in the subject, filed before implementation, closed after with an AI-attributed comment.
- **GitHub access is curl/python, not `gh`.** Token from `/home/imyuvi/projects/codingagents/.env`, never echoed. **Never run `git remote -v`.**
- **Build invocation** from `/home/imyuvi/projects/codingagents/oneMind`: `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew <task>`. `java` is not on `PATH`.
- **Presentation only, and strictly.** No file under `domain/`, `data/` or `capture/` changes. **No ViewModel changes either** — every screen below already receives the state it needs. If a restyle appears to need new state, stop and report rather than reaching for it.
- **440 dp frame** via `PhoneFrame` at each screen root, once.
- **48 dp tap targets**, 56 dp for the composer's attach button. Never shrunk.
- **`HeroHeader` owns the status-bar inset.** Screens that adopt it must not add their own `statusBarsPadding`, and screens that keep a `Scaffold` keep getting it from the `TopAppBar`.
- **The existing `EventsScreenTest` assertions are a contract.** `"Events"`, `"Upcoming"`, `"Expired & rejected"`, `"No upcoming events"`, and the content descriptions `"Back"`, `"Add to calendar"`, `"Reject"`, plus `"In calendar"`, `"Rejected"`, `"Undo"` must all still resolve. #37 exists because this screen lost its back affordance once.

## Prerequisites

Both must be complete:

- `docs/superpowers/plans/2026-08-24-expressive-theme-foundation.md` — supplies `PhoneFrame`, `HeroHeader`, `SectionNav`, `CategoryChip`, `StatusPill`, `StateChip`, `CookieThumb`, `StaggeredEntrance`, `rememberPressMorph`, `pressScale`, the card shapes, `PillShape`, `EmberGradient`, `Tracking`.
- `docs/superpowers/plans/2026-08-24-feed-timeline-search-destinations.md` — supplies `navigateToSection`, `SectionDestination` wiring, and `MemoryDisplay`.

`docs/superpowers/plans/2026-08-24-events-reject-and-calendar.md` must also be complete —
this plan restyles the four event states that plan creates.

## File Structure

| File | Responsibility | Change |
|---|---|---|
`ui/events/EventsScreen.kt` | Events destination | Rewritten |
`ui/navigation/OneMindNavHost.kt` | Graph | Modify — Events gains section nav |
`app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt` | Events behaviour | Modify — new structure, same contract |
`ui/composer/ComposerScreen.kt` | Capture | Modify — composer, toolbar, draft pill |
`ui/feed/MemoryDetailScreen.kt` | One memory | Modify — summary block, section cards |
`ui/onboarding/ModelSelectionScreen.kt` | Model choice | Modify — pill rows, size badges, sticky CTA |
`ui/settings/SettingsScreen.kt` | Settings | Modify — labelled sections, fields, switch, segmented actions |

Two issues, two commits:

| Issue | Tasks | Subject |
|---|---|---|
| I | 1–2 | Events redesign |
| J | 3–6 | Composer, memory detail, onboarding, settings restyle |

Issue J groups four screens into one commit because each is a restyle of an already-correct
screen with no behaviour change, and a reviewer gains nothing from four separate diffs of
the same kind. Where a screen turns out to need more than restyling, split it out and say
why.

---

## Task 1: Rewrite `EventsScreen`

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt` (whole file)
- Modify: `app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt`

**Interfaces:**
- Consumes: `EventCardUi`, `EventsUiState`, `EventsViewModel` (events plan); `PhoneFrame`, `HeroHeader`, `SectionNav`, `SectionDestination`, `CategoryChip`, `StatusPill`, `StaggeredEntrance`, `rememberPressMorph`, `pressScale`, `CardShapeSmall`, `PillShape`, `Tracking`.
- Produces: `@Composable fun EventsScreen(onNavigateToMemory: (Long) -> Unit, onNavigateBack: () -> Unit, onNavigateToSection: (SectionDestination) -> Unit, viewModel: EventsViewModel = hiltViewModel())`

**The back affordance stays, alongside the group.** #37 exists because this screen shipped
with no way back, and its test still asserts a `"Back"` content description. The segmented
group is in addition to that, not a replacement — it moves between peers, it does not
leave. The arrow goes in `HeroHeader`'s `leading` slot.

**Expired and rejected cards diverge.** The reference dims both, but only strikes through
the *rejected* title: an expired event still happened, and a line through it would say the
user dismissed something they never touched.

- [ ] **Step 1: Rewrite the file**

Replace the whole of `app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt`:

```kotlin
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
    // Continuous across both lists so the entrance runs down the screen once rather than
    // restarting at the second heading, which reads as two lists appearing separately.
    var position = 0

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
                val index = position++
                StaggeredEntrance(index = index) {
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
                val index = position++
                StaggeredEntrance(index = index) {
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
 * slot on all of them to say what the list heading already says.
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
```

The private `CategoryChip` and `StatusPill` the events plan added to this file are gone —
the shared components replace them, and `CategoryChip` now uses `primaryContainer` per
DESIGN-GUIDE §5.4 rather than the interim `secondaryContainer`.

- [ ] **Step 2: The heading label is no longer the screen title**

`EventsScreenTest.theEmptyStateAlsoClearsTheStatusBar` asserts `onNodeWithText("Events")`.
The old `TopAppBar` title is gone; the hero says "Things coming up". The segmented group's
Events segment still renders the text `"Events"`, so the assertion resolves — but it now
resolves against the nav rather than a title.

That is a coincidence, not a contract. Task 2 changes the assertion to name what it means
instead of relying on it.

- [ ] **Step 3: Wire the section callback**

In `app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt`, replace the
`composable(NavRoutes.EVENTS)` block:

```kotlin
        composable(NavRoutes.EVENTS) {
            EventsScreen(
                onNavigateToMemory = { memoryId ->
                    navController.navigate(NavRoutes.memoryDetail(memoryId))
                },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSection = navController::navigateToSection
            )
        }
```

- [ ] **Step 4: Confirm it compiles**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL for `main`. `androidTest` will fail — `EventsScreenTest` calls
`EventsScreen` without `onNavigateToSection`. Task 2 fixes it.

---

## Task 2: Update `EventsScreenTest` — Issue I lands here

**Files:**
- Modify: `app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt`

**Interfaces:**
- Consumes: the rewritten `EventsScreen` (Task 1).
- Produces: no API. Keeps the assertion contract, and stops one assertion passing by accident.

- [ ] **Step 1: Fix the call and sharpen the assertions**

In `renderScreen`, add the new parameter:

```kotlin
                EventsScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    onNavigateToSection = {},
                    viewModel = EventsViewModel(repository, mockk(relaxed = true), Clock.systemUTC())
                )
```

Rename the status-bar test's target, since the header it guards is now the hero rather
than the "Upcoming" heading, and update its body:

```kotlin
    @Test
    fun theHeroDoesNotDrawUnderTheStatusBar() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))

        renderScreen()

        val statusBar = statusBarHeightDp()
        assertTrue(
            "This device reports no status bar inset, so the overlap cannot be " +
                "observed and this test would pass for the wrong reason",
            statusBar > 0f
        )

        // The Scaffold + TopAppBar that used to consume this inset is gone; HeroHeader
        // consumes it now. #37 was this screen drawing its first row over the system
        // clock, and the mechanism that prevented it has been replaced — so the
        // assertion moves to the new first row rather than being retired with the old one.
        val heroTop = composeRule.onNodeWithText("Things coming up").getBoundsInRoot().top
        assertTrue(
            "\"Things coming up\" starts at ${heroTop.value}dp, inside the " +
                "${statusBar}dp status bar — it is drawing over the system clock",
            heroTop.value >= statusBar
        )
    }
```

In `theEmptyStateAlsoClearsTheStatusBar`, replace:

```kotlin
        composeRule.onNodeWithText("Events").assertIsDisplayed()
        composeRule.onNodeWithText("No upcoming events").assertIsDisplayed()
```

with:

```kotlin
        // "Events" used to be the top bar's title. It is now the segmented group's third
        // segment, which happens to render the same string — so asserting on it would keep
        // passing while meaning something else entirely. Assert the hero and the empty
        // state, which are what this screen owes a user who has saved nothing yet.
        composeRule.onNodeWithText("Things coming up").assertIsDisplayed()
        composeRule.onNodeWithText("No upcoming events").assertIsDisplayed()
```

Add one test for the group, since it is new on this screen:

```kotlin
    @Test
    fun theSectionGroupIsOfferedAlongsideTheWayBack() {
        var section: SectionDestination? = null
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        composeRule.activity.runOnUiThread { composeRule.activity.enableEdgeToEdge() }
        composeRule.setContent {
            OneMindTheme {
                EventsScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    onNavigateToSection = { section = it },
                    viewModel = EventsViewModel(repository, mockk(relaxed = true), Clock.systemUTC())
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Feed").performClick()
        composeRule.waitForIdle()
        assertEquals(SectionDestination.FEED, section)

        // And the arrow is still there. The group moves between peers; it does not leave,
        // and #37 was this screen having no way out.
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backPresses)
    }
```

Add `import com.onemind.app.ui.components.SectionDestination`.

- [ ] **Step 2: Run the suite**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL on both; `EventsScreenTest` at 9 tests, 0 failures.

- [ ] **Step 3: Compare against the mock**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

Open `/home/imyuvi/projects/codingagents/design-reference/events.html` beside the running
app. Check all four states: an upcoming card with the accent "Add to calendar" and a square
reject; an in-calendar card with the accent pill and no actions; a rejected card, dimmed,
struck through, with Undo; an expired card, dimmed, not struck through, with nothing.

- [ ] **Step 4: File Issue I, commit, close**

File:

```bash
python3 - <<'PY'
import json, re, urllib.request
pat = re.search(r'^GITHUB_PAT=(.*)$',
                open('/home/imyuvi/projects/codingagents/.env').read(), re.M).group(1).strip()
body = {
  "title": "Events redesign",
  "body": """The Events screen works but does not look like the reference. It is a
`Scaffold` with an empty-ish top bar over a `LazyColumn` of plain `Card`s, with locally
defined chip and pill composables that duplicate the shared ones.

**Fix.** `PhoneFrame` + `HeroHeader` + `SectionNav`, and cards on the asymmetric small
silhouette with the press morph. The four states get the reference's treatment: accent "Add
to calendar" with a square reject for upcoming, an accent pill and no actions for in
calendar, dimmed and struck through with Undo for rejected, dimmed and inert for expired.

Two decisions worth recording.

`HeroHeader` now consumes the status-bar inset that the `Scaffold` + `TopAppBar` used to.
That mechanism is exactly what #37 fixed, so retiring it without moving the guard would
quietly reopen the defect — the instrumented assertion moves to the hero rather than being
deleted with the old header.

`EventsScreenTest` asserted `onNodeWithText("Events")` against the old top-bar title. The
segmented group's third segment renders the same string, so the assertion would keep passing
while meaning something completely different. It now asserts the hero and the empty state.

Only rejected titles are struck through, not expired ones: an expired event still happened,
and a line through it would say the user dismissed something they never touched.

The back arrow stays alongside the group. The group moves between peers; it does not leave,
and #37 was this screen having no way out at all.

Plan: `docs/superpowers/plans/2026-08-24-events-and-remaining-screens.md`"""
}
req = urllib.request.Request(
    "https://api.github.com/repos/Yuvraj-ai/oneMind/issues",
    data=json.dumps(body).encode(),
    headers={"Authorization": f"Bearer {pat}", "Accept": "application/vnd.github+json",
             "Content-Type": "application/json"})
print("issue", json.load(urllib.request.urlopen(req))["number"])
PY
```

Commit:

```bash
git rev-parse --abbrev-ref HEAD   # must print my-extra-work

git add app/src/main/java/com/onemind/app/ui/events/EventsScreen.kt \
        app/src/main/java/com/onemind/app/ui/navigation/OneMindNavHost.kt \
        app/src/androidTest/java/com/onemind/app/EventsScreenTest.kt

git commit -F - <<'MSG'
feat(ui): redesign the Events screen (#I)

PhoneFrame, HeroHeader and SectionNav replace the Scaffold and top bar; cards
move onto the asymmetric small silhouette with the press morph; the local chip
and pill composables give way to the shared ones. The four states get the
reference's treatment — accent "Add to calendar" plus a square reject when
upcoming, an accent pill and nothing to do once in a calendar, dimmed and
struck through with Undo when rejected, dimmed and inert when expired.

HeroHeader now consumes the status-bar inset the TopAppBar used to. That is the
mechanism #37 fixed, so the instrumented assertion moved to the hero instead of
being deleted along with the header it was written against — retiring the guard
with the thing it guards is how a fixed defect comes back.

EventsScreenTest asserted onNodeWithText("Events") against the old top-bar
title. The group's third segment renders that same string, so the assertion
would have kept passing while meaning something entirely different. It asserts
the hero and the empty state now.

Only rejected titles are struck through. An expired event still happened, and a
line through it would claim the user dismissed something they never touched.

The back arrow stays. The group moves between peers, it does not leave, and #37
was this screen having no way out.
MSG
```

Close with a comment covering the same points and ending
`*Implemented by an AI agent (Claude), reviewed against the design reference.*`

---

## Task 3: Composer

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/composer/ComposerScreen.kt`

**Interfaces:**
- Consumes: existing `ComposerUiState` and `ComposerViewModel`, unchanged.
- Produces: no signature change. `ComposerScreen(memoryId, onNavigateBack, viewModel)` keeps its parameters.

**The `Scaffold` stays here.** This screen has a bottom toolbar that must stay pinned above
the keyboard and the navigation bar, which is what `Scaffold`'s `bottomBar` is for, and a
small top bar rather than a hero — `capture.html` specifies exactly that. Replacing it with
`PhoneFrame` would mean re-implementing inset handling that already works.

**`BackHandler` and `onLeaveComposer` are not touched.** Their comment records a defect
where anything typed inside the autosave window was lost outright and the Memory stayed in
`DRAFT` — never enqueued, never enriched, never searchable. Restyling must not go near it.

- [ ] **Step 1: Restyle the top bar's draft pill**

Read the current top bar first:

```bash
sed -n '74,100p' app/src/main/java/com/onemind/app/ui/composer/ComposerScreen.kt
```

Replace the `AnimatedVisibility` block inside `TopAppBar`'s `title` with a pill that is
present in both states rather than appearing and vanishing:

```kotlin
                title = {
                    // Present in both states rather than fading in and out. The reference
                    // shows "Draft" from the start and switches it to "Draft saved"; a
                    // pill that appears from nowhere reads as an alert, when what it is
                    // reporting is that nothing has gone wrong.
                    val saved = uiState.showSavedIndicator
                    Surface(
                        shape = PillShape,
                        color = if (saved) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (saved) "Draft saved" else "Draft",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = Tracking.Chip,
                                color = if (saved) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (saved) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
```

Add imports: `androidx.compose.material.icons.filled.Check`, `androidx.compose.material3.Surface`,
`com.onemind.app.ui.theme.PillShape`, `com.onemind.app.ui.theme.Tracking`,
`androidx.compose.foundation.layout.size`. Remove the `AnimatedVisibility`, `fadeIn` and
`fadeOut` imports if nothing else uses them.

- [ ] **Step 2: Restyle the composer field**

Replace the `TextField` block (`:144-160`) with:

```kotlin
                // 52 vh, per `capture.html`: tall enough that the field is obviously the
                // point of the screen, short enough that the toolbar stays visible.
                val composerHeight = LocalConfiguration.current.screenHeightDp.dp * 0.52f

                TextField(
                    value = uiState.text,
                    onValueChange = { viewModel.onTextChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(composerHeight),
                    placeholder = {
                        Text(
                            text = "What do you want to remember?",
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    // Outfit at 32 sp: the reference sets the composer in the display face,
                    // which is what makes typing feel like writing rather than filling in
                    // a form.
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 32.sp,
                        lineHeight = 44.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
```

Add imports: `androidx.compose.ui.graphics.Color`,
`androidx.compose.ui.platform.LocalConfiguration`, `androidx.compose.ui.unit.sp`,
`androidx.compose.foundation.layout.height`.

**Remove `.verticalScroll(rememberScrollState())` from the enclosing `Column`.** A
fixed-height `TextField` inside a vertically scrolling parent gives the field unbounded
height and it will not scroll its own content. The attachment row above it is a `LazyRow`
and scrolls horizontally, so nothing else on this screen needs the parent scroll.

- [ ] **Step 3: Restyle the bottom toolbar**

Replace `ComposerBottomBar` entirely:

```kotlin
/**
 * The pinned toolbar: attach, paste, and a note about where the work happens.
 *
 * A 1 dp top border rather than tonal elevation, which is what the reference uses and what
 * keeps the toolbar readable against a `surfaceContainerLow` fill on a dark background —
 * elevation alone is nearly invisible at these tonal steps.
 *
 * The attach button is 56 dp, not 48. It is the only control here that opens something, and
 * §5.5 names that size specifically.
 */
@Composable
private fun ComposerBottomBar(
    onAttachImage: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onAttachImage,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }

                Surface(
                    onClick = onPasteClipboard,
                    modifier = Modifier.height(48.dp),
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Auto-saves · processed on-device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
```

Add imports: `androidx.compose.foundation.layout.navigationBarsPadding`,
`androidx.compose.material.icons.filled.ContentPaste`,
`androidx.compose.material3.HorizontalDivider`, `androidx.compose.ui.text.style.TextAlign`,
`androidx.compose.foundation.layout.Box`.

- [ ] **Step 4: Confirm and look**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

Open the composer beside `capture.html`. Type something and confirm the draft pill switches
to "Draft saved" with a check. Then **leave with the system back gesture** and confirm the
Memory is saved and enqueued — that is the path whose comment warns it was silently broken
once, and this task edited the screen around it.

---

## Task 4: Memory detail

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/feed/MemoryDetailScreen.kt`

**Interfaces:**
- Consumes: existing `MemoryDetailViewModel`, unchanged.
- Produces: no signature change.

This screen is already decomposed into named section composables — `SummarySection`,
`ExtractedMetadataSection`, `RecognizedTextSection`, `ImageDescriptionSection`,
`ContentBlockView`, `StatusNote` — so each is a replaceable unit and the restyle is a series
of local swaps rather than a rewrite. The `Scaffold` and small top bar stay, as
`memory.html` specifies.

- [ ] **Step 1: Read what is there**

```bash
sed -n '38,160p' app/src/main/java/com/onemind/app/ui/feed/MemoryDetailScreen.kt
sed -n '235,275p' app/src/main/java/com/onemind/app/ui/feed/MemoryDetailScreen.kt
```

- [ ] **Step 2: Restyle `SummarySection`**

Replace its body with the reference's summary block — asymmetric 40/16 corners,
`primaryContainer`, a 28 sp Outfit title and a 12 sp attribution line at 70% opacity:

```kotlin
@Composable
private fun SummarySection(memory: Memory) {
    val summary = memory.derived.summary ?: return
    if (summary.status != StageStatus.SUCCESS) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        // 2.5rem everywhere but the top-right, per `memory.html`. The one odd corner is
        // the brand signature and is not a rounding of the others.
        shape = RoundedCornerShape(
            topStart = 40.dp, topEnd = 16.dp, bottomEnd = 40.dp, bottomStart = 40.dp
        ),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = summary.summaryText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            // Names the model and says where it ran. Quieter than the summary because it
            // is provenance, not content — but present, because "on device" is a claim
            // this app makes and should keep visible.
            Text(
                text = buildString {
                    val model = summary.providerModel
                    if (model != null) append("summarised by $model · ") else append("summarised ")
                    append("on device")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
```

Check `MemorySummary`'s field name for the model before relying on `providerModel`:

```bash
grep -n "providerModel" app/src/main/java/com/onemind/app/domain/model/DerivedData.kt
```

If the summary carries no such field, drop the model from the string and keep "summarised
on device" — do not add a field to the model.

- [ ] **Step 3: Restyle the section cards**

`RecognizedTextSection`, `ImageDescriptionSection` and `ExtractedMetadataSection` each wrap
their content in a container. Give all three the same shell:

```kotlin
/**
 * The shared shell for a detail section.
 *
 * One composable rather than the same `Surface` written three times, so the three sections
 * cannot drift apart — which they had already started to, at 12 dp, 16 dp and no corner
 * respectively.
 */
@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                content()
            }
        )
    }
}
```

Wrap each existing section's body in `DetailSection(title = "…") { … }`, removing whatever
container it had. Titles, from `memory.html`: `"Source content"`, `"Text in images"`,
`"Links"`. Add `import androidx.compose.foundation.layout.ColumnScope`.

- [ ] **Step 4: Add the image placeholder**

`memory.html` shows a 256 dp ember panel where a Memory has an image. Insert it in
`MemoryDetailContent`, after the category chips:

```kotlin
            if (memory.imageBlocks().isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(256.dp)
                        // Mirrors the summary block's odd corner on the opposite side, so
                        // the two read as a pair rather than as one styled panel and one
                        // rounded rectangle.
                        .clip(
                            RoundedCornerShape(
                                topStart = 40.dp, topEnd = 40.dp,
                                bottomEnd = 40.dp, bottomStart = 16.dp
                            )
                        )
                        .background(EmberGradient)
                )
            }
```

Add imports: `androidx.compose.foundation.background`, `androidx.compose.ui.draw.clip`,
`com.onemind.app.ui.theme.EmberGradient`.

- [ ] **Step 5: Confirm and look**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug installDebug
```

Open a memory beside `memory.html`. Confirm the retry button still appears for a `FAILED`
Memory and still retries — that behaviour is untouched and must stay so.

---

## Task 5: Onboarding model selection

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/onboarding/ModelSelectionScreen.kt`

**Interfaces:**
- Consumes: existing `OnboardingViewModel`, `ModelInfo`, `LlmCapability`, unchanged.
- Produces: no signature change.

**`ModelCard` becomes a pill row.** `onboarding.html` uses a 32 dp-radius row with a 48 dp
left badge carrying the parameter size, a title and subtitle, and a download icon that
appears only on the selected row. The current card shows three metadata strings in a row
below the name, which the reference folds into one subtitle.

- [ ] **Step 1: Replace `ModelCard`**

```kotlin
/**
 * One model, as a selectable pill row.
 *
 * The size badge on the left is the reference's idea and a good one: parameter count is the
 * single number that decides whether a model will run acceptably on a given phone, and
 * putting it in a fixed 48 dp slot makes six models comparable at a glance in a way three
 * metadata strings per row do not.
 *
 * The download icon appears only on the selected row. On every row it would read as six
 * things to download rather than one choice to confirm — and the sticky CTA below is what
 * actually starts the download.
 */
@Composable
private fun ModelCard(
    model: ModelInfo,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${model.parameterCountB}B",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (isRecommended) {
                        StatusPill(
                            label = "Recommended",
                            container = MaterialTheme.colorScheme.tertiary,
                            content = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
                Text(
                    // One line instead of three separate metadata strings: size, format,
                    // and whether it can see. Everything that changes a decision, nothing
                    // that does not.
                    text = buildString {
                        append("${model.downloadSizeMb} MB · ${model.quantizationFormat}")
                        if (model.capabilities.contains(LlmCapability.VISION)) {
                            append(" · understands images")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
```

Add imports: `androidx.compose.foundation.layout.Box`,
`androidx.compose.foundation.layout.size`, `androidx.compose.material.icons.Icons`,
`androidx.compose.material.icons.filled.Download`, `androidx.compose.material3.Icon`,
`androidx.compose.material3.Surface`, `com.onemind.app.ui.components.StatusPill`. Remove
the `Card`, `SuggestionChip`, `border` and `clickable` imports if nothing else uses them.

- [ ] **Step 2: Hero and sticky CTA**

Read the screen's top and bottom:

```bash
sed -n '18,105p' app/src/main/java/com/onemind/app/ui/onboarding/ModelSelectionScreen.kt
```

Replace whatever top bar it has with `HeroHeader(eyebrow = "Runs on this device", title = "Pick a mind", leading = { back arrow })`,
and make the confirm button a 56 dp-tall pill in `primary` pinned at the bottom with
`navigationBarsPadding()`. If the screen currently has no sticky bottom bar, add one as a
`Column` whose last child is the button, with the list above it taking `weight(1f)` — do
not introduce a `Scaffold` just for this.

- [ ] **Step 3: Confirm and look**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug installDebug
```

Compare against `onboarding.html`. Confirm selecting a model still updates the CTA's label
and that downloading still starts — behaviour is untouched.

---

## Task 6: Settings — Issue J lands here

**Files:**
- Modify: `app/src/main/java/com/onemind/app/ui/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: existing `SettingsViewModel`, `SettingsUiState`, `CloudTestResult`, unchanged.
- Produces: no signature change.

Already decomposed into `CurrentProviderSection`, `LocalModelSection`,
`CloudProviderSection`, `StorageSection`, so this is four local swaps. Behaviour —
including the `Test` button's enablement rules and `Use cloud` requiring a successful test —
is preserved exactly.

- [ ] **Step 1: Restyle `CurrentProviderSection` and `StorageSection`**

Give both the labelled-block shell: an uppercase eyebrow label above a rounded panel.

```kotlin
/**
 * A labelled settings block.
 *
 * The label sits outside the panel, as an eyebrow, rather than inside it as a heading. That
 * is what lets the panel itself be tonal and borderless while the page still reads as a
 * list of named sections.
 */
@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = Tracking.Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}
```

Wrap each of the four sections in `SettingsSection(label = "…") { … }` and delete the
`Text(…, titleMedium)` heading each currently starts with. Labels, from `settings.html`:
`"Active AI provider"`, `"Local model"`, `"Cloud provider"`, `"Storage"`. Space the four
sections 32 dp apart in the enclosing `Column`.

Give `CurrentProviderSection`'s panel the summary-block treatment — 40 dp corners except
16 dp top-right, `primaryContainer`, a `Cpu`-style leading icon, the model id at 16 sp, and
"Cloud provider" beneath at 70% opacity. Give `StorageSection`'s panel
`MaterialTheme.shapes.extraLarge` and `surfaceContainerLow`, and make its delete action a
full-width pill in `surfaceContainerHigh` with `error`-coloured text.

- [ ] **Step 2: Restyle the fields and the vision switch**

In `CloudProviderSection`, give each `OutlinedTextField` the reference's 16 dp shape and
outline colours:

```kotlin
        val fieldShape = MaterialTheme.shapes.medium
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
```

and pass `shape = fieldShape, colors = fieldColors` to all three. Leave
`visualTransformation = PasswordVisualTransformation()` on the API key field exactly as it
is — that is the one thing on this screen that is a security property rather than a style.

Put the vision toggle in its own row panel:

```kotlin
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Supports vision",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.cloudSupportsVision,
                    onCheckedChange = onVisionToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }
```

The reference specifies a 56×32 dp switch. Material's `Switch` has a fixed size and no
public parameter for it; scaling it with `graphicsLayer` would also scale its touch target,
which §5.5 says never to shrink. So the switch keeps Material's dimensions and takes the
reference's colours. **Record this as a deviation rather than working around it.**

Add imports: `androidx.compose.material.icons.filled.Visibility`,
`androidx.compose.material3.OutlinedTextFieldDefaults`,
`androidx.compose.material3.SwitchDefaults`, `androidx.compose.material3.Surface`,
`androidx.compose.material3.Icon`, `androidx.compose.foundation.layout.size`.

- [ ] **Step 3: Make Test / Use cloud a connected pair**

Replace the `Row` holding `OutlinedButton("Test")` and `Button("Use Cloud")` with a
segmented row, keeping **both enablement conditions exactly as they are**:

```kotlin
        val canTest = uiState.cloudBaseUrl.isNotBlank() &&
            uiState.cloudApiKey.isNotBlank() &&
            uiState.cloudModelName.isNotBlank() &&
            uiState.cloudTestResult != CloudTestResult.TESTING
        val canConfirm = uiState.cloudTestResult == CloudTestResult.SUCCESS

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                // Neither is a selection — they are two actions that happen to be
                // connected visually. `selected = false` on both keeps the group from
                // claiming one of them is the current state.
                selected = false,
                onClick = onTestConnection,
                enabled = canTest,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Test") }
            )
            SegmentedButton(
                selected = false,
                onClick = onConfirm,
                enabled = canConfirm,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Use cloud") }
            )
        }

        // Kept as its own row below the group. Inside it, a "Failed" label would sit
        // where a third action goes and read as one.
        when (uiState.cloudTestResult) {
            CloudTestResult.TESTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            CloudTestResult.SUCCESS -> Text(
                text = "Connected",
                style = MaterialTheme.typography.bodySmall,
                color = OneMindSuccess
            )
            CloudTestResult.FAILED -> Text(
                text = "Failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            null -> Unit
        }
```

Add imports: `androidx.compose.material3.SegmentedButton`,
`androidx.compose.material3.SegmentedButtonDefaults`,
`androidx.compose.material3.SingleChoiceSegmentedButtonRow`,
`com.onemind.app.ui.theme.OneMindSuccess`.

- [ ] **Step 4: Hero**

Replace the screen's top bar with
`HeroHeader(eyebrow = "oneMind", title = "Settings", leading = { back arrow })`, wrap the
content in `PhoneFrame`, and keep the sections in a `Column` with
`verticalScroll(rememberScrollState())` and `navigationBarsPadding()`.

- [ ] **Step 5: Verify everything**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew connectedDebugAndroidTest
```

Expected: BUILD SUCCESSFUL, full suite green.

- [ ] **Step 6: Exercise the four screens on device**

```bash
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

This commit touches four screens and adds no tests, so the manual pass is the coverage.
Compare each against its mock, and check the behaviour each screen owns:

- **Composer** — type, watch the pill switch to "Draft saved", leave with the **system back
  gesture**, confirm the Memory is saved and enqueued.
- **Memory detail** — a `FAILED` Memory still offers Retry and retrying still works.
- **Onboarding** — selecting a model still updates the CTA and downloading still starts.
- **Settings** — `Test` stays disabled until all three fields are filled; `Use cloud` stays
  disabled until a test succeeds; the API key is still masked.

- [ ] **Step 7: File Issue J, commit, close**

File with title **"Composer, memory detail, onboarding, settings restyle"** and a body
covering: four screens brought onto the theme with no behaviour change; the composer's 52 vh
Outfit field and pinned toolbar, with a note that `BackHandler` and `onLeaveComposer` were
not touched because their comment records a defect that lost typed content; the summary
block and section shell on memory detail; model rows becoming size-badged pills; settings
gaining labelled sections, a coloured switch and a connected Test/Use cloud pair with both
enablement rules preserved; and the one deviation — Material's `Switch` keeps its own
dimensions rather than the reference's 56×32, because scaling it would shrink the touch
target that §5.5 says never to shrink.

Commit:

```bash
git add app/src/main/java/com/onemind/app/ui/composer/ComposerScreen.kt \
        app/src/main/java/com/onemind/app/ui/feed/MemoryDetailScreen.kt \
        app/src/main/java/com/onemind/app/ui/onboarding/ModelSelectionScreen.kt \
        app/src/main/java/com/onemind/app/ui/settings/SettingsScreen.kt

git commit -F - <<'MSG'
feat(ui): restyle composer, memory detail, onboarding and settings (#J)

Four screens onto the expressive theme. No view model, repository or DAO
touched, and no behaviour changed — each screen already received the state it
needed.

Composer keeps its Scaffold: it has a toolbar that must stay pinned above the
keyboard and the navigation bar, which is what bottomBar is for, and capture
.html specifies a small top bar rather than a hero. The field becomes 52 vh of
Outfit at 32 sp, and the draft pill is present in both states rather than fading
in — a pill that appears from nowhere reads as an alert, and what it reports is
that nothing has gone wrong. The parent verticalScroll had to go: a
fixed-height TextField inside a scrolling parent gets unbounded height and
stops scrolling its own content.

BackHandler and onLeaveComposer are untouched. Their comment records a defect
where anything typed inside the autosave window was lost outright and the
Memory stayed in DRAFT, never enqueued and never searchable. Verified by hand
that leaving with the system gesture still saves.

Memory detail gains the summary block on its asymmetric 40/16 corners and one
shared DetailSection shell for its three sections, which had already drifted to
12 dp, 16 dp and no corner respectively.

Onboarding model rows become pills with a 48 dp size badge. Parameter count is
the one number that decides whether a model will run acceptably on a given
phone, and a fixed slot makes six of them comparable at a glance in a way three
metadata strings per row does not. The download icon shows only on the selected
row, or six rows read as six things to download.

Settings gains labelled sections, brand switch colours, and a connected
Test / Use cloud pair — both with selected = false, since they are two actions
rather than a choice, and with their enablement rules carried over exactly. The
API key stays masked. The test-result label stays outside the group, where a
"Failed" inside it would sit where a third action goes.

One deviation: Material's Switch keeps its own dimensions rather than the
reference's 56x32. It has no size parameter, and scaling it with graphicsLayer
would scale the touch target §5.5 says never to shrink. It takes the
reference's colours instead.
MSG
```

---

## Done when

- [ ] Issues I and J filed, implemented, closed with AI-attributed comments.
- [ ] Two commits on `my-extra-work`, `(#N)` in each subject, no `Co-Authored-By` and no
      generated-by trailer.
- [ ] `assembleDebug`, `testDebugUnitTest` and the full instrumented suite green on the
      `onemind_test` AVD, with `EventsScreenTest` at 9 tests.
- [ ] All eight screens compared against their `*.html` counterparts on device.
- [ ] The four behaviours in Task 6 Step 6 checked by hand and reported truthfully.
- [ ] `git diff --stat` shows nothing under `domain/`, `data/` or `capture/`, and no
      ViewModel modified.
- [ ] Nothing released.

## Deviations recorded deliberately

- **Only the model-picker surface of onboarding is restyled.** `onboarding.html` is the
  model picker, and the design spec's screens table scopes onboarding to "model pill rows
  with size badges, sticky CTA". `WelcomeScreen`, `PermissionsScreen`, `CloudConfigScreen`
  and `DownloadScreen` have no counterpart in the reference, so they pick up the palette and
  typography from the theme work and keep their current layouts. They will look themed but
  not redesigned. That is the spec's scope, not an oversight — but it is visible, so it is
  written down rather than left to be discovered.
- **The vision switch is Material's size, not 56×32.** `Switch` exposes no dimension
  parameter, and a `graphicsLayer` scale would shrink the touch target §5.5 says never to
  shrink. Colours match; geometry does not.
- **Composer and memory detail keep their `Scaffold`.** Both have a small top bar in the
  reference rather than a hero, and the composer needs `bottomBar` for its pinned toolbar.
  Replacing them with `PhoneFrame` would mean reimplementing inset handling that works.
- **No new tests in Issue J.** Four restyles with no behaviour change, and the assertions
  that would be worth writing — corner radii, font faces — are the constants they would be
  compared against. The manual pass is the coverage, and it is listed step by step rather
  than waved at.
- **The status-bar-inset test moved rather than being deleted.** `HeroHeader` replaced the
  `TopAppBar` that fixed #37, so the guard follows the mechanism.
