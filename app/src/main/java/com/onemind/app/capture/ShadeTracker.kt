package com.onemind.app.capture

/**
 * Decides when it is safe to take a screenshot.
 *
 * Split out of [ScreenCaptureAccessibilityService] because the bug it fixes was a
 * guess: the service waited a fixed 500ms for a notification shade that nothing had
 * asked to close, and captured it. Guesses about timing are exactly what a test
 * pins down, and none of this needs Android to be true — so none of it lives in the
 * service any more.
 *
 * Two jobs, and they are the same job seen from two sides:
 *
 * - **Which app is the user actually in.** The shade opening is a window-state
 *   change like any other, so the service's old filter — anything that is not us —
 *   let `com.android.systemui` become the recorded source app of every tile
 *   screenshot.
 * - **Has the shade gone yet.** Once dismissal is requested, the window-state
 *   change back to a real app *is* the completion signal. Waiting for it beats
 *   waiting a fixed interval, and a timeout is kept only as a backstop for the
 *   case where that signal never arrives.
 *
 * Not thread-safe by design: every caller is the service's main looper, which is
 * also where the timeout is posted. Adding locks here would suggest a second
 * caller exists.
 */
class ShadeTracker(private val ownPackage: String) {

    /** The last window we saw that was neither the shade nor us. */
    var lastAppPackage: String? = null
        private set

    /** Whether the most recent window belonged to the notification shade. */
    var isShadeInFront: Boolean = false
        private set

    /**
     * The pending capture, if one is waiting for the shade to close.
     *
     * Nulled before being invoked, never after: that ordering is what makes the
     * latch one-shot even when the window change and the timeout race. Two
     * `takeScreenshot()` calls for one tile tap would persist two Memories.
     */
    private var pendingCapture: (() -> Unit)? = null

    fun onWindowStateChanged(pkg: String) {
        when (pkg) {
            SYSTEM_UI_PACKAGE -> isShadeInFront = true
            ownPackage -> {
                // Our own windows say nothing about which app the user came from,
                // and the capture notification is one of them.
            }
            else -> {
                isShadeInFront = false
                lastAppPackage = pkg
                fireIfArmed()
            }
        }
    }

    /**
     * Run [onReady] as soon as the shade is out of the way.
     *
     * @return true if it ran synchronously because the shade was never in front —
     *   the common case, when the tile is tapped from a shade that some other app
     *   already closed, or on a device where the shade was not open at all.
     */
    fun awaitShadeGone(onReady: () -> Unit): Boolean {
        if (!isShadeInFront) {
            onReady()
            return true
        }
        pendingCapture = onReady
        return false
    }

    /**
     * The backstop fired. Capture anyway.
     *
     * A no-op when nothing is armed, so a timeout left over from a completed
     * capture cannot trigger the next one.
     */
    fun onTimeout() = fireIfArmed()

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
         * build uses it. A skin that does not would fall back to the timeout, which
         * is the same behaviour as before this class existed — degraded, not broken.
         */
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
