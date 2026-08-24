# Shade-Safe Screenshot Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A QS-tile screenshot captures the app the user was looking at, tagged with that app's package — not the notification shade, and not `com.android.systemui`.

**Architecture:** The notification shade is dismissed by `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)`, which only an `AccessibilityService` may call, so the fix stays inside `ScreenCaptureAccessibilityService`. The decision logic — is the shade in front, what was the last real app, has the shade gone yet — moves into a plain Kotlin class `ShadeTracker` that has no Android dependencies and is unit-testable on the JVM. The service keeps only the privileged calls and the `Handler`.

**Tech Stack:** Kotlin 2.1.0, Android AccessibilityService API, JUnit 4 (JVM unit tests), Hilt, Gradle.

## Global Constraints

- Branch is `my-extra-work`. Never commit to `main`, never merge or fast-forward from `main`.
- Commit attribution is the user alone. **Never** add `Co-Authored-By: Claude`, `Generated with`, or any similar trailer.
- No version bump, git tag, APK build, or GitHub release. This ships unreleased.
- File the GitHub issue **before** implementing, and put `(#N)` in the commit subject. One commit for this whole plan.
- GitHub access uses `curl` with the PAT from `/home/imyuvi/projects/codingagents/.env`. `gh` is not installed. **Never** run `git remote -v` or `git config --get remote.origin.url` — the PAT is embedded in origin's URL and printing it leaks it.
- Build invocation, always from `/home/imyuvi/projects/codingagents/oneMind`:
  `JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew <task>`
  `java` is not on `PATH`; the `JAVA_HOME` prefix is mandatory on every invocation.
- `minSdk` is **30** and does not change. `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` is API **31** (`Build.VERSION_CODES.S`); the API-30 path is knowingly left unfixed and must say so in a comment.
- `assembleDebug` must pass before the commit. It is the only check that validates the Hilt DI graph.
- Spec: `docs/superpowers/specs/2026-08-24-onemind-ui-redesign-design.md`, "Phase 1".

---

## File Structure

| File | Responsibility |
|---|---|
| `app/src/main/java/com/onemind/app/capture/ShadeTracker.kt` | **Create.** Pure decision logic: which package is in front, which was the last real app, and a one-shot latch that fires on shade-gone or timeout, whichever comes first. No Android imports. |
| `app/src/test/java/com/onemind/app/ShadeTrackerTest.kt` | **Create.** JVM unit tests for every branch of `ShadeTracker`. |
| `app/src/main/java/com/onemind/app/capture/ScreenCaptureAccessibilityService.kt` | **Modify.** Delegate package tracking to `ShadeTracker`, dismiss the shade on API 31+, and capture on the tracker's signal instead of a blind delay. |

`ShadeTracker` lives in `capture/` next to its only consumer, per the codebase's pattern of keeping a collaborator beside the class that owns it (`CaptureNotifier`, `ScreenCaptureTileService`). The test goes in the flat `app/src/test/java/com/onemind/app/` directory — this project puts every unit test there with no subpackages.

---

### Task 1: `ShadeTracker` — the pure decision logic

**Files:**
- Create: `app/src/main/java/com/onemind/app/capture/ShadeTracker.kt`
- Test: `app/src/test/java/com/onemind/app/ShadeTrackerTest.kt`

**Interfaces:**
- Consumes: nothing. This task has no dependencies.
- Produces, relied on by Task 2:
  - `class ShadeTracker(private val ownPackage: String)`
  - `fun onWindowStateChanged(pkg: String)`
  - `val isShadeInFront: Boolean`
  - `val lastAppPackage: String?`
  - `fun awaitShadeGone(onReady: () -> Unit): Boolean` — registers a one-shot
    callback. Returns `true` if it fired synchronously (the shade was already
    gone), `false` if it is now armed and waiting.
  - `fun onTimeout()` — fires an armed callback and disarms.
  - `const val SYSTEM_UI_PACKAGE = "com.android.systemui"` in the companion.

**Why a latch and not a callback per event:** `takeScreenshot()` fired twice for one tile tap persists two Memories. The latch must be idempotent — once it fires, both the window-state path and the timeout path must find nothing to do.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/onemind/app/ShadeTrackerTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd /home/imyuvi/projects/codingagents/oneMind
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest --tests '*ShadeTrackerTest*'
```

Expected: FAIL at compilation — `Unresolved reference: ShadeTracker`. Not a
runtime assertion failure. If it compiles, `ShadeTracker.kt` already exists and
you are in the wrong state.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/com/onemind/app/capture/ShadeTracker.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd /home/imyuvi/projects/codingagents/oneMind
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew testDebugUnitTest --tests '*ShadeTrackerTest*'
```

Expected: `BUILD SUCCESSFUL`, 12 tests, 0 failures.

Do not commit yet — Task 2 wires this in, and a commit with `ShadeTracker`
unused would leave the defect in place while claiming it fixed.

---

### Task 2: Wire the tracker into the service and dismiss the shade

**Files:**
- Modify: `app/src/main/java/com/onemind/app/capture/ScreenCaptureAccessibilityService.kt`
  - `:83-92` (`onAccessibilityEvent`)
  - `:96-114` (`onStartCommand` and its KDoc)
  - `:175` (`sourcePackage = foregroundPackage`)
  - `:200-213` (companion object)
  - the `foregroundPackage` field at `:71-73`

**Interfaces:**
- Consumes from Task 1: `ShadeTracker(ownPackage)`, `onWindowStateChanged(pkg)`,
  `isShadeInFront`, `lastAppPackage`, `awaitShadeGone(onReady) : Boolean`,
  `onTimeout()`, `ShadeTracker.SYSTEM_UI_PACKAGE`.
- Produces: nothing consumed by a later task. This is the last code task.

- [ ] **Step 1: Replace the `foregroundPackage` field with the tracker**

Find this, at `:65-73`:

```kotlin
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The last foreground package we observed. Updated on every window-state change.
     * Read at capture time to tag the Memory with which app was being used.
     */
    @Volatile
    private var foregroundPackage: String? = null
```

Replace with:

```kotlin
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Which window is in front, and whether it is the notification shade.
     *
     * Created lazily rather than in a field initialiser because `packageName` is a
     * `Context` method and is not safe to call before the service is attached.
     */
    private val shadeTracker by lazy { ShadeTracker(ownPackage = packageName) }

    /** Posts the shade-collapse backstop. Created once, on the main looper. */
    private val handler by lazy { Handler(mainLooper) }
```

- [ ] **Step 2: Add the `Handler` import**

The current code calls `android.os.Handler(mainLooper)` fully qualified at `:109`.
Add a proper import so the field above reads cleanly. In the import block, after
`import android.os.Build`:

```kotlin
import android.os.Handler
```

- [ ] **Step 3: Delegate `onAccessibilityEvent` to the tracker**

Find this, at `:83-92`:

```kotlin
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { pkg ->
                // Don't record ourselves as the foreground app.
                if (pkg != packageName) {
                    foregroundPackage = pkg
                }
            }
        }
    }
```

Replace with:

```kotlin
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let(shadeTracker::onWindowStateChanged)
        }
    }
```

The "don't record ourselves" rule has not been dropped — it moved into
`ShadeTracker`, which also rejects the shade. That second rejection is the fix for
every tile screenshot having been tagged `com.android.systemui`.

- [ ] **Step 4: Dismiss the shade and wait for the signal**

Find this, at `:96-114`:

```kotlin
    /**
     * Triggered by the QS tile (via startService). Takes one screenshot and saves it.
     *
     * A delay is inserted before capturing because the tile's onClick fires while
     * the notification shade is still visible. The shade collapses automatically
     * after onClick returns, but the animation takes ~300-500ms. Without the delay,
     * the screenshot captures the shade rather than the app underneath — which is
     * exactly the bug that prompted this fix.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TAKE_SCREENSHOT) {
            // Post with a delay long enough for the shade collapse animation to
            // finish. 500ms is safe on every device tested; 300ms races on slow ones.
            android.os.Handler(mainLooper).postDelayed({
                takeScreenshotNow()
            }, SHADE_COLLAPSE_DELAY_MS)
        }
        return START_NOT_STICKY
    }
```

Replace with:

```kotlin
    /**
     * Triggered by the QS tile (via startService). Takes one screenshot and saves it.
     *
     * ## Why this is not just a delay
     *
     * The previous version posted the capture behind a fixed 500ms and explained it
     * as waiting out the shade's collapse animation. The shade was not collapsing.
     * `TileService.onClick` carries no such contract — only
     * `startActivityAndCollapse` closes the shade, and that path is used here solely
     * for the accessibility-settings redirect. So the delay was spent sitting beside
     * a fully open shade, and `takeScreenshot()` captured it, faithfully.
     *
     * Asking is the fix. `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` is available to
     * an accessibility service and to almost nothing else, which is why this cannot
     * live in the tile.
     *
     * ## Why the delay survives, demoted
     *
     * Dismissal is asynchronous and reports nothing. But the shade is a window, so
     * its departure arrives as a window-state change — the same events this service
     * already receives, which is how it knew the shade was in front to begin with.
     * Capture is triggered by that signal, and the 500ms remains only as a backstop
     * for a device that never sends it. First one wins; [ShadeTracker] guarantees
     * only one does.
     *
     * ## API 30
     *
     * `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` is API 31. `minSdk` is 30, and
     * raising it would drop devices for one action, so on API 30 the shade is not
     * dismissed and the backstop expires into the original behaviour: a screenshot
     * of the shade. Knowingly unfixed rather than quietly broken.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TAKE_SCREENSHOT) {
            requestShadeDismissal()

            val capturedSynchronously = shadeTracker.awaitShadeGone(::takeScreenshotNow)
            if (!capturedSynchronously) {
                handler.postDelayed({ shadeTracker.onTimeout() }, SHADE_COLLAPSE_TIMEOUT_MS)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Ask the system to close the notification shade, if it is open and if this
     * Android version can be asked.
     */
    private fun requestShadeDismissal() {
        if (!shadeTracker.isShadeInFront) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }
```

- [ ] **Step 5: Read the source package from the tracker**

Find this, at `:174-175`:

```kotlin
                    // The app the user was looking at when they tapped the tile.
                    sourcePackage = foregroundPackage
```

Replace with:

```kotlin
                    // The app the user was looking at when they tapped the tile —
                    // never the notification shade they tapped it from.
                    sourcePackage = shadeTracker.lastAppPackage
```

- [ ] **Step 6: Rename the constant to say what it now is**

Find this, at `:200-213`:

```kotlin
    companion object {
        const val ACTION_TAKE_SCREENSHOT = "com.onemind.app.ACTION_TAKE_SCREENSHOT"

        /**
         * Time to wait for the notification shade to finish collapsing.
         *
         * The shade animation runs ~300ms on most devices but can be slower on
         * low-end hardware or with accessibility animations enabled. 500ms is safe
         * on everything tested. Too short = captures the shade; too long = the user
         * thinks nothing happened. 500ms is imperceptible as "lag" but enough for
         * any animation to finish.
         */
        private const val SHADE_COLLAPSE_DELAY_MS = 500L
    }
```

Replace with:

```kotlin
    companion object {
        const val ACTION_TAKE_SCREENSHOT = "com.onemind.app.ACTION_TAKE_SCREENSHOT"

        /**
         * How long to wait for the shade's departure before capturing regardless.
         *
         * This used to be the mechanism and is now the backstop, which is why it
         * kept its value and changed its name. The window-state change normally
         * arrives well inside it; when it does, this never fires. When it does not —
         * an OEM shade under a different package, or API 30 where dismissal cannot
         * be requested at all — the capture still happens rather than never
         * happening, and the user gets a picture of the shade instead of silence.
         */
        private const val SHADE_COLLAPSE_TIMEOUT_MS = 500L
    }
```

- [ ] **Step 7: Verify the whole module compiles and the unit tests pass**

```bash
cd /home/imyuvi/projects/codingagents/oneMind
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. `assembleDebug` is what proves the Hilt graph still
resolves — `ShadeTracker` is constructed by hand, not injected, so nothing should
have changed there, and this confirms it. There must be no remaining reference to
`foregroundPackage` or `SHADE_COLLAPSE_DELAY_MS`; a leftover reference fails
compilation, which is the intended safety net for Steps 1–6.

- [ ] **Step 8: Verify manually on device**

This is the part no test covers, and it is the part the user reported. Boot the
`onemind_test` AVD or use a physical device, install the debug APK, enable the
accessibility service, and add the oneMind tile to Quick Settings.

```bash
cd /home/imyuvi/projects/codingagents/oneMind
JAVA_HOME=/home/imyuvi/.local/jdks/jdk-17.0.20.1+1 ./gradlew installDebug
```

Then, by hand:
1. Open any app that is not oneMind — the browser is a good choice because its
   content is unmistakable.
2. Pull the notification shade **fully down**.
3. Tap the oneMind screen-capture tile.
4. Open oneMind and look at the newest Memory.

Two things must both be true, and before this change neither was:
- The image shows the browser, not the shade.
- The Memory's source reads as the browser, not "System UI".

Also check the feed's source filter row: it must not offer a "System UI" entry
created by this capture.

- [ ] **Step 9: File the issue**

Do this before committing, so the commit can reference it. `gh` is not installed.

```bash
export GITHUB_PAT=$(grep '^GITHUB_PAT=' /home/imyuvi/projects/codingagents/.env | cut -d= -f2- | tr -d '\r\n')
curl -s -X POST -H "Authorization: Bearer $GITHUB_PAT" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/Yuvraj-ai/oneMind/issues \
  -d '{
    "title": "Tile screenshot captures the notification shade, and records System UI as the source app",
    "body": "Pulling the notification shade down and tapping the screen-capture tile saves a picture of the shade instead of the app underneath.\n\n`ScreenCaptureTileService.onClick()` starts the accessibility service and returns. `ScreenCaptureAccessibilityService.onStartCommand` then posts the capture behind a fixed 500ms delay, documented as waiting out the shade collapse animation. Nothing collapses the shade: `TileService.onClick` carries no such contract, and only `startActivityAndCollapse` closes it — used here solely for the accessibility-settings redirect. The delay is spent beside a fully open shade.\n\nSecond defect in the same path. Opening the shade emits `TYPE_WINDOW_STATE_CHANGED` for `com.android.systemui`, and `onAccessibilityEvent` filters out only oneMind'\''s own package, so `foregroundPackage` is `com.android.systemui` by capture time. Every tile screenshot is persisted with the wrong `sourcePackage`, and the feed'\''s source filter gains a phantom System UI entry.\n\nFix: `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)` (API 31) from the accessibility service, then capture on the window-state change back to a real app rather than on a fixed delay, which is demoted to a backstop. The shade is excluded from foreground-app tracking. API 30 has no such global action and is knowingly left unfixed.\n\nDesign: `docs/superpowers/specs/2026-08-24-onemind-ui-redesign-design.md`, Phase 1."
  }' | python3 -c "import sys,json; print('issue', json.load(sys.stdin)['number'])"
```

Note the issue number it prints. It is `N` in the next step.

- [ ] **Step 10: Commit**

Replace `N` with the issue number from Step 9.

```bash
cd /home/imyuvi/projects/codingagents/oneMind
git add app/src/main/java/com/onemind/app/capture/ShadeTracker.kt \
        app/src/main/java/com/onemind/app/capture/ScreenCaptureAccessibilityService.kt \
        app/src/test/java/com/onemind/app/ShadeTrackerTest.kt
git commit -F - <<'EOF'
Dismiss the notification shade before capturing the screen (#N)

The tile screenshot photographed the shade. onStartCommand posted the capture
behind a fixed 500ms and called it the shade's collapse animation, but nothing
was collapsing: TileService.onClick makes no such promise, and the only call
here that does close the shade — startActivityAndCollapse — is used solely for
the accessibility-settings redirect. The delay was spent beside an open shade.

performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE) is the missing
call, and an accessibility service is the only component that may make it, so
the fix stays here. Its completion is not reported, but the shade is a window,
so its departure arrives as a window-state change this service already
receives. Capture now waits for that signal; the 500ms survives as a backstop
for a device that never sends one.

Same commit fixes the defect one line away: the shade opening is itself a
window-state change, and onAccessibilityEvent rejected only oneMind's own
package, so com.android.systemui became the recorded source app of every tile
screenshot and a phantom "System UI" entry appeared in the feed's source
filter.

The decision — which window is in front, which was the last real app, has the
shade gone — moves to ShadeTracker, which needs no Android and so can be
tested. The latch is one-shot on purpose: two takeScreenshot() calls for one
tap would persist two Memories. What remains untestable is performGlobalAction
itself, verified by hand on device.

API 30 has no GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE. minSdk stays 30 rather
than dropping devices for one action, so API 30 falls through to the backstop
and the old behaviour. Knowingly unfixed, and commented as such.
EOF
git log --oneline -1
```

Expected: one commit, subject ending `(#N)`, and **no** `Co-Authored-By` or
generated-by trailer in the body.

- [ ] **Step 11: Close the issue with a comment**

Replace `N` and `<sha>`.

```bash
export GITHUB_PAT=$(grep '^GITHUB_PAT=' /home/imyuvi/projects/codingagents/.env | cut -d= -f2- | tr -d '\r\n')
curl -s -X POST -H "Authorization: Bearer $GITHUB_PAT" \
  -H "Accept: application/vnd.github+json" \
  https://api.github.com/repos/Yuvraj-ai/oneMind/issues/N/comments \
  -d '{"body": "Fixed in `<sha>` on `my-extra-work`.\n\nThe shade is now dismissed via `performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)` and the capture waits for the window-state change back to a real app, with the old 500ms kept only as a backstop. `com.android.systemui` is excluded from foreground-app tracking, so the source package is the app the user was actually in.\n\nDecision logic extracted to `ShadeTracker` and covered by 12 JVM unit tests, including the one-shot latch under both race orderings. `performGlobalAction` itself cannot be asserted without a real display and was verified manually: shade down, tile tapped, saved Memory shows the app underneath and carries its package.\n\nAPI 30 is knowingly unfixed — the global action does not exist there and `minSdk` stays 30.\n\n_Posted by an AI agent._"}' \
  | python3 -c "import sys,json; print('commented on', json.load(sys.stdin)['html_url'])"
```

---

## Done when

- `ShadeTrackerTest` passes with 12 tests on the JVM.
- `assembleDebug` and `testDebugUnitTest` both pass.
- On device: shade down → tile tap → the saved Memory shows the app underneath
  and is attributed to that app.
- One commit on `my-extra-work`, subject ends `(#N)`, no AI attribution trailer.
- The issue is closed with an AI-attributed comment.
- Nothing released: no version bump, no tag, no APK published.
