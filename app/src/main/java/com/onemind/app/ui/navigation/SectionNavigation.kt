package com.onemind.app.ui.navigation

import androidx.navigation.NavHostController
import com.onemind.app.ui.components.SectionDestination

/**
 * Which route a segmented-group destination points at.
 *
 * Kept here rather than on `SectionDestination` deliberately: that enum is a presentation
 * component and knows only its own labels, so it can be rendered — and tested — without
 * a navigation graph existing at all.
 */
fun SectionDestination.route(): String = when (this) {
    SectionDestination.FEED -> NavRoutes.FEED
    SectionDestination.TIMELINE -> NavRoutes.TIMELINE
    SectionDestination.EVENTS -> NavRoutes.EVENTS
}

/**
 * Move between the three peer destinations without stacking them.
 *
 * A plain `navigate` would push, so Feed → Timeline → Events → Feed would leave four
 * entries on the back stack and four presses of back to leave. `popUpTo(FEED)` makes the
 * feed the group's floor and `launchSingleTop` stops a destination being pushed onto
 * itself, so back always goes to the feed and then out — which is what a tab-like group
 * has to do to feel like one.
 *
 * `saveState` and `restoreState` keep each destination's scroll position across a swap.
 * Without them, returning to a feed the user had scrolled halfway down snaps it to the top,
 * which reads as the app having reloaded.
 */
fun NavHostController.navigateToSection(destination: SectionDestination) {
    navigate(destination.route()) {
        popUpTo(NavRoutes.FEED) {
            inclusive = false
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
