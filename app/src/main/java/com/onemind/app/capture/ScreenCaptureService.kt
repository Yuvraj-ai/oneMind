package com.onemind.app.capture

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.onemind.app.R
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
 * Captures a single screen frame and saves it as a Memory.
 *
 * A foreground service rather than plain background work because Android 14
 * requires one: a `mediaProjection`-typed foreground service must already be
 * running before [MediaProjectionManager.getMediaProjection] is called, or the
 * call throws. That constraint dictates the shape of this class — the ordering in
 * [onStartCommand] is not stylistic.
 *
 * The service is deliberately short-lived. It starts, grabs one frame, releases
 * the projection, and stops itself. There is no continuous recording and no
 * VirtualDisplay left alive: the app captures what the user asked for at the
 * moment they asked, and gives the permission straight back.
 */
@AndroidEntryPoint
class ScreenCaptureService : Service() {

    @Inject lateinit var memoryRepository: MemoryRepository
    @Inject lateinit var imageFileStorage: ImageFileStorage
    @Inject lateinit var processingScheduler: ProcessingScheduler
    @Inject lateinit var notifier: CaptureNotifier

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureThread: HandlerThread? = null

    /** Guards against a second frame arriving before teardown finishes. */
    @Volatile
    private var frameHandled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Foreground first. On API 34+ the projection cannot be obtained
        //    until this service is already in the foreground.
        startForegroundCompat()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == 0 || resultData == null) {
            fail("Capture cancelled")
            return START_NOT_STICKY
        }

        try {
            beginCapture(resultCode, resultData)
        } catch (e: Exception) {
            fail("Could not capture screen")
        }

        return START_NOT_STICKY
    }

    private fun beginCapture(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as? MediaProjectionManager ?: run {
            fail("Screen capture unavailable")
            return
        }

        // 2. Now safe to obtain the projection.
        val mediaProjection = manager.getMediaProjection(resultCode, resultData) ?: run {
            fail("Capture cancelled")
            return
        }
        projection = mediaProjection

        // 3. A callback must be registered before creating the VirtualDisplay on
        //    API 34+. It also covers the user revoking consent mid-capture.
        val thread = HandlerThread("onemind-screen-capture").apply { start() }
        captureThread = thread
        val handler = Handler(thread.looper)

        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    // Revoked or ended. If no frame was handled, nothing was saved.
                    if (!frameHandled) {
                        frameHandled = true
                        fail("Capture stopped")
                    }
                }
            },
            handler
        )

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        if (width <= 0 || height <= 0) {
            fail("Could not read screen size")
            return
        }

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, MAX_BUFFERED_IMAGES)
        imageReader = reader

        // 4. Wait for a frame rather than polling: acquiring immediately after
        //    creating the display almost always returns null.
        reader.setOnImageAvailableListener({ r ->
            if (frameHandled) return@setOnImageAvailableListener
            frameHandled = true

            val bitmap = readFrame(r, width, height)
            if (bitmap == null) {
                fail("Could not read screen")
            } else {
                releaseCaptureResources()
                persist(bitmap)
            }
        }, handler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "onemind-capture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )

        // Give up rather than hold the projection open indefinitely if no frame
        // ever arrives.
        handler.postDelayed({
            if (!frameHandled) {
                frameHandled = true
                fail("Screen capture timed out")
            }
        }, CAPTURE_TIMEOUT_MS)
    }

    private fun readFrame(reader: ImageReader, width: Int, height: Int): Bitmap? {
        val image = reader.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            ScreenFrameConverter.toBitmap(
                buffer = plane.buffer,
                width = width,
                height = height,
                pixelStride = plane.pixelStride,
                rowStride = plane.rowStride
            )
        } catch (e: Exception) {
            null
        } finally {
            image.close()
        }
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
                    )
                )

                val memoryId = memoryRepository.createMemory(memory)
                memoryRepository.transitionState(memoryId, ProcessingState.SAVED)
                processingScheduler.enqueue(memoryId)

                notifier.notify(memoryId = memoryId, previewText = null, thumbnail = bitmap)
            } catch (e: Exception) {
                notifier.notifyMessage("Could not save screenshot")
            } finally {
                bitmap.recycle()
                stopSelfCleanly()
            }
        }
    }

    private fun fail(message: String) {
        notifier.notifyMessage(message)
        stopSelfCleanly()
    }

    private fun startForegroundCompat() {
        val notification: Notification = NotificationCompat.Builder(
            this,
            NotificationChannels.CHANNEL_CAPTURE_SERVICE
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Capturing screen")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
        )
    }

    /**
     * Release the projection and everything hanging off it.
     *
     * Called as soon as a frame is in hand, before the Memory is even written, so
     * the app holds screen-capture permission for the shortest possible time.
     */
    private fun releaseCaptureResources() {
        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        projection?.stop()
        projection = null
    }

    private fun stopSelfCleanly() {
        releaseCaptureResources()
        captureThread?.quitSafely()
        captureThread = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseCaptureResources()
        captureThread?.quitSafely()
        captureThread = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "com.onemind.app.EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.onemind.app.EXTRA_RESULT_DATA"

        private const val FOREGROUND_ID = 4001

        /** Two is enough to avoid dropping the first frame without buffering video. */
        private const val MAX_BUFFERED_IMAGES = 2

        /** Give up if the compositor never delivers a frame. */
        private const val CAPTURE_TIMEOUT_MS = 5_000L

        fun intent(context: Context, resultCode: Int, resultData: Intent): Intent =
            Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
    }
}
