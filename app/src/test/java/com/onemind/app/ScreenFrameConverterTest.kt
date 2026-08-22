package com.onemind.app

import com.onemind.app.capture.ScreenFrameConverter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The row-stride arithmetic behind screen capture.
 *
 * ImageReader hands back a buffer whose rows are padded out to a hardware-friendly
 * stride, so the buffer is usually wider than the screen. Copying it straight into
 * a Bitmap of the screen's dimensions shears the image — every row drifts further
 * right than the last. It is the classic MediaProjection bug, and on a device it is
 * invisible until you look at a screenshot from a phone whose width happens not to
 * divide evenly.
 *
 * Testable here precisely because the logic was kept out of the service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenFrameConverterTest {

    private fun buffer(height: Int, rowStride: Int): ByteBuffer =
        ByteBuffer
            .allocate(ScreenFrameConverter.requiredBufferBytes(height, rowStride))
            .order(ByteOrder.nativeOrder())

    // --- the padded case, which is the normal one on real hardware ----------

    @Test
    fun `a padded buffer still produces a bitmap of the real screen size`() {
        // 1080 wide at 4 bytes per pixel is 4320 bytes; hardware pads to 4352.
        val width = 1080
        val height = 2400
        val pixelStride = 4
        val rowStride = 4352

        val bitmap = ScreenFrameConverter.toBitmap(
            buffer = buffer(height, rowStride),
            width = width,
            height = height,
            pixelStride = pixelStride,
            rowStride = rowStride
        )

        // The padding must be cropped away, not left in the saved screenshot.
        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    @Test
    fun `an unpadded buffer produces a bitmap of the same size`() {
        val width = 1024
        val height = 768
        val pixelStride = 4
        val rowStride = width * pixelStride

        val bitmap = ScreenFrameConverter.toBitmap(
            buffer = buffer(height, rowStride),
            width = width,
            height = height,
            pixelStride = pixelStride,
            rowStride = rowStride
        )

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    @Test
    fun `a single byte of padding per row is still handled`() {
        // One pixel of padding — the smallest case that would shear if ignored.
        val width = 100
        val height = 50
        val pixelStride = 4
        val rowStride = width * pixelStride + 4

        val bitmap = ScreenFrameConverter.toBitmap(
            buffer = buffer(height, rowStride),
            width = width,
            height = height,
            pixelStride = pixelStride,
            rowStride = rowStride
        )

        assertEquals(width, bitmap.width)
        assertEquals(height, bitmap.height)
    }

    @Test
    fun `heavy padding is handled`() {
        val width = 720
        val height = 1280
        val pixelStride = 4
        val rowStride = width * pixelStride + 256

        val bitmap = ScreenFrameConverter.toBitmap(
            buffer = buffer(height, rowStride),
            width = width,
            height = height,
            pixelStride = pixelStride,
            rowStride = rowStride
        )

        assertEquals(width, bitmap.width)
    }

    // --- assorted real device geometries -----------------------------------

    @Test
    fun `common device resolutions all round-trip to the right size`() {
        val geometries = listOf(
            Triple(1080, 1920, 4352),  // FHD, padded
            Triple(1080, 2400, 4352),  // tall FHD+
            Triple(1440, 3120, 5760),  // QHD+, exact
            Triple(720, 1600, 2880),   // HD+, exact
            Triple(1284, 2778, 5136)   // odd width, padded
        )

        geometries.forEach { (width, height, rowStride) ->
            val bitmap = ScreenFrameConverter.toBitmap(
                buffer = buffer(height, rowStride),
                width = width,
                height = height,
                pixelStride = 4,
                rowStride = rowStride
            )

            assertEquals("width wrong for ${width}x$height", width, bitmap.width)
            assertEquals("height wrong for ${width}x$height", height, bitmap.height)
            bitmap.recycle()
        }
    }

    // --- rejecting geometry that cannot be right ---------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `a rowStride narrower than the screen is rejected`() {
        // Physically impossible, and silently trusting it would read past the row.
        ScreenFrameConverter.toBitmap(
            buffer = buffer(10, 40),
            width = 100,
            height = 10,
            pixelStride = 4,
            rowStride = 40
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero width is rejected`() {
        ScreenFrameConverter.toBitmap(
            buffer = buffer(10, 40),
            width = 0,
            height = 10,
            pixelStride = 4,
            rowStride = 40
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero height is rejected`() {
        ScreenFrameConverter.toBitmap(
            buffer = buffer(1, 40),
            width = 10,
            height = 0,
            pixelStride = 4,
            rowStride = 40
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero pixelStride is rejected`() {
        ScreenFrameConverter.toBitmap(
            buffer = buffer(10, 40),
            width = 10,
            height = 10,
            pixelStride = 0,
            rowStride = 40
        )
    }

    // --- buffer sizing -----------------------------------------------------

    @Test
    fun `requiredBufferBytes accounts for padding, not just visible pixels`() {
        // Sizing from width * height * 4 would under-allocate and overflow.
        assertEquals(2400 * 4352, ScreenFrameConverter.requiredBufferBytes(2400, 4352))
    }

    @Test
    fun `requiredBufferBytes is exact for an unpadded frame`() {
        assertEquals(768 * 4096, ScreenFrameConverter.requiredBufferBytes(768, 4096))
    }
}
