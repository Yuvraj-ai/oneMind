package com.onemind.app.capture

/**
 * Decides when it is safe to take a screenshot, and which app the user came from.
 *
 * Split out of [ScreenCaptureAccessibilityService] because the bug it fixes was a
 * guess: the service waited a fixed 500ms for a notification shade that nothing had
 * asked to close, and captured it. Guesses about timing are exactly what a test pins
 * down, and none of this needs Android to be true — so none of it lives in the
 * service any more.
 *
 * ## Why there is no "is the shade open?" question here
 *
 * There was, and it was always answered "no". The shade opening is not a
 * `TYPE_WINDOW_STATE_CHANGED`: measured on API 36, expanding the notification shade
 * and then Quick Settings produced 30 `TYPE_WINDOW_CONTENT_CHANGED` events from
 * `com.android.systemui` and **zero** window-state changes. A tracker keyed on
 * window-state changes therefore never saw the shade arrive, never asked for it to be
 * dismissed, and captured it — the original bug, surviving its own fix.
 *
 * Widening the event mask to content changes is not the answer either: those fire for
 * every clock tick and arriving notification, so "the shade is in front" would be
 * permanently true, and the service would be handed the contents of system windows it
 * has no business seeing.
 *
 * The question is unnecessary. This capture is triggered by a Quick Settings tile, and
 * reaching that tile means opening the shade — so the shade is open, always, and the
 * dismissal is unconditional. What remains is knowing when it has *gone*, which is a
 * window-state change back to a real app, and that one does arrive: ~240ms after the
 * request, measured on the same device.
 *
 * Not thread-safe by design: every caller is the service's main looper, which is also
 * where the timeout is posted. Adding locks here would suggest a second caller exists.
 */
class ShadeTracker(private val ownPackage: String) {

    /**
     * The last window we saw that was neither the shade nor us.
     *
     * Still filtered against the shade even though the shade is not tracked any
     * more: `com.android.systemui` does emit the occasional window-state change, and
     * before this class existed the service's filter — anything that is not us — let
     * it become the recorded source app of a tile screenshot.
     */
    var lastAppPackage: String? = null
        private set

    /**
     * The pending capture, if one is waiting for the shade to close.
     *
     * Nulled before being invoked, never after: that ordering is what makes the latch
     * one-shot even when the window change and the timeout race. Two
     * `takeScreenshot()` calls for one tile tap would persist two Memories.
     */
    private var pendingCapture: (() -> Unit)? = null

    fun onWindowStateChanged(pkg: String) {
        when (pkg) {
            SYSTEM_UI_PACKAGE -> {
                // The shade is not tracked, and a system window is not a source app.
            }
            ownPackage -> {
                // Our own windows say nothing about which app the user came from,
                // and the capture notification is one of them.
            }
            else -> {
                lastAppPackage = pkg
                fireIfArmed()
            }
        }
    }

    /**
     * Wait for the shade to get out of the way, then run [onReady].
     *
     * Always defers — see the class KDoc for why there is no shade-is-open test to
     * short-circuit on. The caller is responsible for requesting the dismissal that
     * produces the signal, and for arming a backstop in case it never comes.
     *
     * [onReady] is responsible for honouring [settleDelayMs] before it actually grabs
     * a frame; this class decides *how long*, not *how to wait*, because posting a
     * delayed message needs a looper and nothing here needs Android.
     */
    fun armCapture(onReady: () -> Unit) {
        pendingCapture = onReady
    }

    /**
     * How long to wait after the shade-gone signal before grabbing the frame.
     *
     * Pure, and a method rather than the bare constant so that the service reads as
     * asking a question rather than knowing an answer — the wait is a property of the
     * shade's behaviour, which is what this class is about.
     */
    fun settleDelayMs(): Long = SHADE_SETTLE_DELAY_MS

    /**
     * The backstop fired. Capture anyway.
     *
     * A no-op when nothing is armed, so a timeout left over from a completed capture
     * cannot trigger the next one.
     */
    fun onTimeout() = fireIfArmed()

    /** Whether a capture is armed and still waiting. Exists for the tests. */
    val isCapturePending: Boolean get() = pendingCapture != null

    private fun fireIfArmed() {
        val capture = pendingCapture ?: return
        pendingCapture = null
        capture()
    }

    companion object {
        /**
         * The notification shade's package.
         *
         * Hardcoded because there is no API that names it, and every AOSP-derived
         * build uses it. A skin that does not would let the shade become a capture's
         * recorded source app — cosmetic, and the capture itself is unaffected
         * because dismissal no longer depends on recognising the package.
         */
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        /**
         * How long to let the shade finish painting after it stops being in front.
         *
         * Measured on the `onemind_test` emulator (API 36), driving the real QS tile
         * with a real expanded shade:
         *
         * - `TYPE_WINDOW_STATE_CHANGED` back to the foreground app arrives **~240ms**
         *   after `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` is requested. That event
         *   is *focus transfer*, which happens near the **start** of the collapse.
         * - The collapse is not visually finished until **~600-700ms** (burst
         *   `screencap` frames, mean brightness of the top 18% of the frame: open
         *   shade ~105, app ~248; still 105 at ~370ms, 248 by ~670ms).
         *
         * Capturing on the signal alone therefore grabbed the shade mid-fade —
         * notifications ghosted, QS pills still legible. 400ms bridges the gap
         * (240 + 400 = ~640ms, past the observed animation tail) and lands total
         * latency at roughly the fixed 500ms the code spent before any of this
         * existed, so the fix costs the user nothing they were not already paying.
         *
         * Deliberately not tuned down to the shortest value that happened to work:
         * the tail is a range, not a point, and a frame arriving one refresh late is
         * a wrong screenshot rather than a slow one.
         */
        const val SHADE_SETTLE_DELAY_MS = 400L
    }
}
