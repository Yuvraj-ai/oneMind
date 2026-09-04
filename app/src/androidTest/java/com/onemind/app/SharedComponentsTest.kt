package com.onemind.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.components.SectionNav
import com.onemind.app.ui.components.StateChip
import com.onemind.app.ui.theme.OneMindTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shared components' behaviour, not their looks.
 *
 * Visual fidelity is checked by comparison against the reference HTML — a test cannot
 * tell you a corner is 32 dp rather than 24 dp in a way that is cheaper than looking.
 * What a test *can* pin is the part that carries meaning: which label a state gets, and
 * that the frame really does cap its content.
 */
@RunWith(AndroidJUnit4::class)
class SharedComponentsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Height of the status bar, in dp, as the window actually reports it.
     *
     * Same shape as `EventsScreenTest`'s helper, deliberately: the two tests are pinning
     * the same class of bug and diverging on how they measure it would only invite one of
     * them to drift.
     */
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

    /** Render edge to edge, the way `MainActivity` does, so the insets exist at all. */
    private fun renderEdgeToEdge(content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.activity.runOnUiThread {
            composeRule.activity.enableEdgeToEdge()
        }
        composeRule.setContent { OneMindTheme(content = content) }
        composeRule.waitForIdle()
    }

    @Test
    fun everyProcessingStateGetsItsOwnLabel() {
        composeRule.setContent {
            OneMindTheme {
                Column {
                    ProcessingState.entries.forEach { StateChip(state = it) }
                }
            }
        }

        // One chip per state, each saying something different. A `when` that fell through
        // to a shared default would render six identical chips and look fine.
        val labels = listOf("Draft", "Saved", "Processing", "Ready", "Edited", "Failed")
        labels.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun theFrameCapsItsContentAtTheReferenceWidth() {
        composeRule.setContent {
            OneMindTheme {
                // 800 dp regardless of the device, so the cap is exercised on a phone
                // emulator whose screen is narrower than 440 dp and would otherwise make
                // this pass without the frame doing anything.
                Box(modifier = Modifier.requiredWidth(800.dp)) {
                    PhoneFrame {
                        Box(
                            modifier = Modifier
                                .testTag("framed")
                                .fillMaxWidth()
                                .height(40.dp)
                        )
                    }
                }
            }
        }

        val width = composeRule.onNodeWithTag("framed").getBoundsInRoot().width
        assertTrue(
            "framed content is ${width.value}dp wide; the reference frame is 440dp",
            width.value <= 440f + 0.5f
        )
        // And not collapsed to nothing, which would also satisfy the bound above.
        assertTrue("framed content collapsed to ${width.value}dp", width.value > 100f)
    }

    @Test
    fun theHeroShowsItsEyebrowAndTitle() {
        composeRule.setContent {
            OneMindTheme {
                HeroHeader(eyebrow = "Your mind", title = "Everything you saved")
            }
        }

        // "YOUR MIND", not "Your mind": HeroHeader uppercases the eyebrow.
        composeRule.onNodeWithText("YOUR MIND").assertIsDisplayed()
        composeRule.onNodeWithText("Everything you saved").assertIsDisplayed()
    }

    @Test
    fun theHeroKeepsItsTitleOutFromUnderTheStatusBar() {
        // The header replaces the Scaffold + TopAppBar that used to consume this inset.
        // MainActivity calls enableEdgeToEdge(), so nothing else will. EventsScreen
        // shipped without it once (#37) and drew its first row across the system clock;
        // four screens now depend on this one composable getting it right.
        renderEdgeToEdge {
            HeroHeader(eyebrow = "Your mind", title = "Everything you saved")
        }

        val statusBarDp = statusBarHeightDp()
        assertTrue(
            "This device reports no status bar inset, so the overlap cannot be observed " +
                "and this test would pass for the wrong reason",
            statusBarDp > 0f
        )
        val eyebrowTop = composeRule.onNodeWithText("YOUR MIND").getBoundsInRoot().top
        assertTrue(
            "eyebrow starts at ${eyebrowTop.value}dp, inside the ${statusBarDp}dp status bar",
            eyebrowTop.value >= statusBarDp
        )
    }

    @Test
    fun theSectionNavReportsWhatWasTapped() {
        var selected = SectionDestination.FEED
        composeRule.setContent {
            OneMindTheme {
                SectionNav(selected = SectionDestination.FEED, onSelect = { selected = it })
            }
        }

        composeRule.onNodeWithText("Timeline").performClick()
        composeRule.waitForIdle()

        assertEquals(SectionDestination.TIMELINE, selected)
    }
}
