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
import com.onemind.app.ui.components.SectionDestination
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
 * That the Events screen stays out from under the system status bar, can be left, and
 * offers the section group — plus the four card states' actions.
 *
 * `MainActivity` calls `enableEdgeToEdge()`, so every destination owns its own window
 * insets. `EventsScreen` shipped in 3f6f0a8 with no top bar and 16.dp of content padding —
 * less than the status bar — so its first row drew on top of the system clock, and it had
 * no back affordance at all. #37 fixed both with a `Scaffold` + `TopAppBar`.
 *
 * The redesign (#I) removed that Scaffold: `PhoneFrame` + `HeroHeader` consume the inset
 * now, and the back arrow lives in the hero's `leading` slot. The status-bar assertion
 * therefore follows the mechanism onto the hero title rather than being retired with the
 * header it was written against — retiring the guard with the thing it guards is how a
 * fixed defect comes back.
 *
 * The project's first Compose UI test. The bug was originally found by rendering the screen
 * and looking at it; this is that observation made repeatable.
 */
@RunWith(AndroidJUnit4::class)
class EventsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var repository: FakeEventRepository
    private var backPresses = 0
    private var lastSection: SectionDestination? = null

    @Before
    fun setup() {
        repository = FakeEventRepository()
        backPresses = 0
        lastSection = null
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
        // Built here rather than inside setContent. A ViewModel constructed in a
        // composable is rebuilt on every recomposition, and lint's
        // ViewModelConstructorInComposable says so — as an error, which failed
        // lintDebug from the moment this file was added in #37.
        //
        // Relaxed mock for the MemoryRepository: this screen's Memory-side data is the
        // location line and the category chips, and neither is what these tests are
        // about. An empty map and an empty list are exactly the "Memory named no place
        // and had no categories" case, which must render.
        val viewModel = EventsViewModel(repository, mockk(relaxed = true), Clock.systemUTC())
        composeRule.setContent {
            OneMindTheme {
                EventsScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    onNavigateToSection = { lastSection = it },
                    viewModel = viewModel
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
        // clock, and the mechanism that prevented it has been replaced — so the assertion
        // moves to the new first row rather than being retired with the old one.
        val heroTop = composeRule.onNodeWithText("Things coming up").getBoundsInRoot().top
        assertTrue(
            "\"Things coming up\" starts at ${heroTop.value}dp, inside the " +
                "${statusBar}dp status bar — it is drawing over the system clock",
            heroTop.value >= statusBar
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

        // "Events" used to be the top bar's title. It is now the segmented group's third
        // segment, which happens to render the same string — so asserting on it would keep
        // passing while meaning something else entirely. Assert the hero and the empty
        // state, which are what this screen owes a user who has saved nothing yet.
        composeRule.onNodeWithText("Things coming up").assertIsDisplayed()
        composeRule.onNodeWithText("No upcoming events").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backPresses)
    }

    @Test
    fun theSectionGroupIsOfferedAlongsideTheWayBack() {
        repository.emitUpcoming(listOf(event("Dentist on Thursday")))
        renderScreen()

        composeRule.onNodeWithText("Feed").performClick()
        composeRule.waitForIdle()
        assertEquals(SectionDestination.FEED, lastSection)

        // And the arrow is still there. The group moves between peers; it does not leave,
        // and #37 was this screen having no way out.
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
