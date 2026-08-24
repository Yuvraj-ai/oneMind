package com.onemind.app

import com.onemind.app.capture.ShadeTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision logic behind shade-safe capture, tested away from the device.
 *
 * The service itself cannot be unit tested — `performGlobalAction` and
 * `takeScreenshot` need a real display. Everything that decides *when* to capture
 * lives here precisely so it can be, because the bug this fixes was a wrong guess
 * about timing and a wrong guess is exactly what a test pins down.
 */
class ShadeTrackerTest {

    private val tracker = ShadeTracker(ownPackage = "com.onemind.app")

    @Test
    fun `a normal app becomes the last app package`() {
        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals("com.android.chrome", tracker.lastAppPackage)
        assertFalse(tracker.isShadeInFront)
    }

    @Test
    fun `system ui never becomes the last app package`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        // This is the second defect: the shade opening used to overwrite the real
        // foreground app, so every capture was tagged com.android.systemui.
        assertEquals("com.android.chrome", tracker.lastAppPackage)
        assertTrue(tracker.isShadeInFront)
    }

    @Test
    fun `our own package never becomes the last app package`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onWindowStateChanged("com.onemind.app")

        assertEquals("com.android.chrome", tracker.lastAppPackage)
        assertFalse(tracker.isShadeInFront)
    }

    @Test
    fun `last app package is null before anything is seen`() {
        assertNull(tracker.lastAppPackage)
        assertFalse(tracker.isShadeInFront)
    }

    @Test
    fun `awaiting fires immediately when the shade is not in front`() {
        tracker.onWindowStateChanged("com.android.chrome")

        var fired = 0
        val firedSynchronously = tracker.awaitShadeGone { fired++ }

        assertTrue(firedSynchronously)
        assertEquals(1, fired)
    }

    @Test
    fun `awaiting waits when the shade is in front, then fires on the next app window`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        var fired = 0
        val firedSynchronously = tracker.awaitShadeGone { fired++ }

        assertFalse(firedSynchronously)
        assertEquals(0, fired)

        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals(1, fired)
        assertFalse(tracker.isShadeInFront)
    }

    @Test
    fun `a second system ui window while waiting does not fire the callback`() {
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        var fired = 0
        tracker.awaitShadeGone { fired++ }
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        assertEquals(0, fired)
    }

    @Test
    fun `the timeout fires an armed callback`() {
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        var fired = 0
        tracker.awaitShadeGone { fired++ }
        tracker.onTimeout()

        assertEquals(1, fired)
    }

    @Test
    fun `the callback fires exactly once when the window changes and then the timeout lands`() {
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        var fired = 0
        tracker.awaitShadeGone { fired++ }
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onTimeout()

        // Two Memories for one tile tap is the failure this guards.
        assertEquals(1, fired)
    }

    @Test
    fun `the callback fires exactly once when the timeout lands and then the window changes`() {
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        var fired = 0
        tracker.awaitShadeGone { fired++ }
        tracker.onTimeout()
        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals(1, fired)
    }

    @Test
    fun `a timeout with nothing armed does nothing`() {
        tracker.onTimeout()
        // No exception, no state change. A stale timeout from a previous tap must
        // not fire the next tap's capture.
        assertNull(tracker.lastAppPackage)
    }

    @Test
    fun `a second capture can arm the latch again after the first completed`() {
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)
        var first = 0
        tracker.awaitShadeGone { first++ }
        tracker.onWindowStateChanged("com.android.chrome")
        assertEquals(1, first)

        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)
        var second = 0
        tracker.awaitShadeGone { second++ }
        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals(1, second)
    }
}
