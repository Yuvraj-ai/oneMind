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
    fun `an armed capture fires on the first app window when nothing was in front`() {
        // Cold service, or a tap before any app window was seen. There is nothing
        // better to wait for than the first window the collapse reveals.
        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onWindowStateChanged("com.android.chrome")

        assertEquals(1, fired)
        assertEquals("com.android.chrome", tracker.capturedAppPackage)
    }

    @Test
    fun `an unrelated app taking focus mid-collapse does not fire the capture`() {
        tracker.onWindowStateChanged("com.android.chrome")

        var fired = 0
        tracker.armCapture { fired++ }
        tracker.onWindowStateChanged("com.android.dialer")

        // An incoming call is not the shade leaving. Firing here would grab a frame of
        // the shade still collapsing over a screen the user never asked to capture.
        assertEquals(0, fired)
        assertTrue(tracker.isCapturePending)
    }

    @Test
    fun `an unrelated app taking focus does not become the capture's source app`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.armCapture { }

        tracker.onWindowStateChanged("com.android.dialer")
        tracker.onTimeout()

        // This is the #39 attribution defect in a new disguise: the Memory would
        // record the app that interrupted, not the app the user was capturing.
        assertEquals("com.android.chrome", tracker.capturedAppPackage)
    }

    @Test
    fun `the capture's source app is the app that was in front when it was armed`() {
        tracker.onWindowStateChanged("com.android.chrome")
        tracker.armCapture { }

        assertEquals("com.android.chrome", tracker.capturedAppPackage)
    }

    @Test
    fun `a system ui window while waiting does not fire the callback`() {
        tracker.onWindowStateChanged("com.android.chrome")

        var fired = 0
        tracker.armCapture { fired++ }
        tracker.onWindowStateChanged(ShadeTracker.SYSTEM_UI_PACKAGE)

        // The shade coming back into focus is not the shade leaving.
        assertEquals(0, fired)
        assertTrue(tracker.isCapturePending)
    }

    @Test
    fun `our own window while waiting does not fire the callback`() {
        tracker.onWindowStateChanged("com.android.chrome")

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
    fun `arming again replaces a capture that is still waiting`() {
        tracker.onWindowStateChanged("com.android.chrome")

        var first = 0
        var second = 0
        tracker.armCapture { first++ }
        tracker.armCapture { second++ }

        tracker.onWindowStateChanged("com.android.chrome")

        // Two taps inside the collapse window are one intent to capture, and the
        // second tap's frame is the one the user is looking at.
        assertEquals(0, first)
        assertEquals(1, second)
    }

    @Test
    fun `the callback fires exactly once when the window changes and then the timeout lands`() {
        tracker.onWindowStateChanged("com.android.chrome")

        var fired = 0
        tracker.armCapture { fired++ }

        tracker.onWindowStateChanged("com.android.chrome")
        tracker.onTimeout()

        // Two Memories for one tile tap is the failure this guards.
        assertEquals(1, fired)
    }

    @Test
    fun `the callback fires exactly once when the timeout lands and then the window changes`() {
        tracker.onWindowStateChanged("com.android.chrome")

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
        tracker.onWindowStateChanged("com.android.chrome")

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
    // Focus returns to the app ~240ms after the dismissal is requested, which is
    // near the start of the collapse rather than its end. The frame has to be
    // grabbed later than that.
    // ---------------------------------------------------------------------

    @Test
    fun `the settle delay is the documented value`() {
        assertEquals(ShadeTracker.SHADE_SETTLE_DELAY_MS, tracker.settleDelayMs())
    }

    @Test
    fun `the settle delay does not depend on which window fired the capture`() {
        tracker.onWindowStateChanged("com.android.chrome")

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
        // ending up in the screenshot. This is the regression guard for that.
        assertTrue(tracker.settleDelayMs() > 0L)
    }
}
