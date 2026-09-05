package com.onemind.app

import com.onemind.app.ui.components.SectionDestination
import com.onemind.app.ui.navigation.NavRoutes
import com.onemind.app.ui.navigation.route
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * That every segmented-group destination points at the route it claims to.
 *
 * Small, and deliberately so. What is actually worth testing about this group is that the
 * three peers *swap* rather than stack — a plain `navigate` would leave four entries and
 * four presses of back to get out of something that looks like tabs. Asserting back-stack
 * depth needs a real graph with real screens in it, which needs a Hilt test runner this
 * project does not have, so that behaviour is a recorded manual check on device instead of
 * a test that only appears to cover it.
 *
 * What is left still earns its place: the mapping is three lines of `when`, and getting one
 * wrong would send a tap to the wrong screen with nothing failing.
 *
 * A JVM test rather than the instrumented one the plan specified. `TestNavHostController`
 * needed `androidx.navigation:navigation-testing`, which is not on the classpath, and the
 * plan's own guidance was to drop it rather than add a dependency for an unused field —
 * which leaves nothing here that touches Android.
 */
class SectionNavigationTest {

    @Test
    fun eachDestinationMapsToItsRoute() {
        assertEquals(NavRoutes.FEED, SectionDestination.FEED.route())
        assertEquals(NavRoutes.TIMELINE, SectionDestination.TIMELINE.route())
        assertEquals(NavRoutes.EVENTS, SectionDestination.EVENTS.route())
    }

    @Test
    fun theThreeRoutesAreDistinct() {
        // The `when` is exhaustive, so a copy-paste that pointed two destinations at one
        // route would compile and would silently make one of them unreachable.
        val routes = SectionDestination.entries.map { it.route() }

        assertEquals(routes.size, routes.toSet().size)
    }
}
