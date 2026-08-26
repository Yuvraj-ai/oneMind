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
 *
 * What a test could not pin down was the *platform* guess underneath it: that the
 * shade's arrival is a `TYPE_WINDOW_STATE_CHANGED`. It is not, so the tracker's
 * shade-detection was permanently false and the dismissal it gated was never
 * requested. That is gone — the shade is now assumed open, because a Quick Settings
 * tile cannot be reached any other way — and these tests cover the contract that
 * replaced it.
 */
class ShadeTrackerTest {

    private val tracker = ShadeTracker(ownPackage = "com.onemind.app")

    // ---------------------------------------------------------------------
    // Source app attribution.
    // ---------------------------------------------------------------------

    @Test
    fun `a normal app becomes the last app package`() {
        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals("com.android.chrome", tracker.lastAppPackage)
    }

    @Test
    fun `system ui never becomes the last app package`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        // The shade does emit the occasional window-state change, and before this
        // class existed it overwrote the real foreground app, so every tile capture
        // was tagged com.android.systemui.
        assertEquals("com.android.chrome", tracker.lastAppPackage)
    }

    @Test
    fun `our own package never becomes the last app package`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onWindowStateChanged("com.onemind.app")

        assertEquals("com.android.chrome", tracker.lastAppPackage)
    }

    @Test
    fun `last app package is null before anything is seen`() {
        assertNull(tracker.lastAppPackage)
    }

    // ---------------------------------------------------------------------
    // Arming and firing.
    // ---------------------------------------------------------------------

    @Test
    fun `arming never fires synchronously`() {
        tracker.onWindowStateChanged("com.android.chrome")

        var fired = 0
        tracker.armCapture { fired++ }

        // The shade is assumed open, so there is no fast path to short-circuit on.
        // Capturing here is capturing the shade — the bug.
        assertEquals(0, fired)
        assertTrue(tracker.isCapturePending)
    }

    @Test
    fun `an armed capture fires on the next app window`() {
        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals(1, fired)
        assertFalse(tracker.isCapturePending)
    }

    @Test
    fun `a system ui window while waiting does not fire the callback`() {
        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        // The shade coming back into focus is not the shade leaving.
        assertEquals(0, fired)
        assertTrue(tracker.isCapturePending)
    }

    @Test
    fun `our own window while waiting does not fire the callback`() {
        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onWindowStateChanged("com.onemind.app")

        assertEquals(0, fired)
    }

    @Test
    fun `the timeout fires an armed callback`() {
        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onTimeout()

        assertEquals(1, fired)
    }

    @Test
    fun `the callback fires exactly once when the window changes and then the timeout lands`() {
        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onTimeout()

        // Two Memories for one tile tap is the failure this guards.
        assertEquals(1, fired)
    }

    @Test
    fun `the callback fires exactly once when the timeout lands and then the window changes`() {
        var fired = 0
        tracker.armCapture { fired++ }

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
        assertFalse(tracker.isCapturePending)
    }

    @Test
    fun `a second capture can arm the latch again after the first completed`() {
        var first = 0
        tracker.armCapture { first++ }
        tracker.onWindowStateChanged("com.android.chrome")
        assertEquals(1, first)

        var second = 0
        tracker.armCapture { second++ }
        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals(1, second)
    }

    // ---------------------------------------------------------------------
    // Settle delay.
    //
    // The window-state change means focus left the shade, not that the shade
    // has finished painting. Measured on API 36: the event lands ~240ms after
    // dismissal is requested, the collapse animation is not visually done
    // until ~600-700ms, and the frame captured at ~240ms is the shade
    // mid-fade.
    // ---------------------------------------------------------------------

    @Test
    fun `the settle delay is the documented value`() {
        assertEquals(ShadeTracker.SHADE_SETTLE_DELAY_MS, tracker.settleDelayMs())
    }

    @Test
    fun `the settle delay does not depend on which window fired the capture`() {
        var onSignal = -1L
        tracker.armCapture { onSignal = tracker.settleDelayMs() }
        tracker.onWindowStateChanged("com.android.chrome")

        var onTimeout = -1L
        tracker.armCapture { onTimeout = tracker.settleDelayMs() }
        tracker.onTimeout()

        // A timeout means the shade may still be closing, not that it isn't, so the
        // backstop path must not capture any sooner than the signal path.
        assertEquals(ShadeTracker.SHADE_SETTLE_DELAY_MS, onSignal)
        assertEquals(ShadeTracker.SHADE_SETTLE_DELAY_MS, onTimeout)
    }

    @Test
    fun `the settle delay is never zero`() {
        // An earlier version returned zero whenever it believed no shade was
        // involved, and it believed that every time, which is how the shade kept
        // ending up in the screenshot.
        assertTrue(tracker.settleDelayMs() > 0L)
    }

    @Test
    fun `the settle delay covers the gap between the focus signal and the settled frame`() {
        // Not a tautology: this pins the constant against the two measurements
        // that justify it, so shrinking it below the observed animation tail
        // fails here rather than on a device.
        val signalMs = 240L
        val animationDoneMs = 600L

        assertTrue(signalMs + ShadeTracker.SHADE_SETTLE_DELAY_MS >= animationDoneMs)
    }
}
