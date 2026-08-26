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
 * dismissal is unconditional. What remains is knowing when it has *gone*, which is
 * focus returning to the app the capture was armed over, and that one does arrive:
 * ~240ms after the request, measured on the same device.
 *
 * Returning to *that* app specifically, not merely to some window that is not the
 * shade. An incoming call, an alarm or an activity launched from a tapped notification
 * can take focus inside that 240ms window, and treating it as the shade's departure
 * both captures early and attributes the Memory to the wrong app.
 *
 * Not thread-safe by design, with one exception: [capturedAppPackage] is written on the
 * main looper and read from the IO dispatcher that persists the Memory, so it is
 * `@Volatile`. Everything else is main-looper only, including the posted timeout.
 * Adding locks here would suggest a second caller exists.
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
     * The app the current capture belongs to: whatever was in front when it was armed.
     *
     * Latched rather than read live at persist time, because a window that steals focus
     * during the shade's collapse would otherwise become the Memory's `sourcePackage` —
     * the same wrong attribution the shade itself used to cause.
     *
     * `@Volatile` because the write is on the main looper and the read is on
     * `Dispatchers.IO`.
     */
    @Volatile
    var capturedAppPackage: String? = null
        private set

    /**
     * The pending capture, if one is waiting for the shade to close.
     *
     * Nulled before being invoked, never after: that ordering is what makes the latch
     * one-shot even when the window change and the timeout race. Two
     * `takeScreenshot()` calls for one tile tap would persist two Memories.
     */
    private var pendingCapture: (() -> Unit)? = null

    /**
     * The package whose return to the foreground means the shade has gone.
     *
     * Null when nothing was in front at arm time — a cold service, or a capture from
     * the launcher before any app window was seen. In that case the first non-shade
     * window is taken to be the revealed app, because there is nothing better to wait
     * for and the alternative is always falling through to the backstop.
     */
    private var expectedPackage: String? = null

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
                if (expectedPackage == null) {
                    // Nothing was in front when this was armed, so this window is the
                    // best answer available to "which app is the user in".
                    if (pendingCapture != null) capturedAppPackage = pkg
                    fireIfArmed()
                } else if (expectedPackage == pkg) {
                    fireIfArmed()
                }
                // Any other app taking focus mid-collapse is not the shade leaving.
                // The backstop covers the case where the expected app never returns.
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
     *
     * Arming over an already-armed capture replaces it rather than queueing: two taps
     * inside the collapse window are one intent to capture, and the second tap's frame
     * is the one the user is looking at.
     */
    fun armCapture(onReady: () -> Unit) {
        expectedPackage = lastAppPackage
        capturedAppPackage = lastAppPackage
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
         * How long to let the shade finish painting after focus has left it.
         *
         * The focus signal is not the end of the collapse. Measured on the
         * `onemind_test` emulator (API 36), `TYPE_WINDOW_STATE_CHANGED` back to the
         * foreground app arrives **~240ms** after
         * `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` is requested, while burst
         * `screencap` frames taken outside the app still show the panel at ~370ms and
         * show it gone by ~670ms. Capturing on the signal alone lands inside that
         * range.
         *
         * 400ms puts the grab at ~640ms, past the observed tail, and lands total
         * latency at roughly the fixed 500ms the code spent before any of this existed
         * — so the fix costs the user nothing they were not already paying.
         *
         * Two things this value is **not** justified by, both retracted from an earlier
         * version of this comment: a top-of-frame brightness threshold, which reads a
         * dark Quick Settings panel on black as *darker* than a settled light app and
         * so cannot discriminate them; and any claim about what the persisted image
         * contained before the settle existed. It contained a fully opaque panel,
         * because no dismissal was being requested at all.
         *
         * What is verified through the app's own capture path: three consecutive tile
         * taps over Chrome, every saved image inspected and free of the shade.
         *
         * Deliberately not tuned down to the shortest value that happened to work: the
         * tail is a range, not a point, and a frame arriving one refresh late is a
         * wrong screenshot rather than a slow one.
         */
        const val SHADE_SETTLE_DELAY_MS = 400L
    }
}
