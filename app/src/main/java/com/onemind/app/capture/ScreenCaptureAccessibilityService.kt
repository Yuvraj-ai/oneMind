package com.onemind.app.capture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
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
     * The last foreground package we observed. Updated on every window-state change.
     * Read at capture time to tag the Memory with which app was being used.
     */
    @Volatile
    private var foregroundPackage: String? = null

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
            event.packageName?.toString()?.let { pkg ->
                // Don't record ourselves as the foreground app.
                if (pkg != packageName) {
                    foregroundPackage = pkg
                }
            }
        }
    }

    override fun onInterrupt() {}

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
                    // The app the user was looking at when they tapped the tile.
                    sourcePackage = foregroundPackage
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
}
