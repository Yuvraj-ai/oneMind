package com.onemind.app.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.onemind.app.data.processing.ProcessingScheduler
import com.onemind.app.data.storage.ImageFileStorage
import com.onemind.app.domain.model.*
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Takes a screenshot via the Accessibility Service API.
 *
 * ## Why Accessibility rather than MediaProjection
 *
 * MediaProjection shows a system "Cast your screen" dialog on every capture — a
 * full-screen consent prompt designed for screen recording, not for one-tap
 * screenshots. On Android 14+ it asks "Single app or entire screen?" and shows
 * wording about "casting", which confused every test user into thinking the app was
 * about to stream their display.
 *
 * The Accessibility Service avoids all of that. `takeScreenshot()` (API 30+) grabs
 * one frame silently. The trade-off is a single, upfront system setting toggle
 * instead of a per-capture prompt — and the user grants or revokes that any time
 * from system settings. oneMind's privacy guarantee is the same: nothing is captured
 * without an explicit action (the QS tile tap), and no screen content is monitored.
 *
 * ## Foreground app detection
 *
 * `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` tells us which app the user was
 * looking at when they tapped the tile. That becomes the Memory's `sourcePackage`,
 * which is genuinely useful context — and something MediaProjection could not
 * provide at all without a separate `UsageStatsManager` query.
 *
 * ## What this service does NOT do
 *
 * - It does not listen to keystrokes, read screen text, or observe anything
 *   except which window is focused (to know the foreground app).
 * - It does not run a capture continuously. The screenshot happens once, on
 *   explicit command, then nothing until the next command.
 * - The Accessibility configuration (`accessibility_service_config.xml`) sets
 *   `android:canRetrieveWindowContent="false"` so the system does not deliver
 *   node trees to this service.
 */
@AndroidEntryPoint
class ScreenCaptureAccessibilityService : AccessibilityService() {

    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var imageFileStorage: ImageFileStorage
    @Inject lateinit var processingScheduler: ProcessingScheduler
    @Inject lateinit var notifier: CaptureNotifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Which app the user came from, and when the shade has finished leaving.
     *
     * Created lazily rather than in a field initialiser because `packageName` is a
     * `Context` method and is not safe to call before the service is attached.
     */
    private val shadeTracker by lazy { ShadeTracker(ownPackage = packageName) }

    /** Posts the shade-collapse backstop. Created once, on the main looper. */
    private val handler by lazy { Handler(mainLooper) }

    override fun onServiceConnected() {
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // We do not need the window's content tree — only the package name.
            flags = 0
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let(shadeTracker::onWindowStateChanged)
        }
    }

    override fun onInterrupt() {}

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
     * its departure arrives as a window-state change back to the foreground app —
     * ~240ms after the request, measured. Capture is triggered by that signal, and
     * the 500ms remains only as a backstop for a device that never sends it. First
     * one wins; [ShadeTracker] guarantees only one does.
     *
     * ## Why nothing checks whether the shade is open
     *
     * A previous version did, and the check was always false. The shade's arrival is
     * not a `TYPE_WINDOW_STATE_CHANGED` — on API 36 it produces only
     * `TYPE_WINDOW_CONTENT_CHANGED` — so the dismissal was never requested and the
     * shade was captured anyway, the original bug surviving its own fix. The check is
     * also unnecessary: this command comes from a Quick Settings tile, and reaching
     * that tile means the shade is open. So the dismissal is unconditional, and so is
     * the settle. See [ShadeTracker].
     *
     * ## Why the signal alone was not enough either
     *
     * The window-state change means *focus* left the shade, which happens near the
     * start of the collapse animation rather than at its end (~240ms against an
     * animation still running at ~600-700ms). Capturing on the raw signal produced a
     * screenshot of the shade mid-fade. So the signal is followed by
     * [ShadeTracker.settleDelayMs] before the frame is grabbed. The backstop gets the
     * same settle: a timeout means the shade may still be closing, not that it isn't.
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
            // Armed before the request, so the signal cannot arrive unheard.
            shadeTracker.armCapture(::captureAfterSettle)
            requestShadeDismissal()
            handler.postDelayed({ shadeTracker.onTimeout() }, SHADE_COLLAPSE_TIMEOUT_MS)
        }
        return START_NOT_STICKY
    }

    /** Let the shade finish painting, then grab the frame. */
    private fun captureAfterSettle() {
        handler.postDelayed({ takeScreenshotNow() }, shadeTracker.settleDelayMs())
    }

    /**
     * Ask the system to close the notification shade, if this Android version can be
     * asked. Harmless when there is no shade open to close.
     */
    private fun requestShadeDismissal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    private fun takeScreenshotNow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            notifier.notifyMessage("Screen capture requires Android 11+")
            return
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )

                    if (bitmap == null) {
                        screenshot.hardwareBuffer.close()
                        notifier.notifyMessage("Could not capture screen")
                        return
                    }

                    // Convert hardware bitmap to a software bitmap we can compress.
                    val softBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    bitmap.recycle()
                    screenshot.hardwareBuffer.close()

                    if (softBitmap == null) {
                        notifier.notifyMessage("Could not capture screen")
                        return
                    }

                    persist(softBitmap)
                }

                override fun onFailure(errorCode: Int) {
                    notifier.notifyMessage("Capture failed")
                }
            }
        )
    }

    private fun persist(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                val (canonical, thumbnail) = imageFileStorage.saveImage(bitmap)

                val memory = Memory(
                    sourceType = SourceType.SCREENSHOT,
                    processingState = ProcessingState.DRAFT,
                    contentBlocks = listOf(
                        ContentBlock(
                            type = ContentType.IMAGE,
                            content = canonical,
                            thumbnailPath = thumbnail,
                            position = 0
                        )
                    ),
                    // The app the user was looking at when they tapped the tile —
                    // never the notification shade they tapped it from.
                    sourcePackage = shadeTracker.lastAppPackage
                )

                val memoryId = memoryRepository.createMemory(memory)
                memoryRepository.transitionState(memoryId, ProcessingState.SAVED)
                processingScheduler.enqueue(memoryId)

                notifier.notify(
                    memoryId = memoryId,
                    previewText = "Screenshot captured",
                    thumbnail = bitmap
                )
            } catch (e: Exception) {
                notifier.notifyMessage("Could not save screenshot")
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_TAKE_SCREENSHOT = "com.onemind.app.ACTION_TAKE_SCREENSHOT"

        /**
         * How long to wait for the shade's departure before capturing regardless.
         *
         * This used to be the mechanism and is now the backstop, which is why it
         * kept its value and changed its name. The window-state change normally
         * arrives well inside it; when it does, this never fires. When it does not —
         * API 30, where dismissal cannot be requested at all, or a device that
         * collapses the shade without transferring window focus — the capture still
         * happens rather than never happening, and the user gets a picture of the
         * shade instead of silence.
         *
         * Firing it does not skip [ShadeTracker.settleDelayMs]: expiry says only that
         * no signal arrived, which is if anything a reason to think the collapse is
         * still in progress.
         */
        private const val SHADE_COLLAPSE_TIMEOUT_MS = 500L
    }
}
