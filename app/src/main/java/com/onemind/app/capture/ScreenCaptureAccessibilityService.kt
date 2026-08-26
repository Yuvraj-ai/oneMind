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

    /**
     * The backstop itself, held so it can be cancelled.
     *
     * One instance rather than a fresh lambda per tap: `removeCallbacks` matches on
     * identity, so a new lambda each time would be uncancellable and a stale timeout
     * from one tap could fire the next tap's capture before its settle.
     */
    private val backstop = Runnable { shadeTracker.onTimeout() }

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
     * The shade is assumed open, because a Quick Settings tile cannot be reached
     * without opening it, and `TileService.onClick` closes nothing — only
     * `startActivityAndCollapse` does, and that path is used here solely for the
     * accessibility-settings redirect. So the dismissal is requested unconditionally.
     * `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` is available to an accessibility
     * service and to almost nothing else, which is why this cannot live in the tile.
     *
     * Dismissal is asynchronous and reports nothing, so the capture waits for focus to
     * return to the app it was armed over (~240ms), then for the collapse to finish
     * painting ([ShadeTracker.settleDelayMs]). The 500ms backstop covers a device that
     * never sends the signal. First one wins; [ShadeTracker] guarantees only one does.
     *
     * An earlier version gated all of this on detecting that the shade was open, from
     * `TYPE_WINDOW_STATE_CHANGED`. The shade's arrival is not one of those — on API 36
     * it produces only `TYPE_WINDOW_CONTENT_CHANGED` — so the gate never opened and the
     * shade was captured anyway. Widening the event mask is not the alternative: those
     * events fire on every clock tick, and receiving them would mean receiving the
     * contents of system windows this service promises not to read. See [ShadeTracker].
     *
     * On API 30 `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE` does not exist. `minSdk` is
     * 30 and raising it would drop devices for one action, so there the backstop expires
     * into the original behaviour: a screenshot of the shade. Knowingly unfixed rather
     * than quietly broken.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TAKE_SCREENSHOT) {
            // A backstop from a previous tap would otherwise fire this one early.
            handler.removeCallbacks(backstop)
            // Armed before the request, so the signal cannot arrive unheard.
            shadeTracker.armCapture(::captureAfterSettle)
            requestShadeDismissal()
            handler.postDelayed(backstop, SHADE_COLLAPSE_TIMEOUT_MS)
        }
        return START_NOT_STICKY
    }

    /** Let the shade finish painting, then grab the frame. */
    private fun captureAfterSettle() {
        handler.removeCallbacks(backstop)
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
                    // never the notification shade they tapped it from, and never a
                    // window that stole focus while the shade was collapsing.
                    sourcePackage = shadeTracker.capturedAppPackage
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
