package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.domain.search.SearchOrchestrator
import com.onemind.app.ui.search.SearchScreen
import com.onemind.app.ui.search.SearchViewModel
import com.onemind.app.ui.theme.OneMindTheme
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three things the search screen has to get right: it starts by suggesting rather
 * than by reporting nothing found, it can be left, and a query that finds nothing says so
 * without claiming the user's memories are missing.
 *
 * The third is the one with history. `isActive` keys on whether a query could be *built*,
 * not on whether text was typed, because `FtsQuery.build` returns null for a single
 * character or an all-stopword query — and keying on raw text made typing "a" announce
 * "No memories found".
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backPresses = 0

    private fun render(orchestrator: SearchOrchestrator) {
        backPresses = 0
        // Built outside setContent, as EventsScreenTest does: a ViewModel constructed in a
        // composable is rebuilt on every recomposition, and lint's
        // ViewModelConstructorInComposable fails the build over it.
        val viewModel = SearchViewModel(orchestrator)
        composeRule.setContent {
            OneMindTheme {
                SearchScreen(
                    onNavigateToMemory = {},
                    onNavigateBack = { backPresses++ },
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun anUntouchedScreenSuggestsRatherThanReportingNothing() {
        render(mockk(relaxed = true))

        composeRule.onNodeWithText("Try asking").assertIsDisplayed()
        // The failure this guards: an empty query is not a failed search.
        composeRule.onNodeWithText("No memories matched").assertDoesNotExist()
    }

    @Test
    fun theScreenCanBeLeft() {
        render(mockk(relaxed = true))

        composeRule.onNodeWithContentDescription("Back").performClick()

        assertEquals(1, backPresses)
    }

    @Test
    fun aSingleCharacterIsNotAFailedSearch() {
        val orchestrator = mockk<SearchOrchestrator>(relaxed = true)
        coEvery { orchestrator.search(any(), any()) } returns emptyList()
        render(orchestrator)

        composeRule.onNodeWithText("Ask in your own words…").performTextInput("a")
        composeRule.waitForIdle()

        // FtsQuery.build returns null for one character, so nothing was searched for and
        // nothing should be reported as missing.
        composeRule.onNodeWithText("No memories matched").assertDoesNotExist()
        composeRule.onNodeWithText("Try asking").assertIsDisplayed()
    }
}
