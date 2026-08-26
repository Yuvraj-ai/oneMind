package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.domain.model.DetectedEvent
import com.onemind.app.domain.model.EventStatus
import com.onemind.app.domain.repository.EventRepository
import com.onemind.app.ui.events.EventsScreen
import com.onemind.app.ui.events.EventsViewModel
import com.onemind.app.ui.theme.OneMindTheme
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * That the Events screen stays out from under the system status bar, and can be
 * left.
 *
 * `MainActivity` calls `enableEdgeToEdge()`, so every destination owns its own
 * window insets. Every other screen gets that for free from a `Scaffold` with a
 * `TopAppBar`, which consumes the status bar inset. `EventsScreen` shipped in
 * 3f6f0a8 as a bare `LazyColumn` with 16.dp of content padding — less than the
 * status bar — so its "Upcoming" header drew on top of the system clock.
 *
 * The same omission left it with no back affordance, alone among pushed
 * destinations. One `Scaffold` fixes both, which is why both are pinned here.
 *
 * The project's first Compose UI test. The bug was originally found by rendering
 * the screen and looking at it; this is that observation made repeatable.
 */
@RunWith(AndroidJUnit4::class)
class EventsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repository: FakeEventRepository
    private var backPresses = 0

    @Before
    fun setup() {
        repository = FakeEventRepository()
        backPresses = 0
    }

    /**
     * Render the screen the way the app does: edge to edge, so the insets the
     * screen is meant to handle actually exist. Without this the window would fit
     * system windows for us and the overlap could not reproduce.
     */
    private fun renderScreen() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
        }
        composeRule.setContent {
            OneMindTheme {
                EventsScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    // Relaxed: this screen's Memory-side data is the location line and
                    // the category chips, and neither is what these tests are about.
                    // An empty map and an empty list are exactly the "Memory named no
                    // place and had no categories" case, which must render.
                    viewModel = EventsViewModel(repository, mockk(relaxed = true), Clock.systemUTC())
                )
            }
        }
        composeRule.waitForIdle()
    }

    /** Height of the status bar, in dp, as the window actually reports it. */
    private fun statusBarHeightDp(): Float {
        var px = 0
        composeRule.activity.runOnUiThread {
            px = ViewCompat.getRootWindowInsets(composeRule.activity.window.decorView)
                ?.getInsets(WindowInsetsCompat.Type.statusBars())
                ?.top ?: 0
        }
        composeRule.waitForIdle()
        return px / composeRule.activity.resources.displayMetrics.density
    }

    @Test
    fun theUpcomingHeaderDoesNotDrawUnderTheStatusBar() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))

        renderScreen()

        val statusBar = statusBarHeightDp()
        assertTrue(
            "This device reports no status bar inset, so the overlap cannot be " +
                "observed and this test would pass for the wrong reason",
            statusBar > 0f
        )

        val headerTop = composeRule.onNodeWithText("Upcoming").getBoundsInRoot().top
        assertTrue(
            "\"Upcoming\" starts at ${headerTop.value}dp, inside the " +
                "${statusBar}dp status bar — it is drawing over the system clock",
            headerTop.value >= statusBar
        )
    }

    @Test
    fun theScreenCanBeLeft() {
        // Alone among pushed destinations, this screen shipped with no way back
        // except the system gesture.
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))

        renderScreen()
        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backPresses)
    }

    @Test
    fun theEmptyStateAlsoClearsTheStatusBar() {
        // The empty state is a centred Box, so it never overlapped. What it can lose
        // is the top bar, and with it the way back — which would strand a user who
        // opened Events before saving anything with a date.
        renderScreen()

        composeRule.onNodeWithText("Events").assertIsDisplayed()
        composeRule.onNodeWithText("No upcoming events").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backPresses)
    }

    @Test
    fun eventsAreListedUnderTheirHeadings() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        repository.emitExpired(
            listOf(event("Concert last week", at = Instant.now().minus(Duration.ofDays(7))))
        )

        renderScreen()

        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Dentist on Thursday").assertIsDisplayed()
        composeRule.onNodeWithText("Expired & rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Concert last week").assertIsDisplayed()
    }

    @Test
    fun rejectingAnEventMovesItToThePastList() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        renderScreen()

        composeRule.onNodeWithContentDescription("Reject").performClick()
        composeRule.waitForIdle()

        // Not deleted — rejecting is reversible, and a row nothing renders cannot be
        // undone.
        composeRule.onNodeWithText("Expired & rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Rejected").assertIsDisplayed()
        composeRule.onNodeWithText("Dentist on Thursday").assertIsDisplayed()
    }

    @Test
    fun aRejectedEventCanBeUndone() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        renderScreen()
        composeRule.onNodeWithContentDescription("Reject").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reject").assertIsDisplayed()
    }

    @Test
    fun anEventAddedToTheCalendarStaysUpcomingAndSaysSo() {
        repository.emitUpcoming(
            listOf(event("AI Summit", status = EventStatus.IN_CALENDAR))
        )
        renderScreen()

        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("In calendar").assertIsDisplayed()
        // Its only remaining transition is expiry, so neither action is offered.
        composeRule.onNodeWithContentDescription("Add to calendar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reject").assertDoesNotExist()
    }

    @Test
    fun anExpiredEventOffersNoActions() {
        repository.emitExpired(
            listOf(event("Concert last week", at = Instant.now().minus(Duration.ofDays(7))))
        )
        renderScreen()

        composeRule.onNodeWithText("Concert last week").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Add to calendar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Reject").assertDoesNotExist()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
    }

    private fun event(
        title: String,
        at: Instant = Instant.now().plus(Duration.ofDays(3)),
        status: EventStatus = EventStatus.UPCOMING
    ) = DetectedEvent(
        id = title.hashCode().toLong(),
        memoryId = 1L,
        eventTime = at,
        eventTitle = title,
        status = status
    )

    /**
     * Enough of an [EventRepository] to render against, and to watch change.
     *
     * Hand-rolled rather than Room-backed: what is under test is the screen, and a
     * real database would only add ways for it to fail for reasons that have nothing
     * to do with layout. It holds one list and derives the two the screen reads,
     * because the status transitions are what the action tests are about — two fixed
     * lists could not express an event moving between them.
     */
    private class FakeEventRepository : EventRepository {
        private val all = MutableStateFlow<List<DetectedEvent>>(emptyList())

        fun emitUpcoming(events: List<DetectedEvent>) = merge(events)

        fun emitExpired(events: List<DetectedEvent>) =
            merge(events.map { it.copy(status = EventStatus.EXPIRED) })

        private fun merge(events: List<DetectedEvent>) {
            all.value = all.value.filterNot { row -> events.any { it.id == row.id } } + events
        }

        private fun setStatus(eventId: Long, status: EventStatus) {
            all.value = all.value.map { if (it.id == eventId) it.copy(status = status) else it }
        }

        override suspend fun replaceEventsForMemory(memoryId: Long, events: List<DetectedEvent>) = Unit

        override fun observeUpcoming(): Flow<List<DetectedEvent>> = all.map { list ->
            list.filter {
                it.status == EventStatus.UPCOMING || it.status == EventStatus.IN_CALENDAR
            }.sortedBy { it.eventTime }
        }

        override fun observeExpired(): Flow<List<DetectedEvent>> = all.map { list ->
            list.filter {
                it.status == EventStatus.EXPIRED || it.status == EventStatus.REJECTED
            }.sortedByDescending { it.eventTime }
        }

        override suspend fun expireOverdue(now: Instant): Int = 0
        override suspend fun reject(eventId: Long) = setStatus(eventId, EventStatus.REJECTED)
        override suspend fun undoReject(eventId: Long) = setStatus(eventId, EventStatus.UPCOMING)
        override suspend fun markAddedToCalendar(eventId: Long) =
            setStatus(eventId, EventStatus.IN_CALENDAR)
    }
}
