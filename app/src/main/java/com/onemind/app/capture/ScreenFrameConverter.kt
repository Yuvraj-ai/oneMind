package com.onemind.app.capture

import android.graphics.Bitmap
import java.nio.ByteBuffer

/**
 * Turns a raw screen frame into a Bitmap.
 *
 * Extracted from the capture service because the interesting part is arithmetic,
 * not Android: an [android.media.ImageReader] hands back a buffer whose rows are
 * padded out to a hardware-friendly stride, so the buffer is usually *wider* than
 * the screen. Copying it straight into a Bitmap of the screen's dimensions
 * produces a skewed image; the padding has to be accounted for and then cropped
 * away.
 *
 * Getting this wrong is the classic MediaProjection bug and it is invisible until
 * you look at a screenshot on a device whose width is not a multiple of the
 * stride. Keeping it here means it can be tested directly with a synthetic
 * buffer.
 */
object ScreenFrameConverter {

    /**
     * Convert one frame's pixel buffer into a Bitmap of exactly [width] x [height].
     *
     * @param buffer RGBA_8888 pixels, row-padded to [rowStride] bytes per row.
     * @param width Real display width in pixels.
     * @param height Real display height in pixels.
     * @param pixelStride Bytes per pixel — 4 for RGBA_8888.
     * @param rowStride Bytes per row, including padding. Always >= width * pixelStride.
     */
    fun toBitmap(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        pixelStride: Int,
        rowStride: Int
    ): Bitmap {
        require(width > 0 && height > 0) { "width and height must be positive" }
        require(pixelStride > 0) { "pixelStride must be positive" }
        require(rowStride >= width * pixelStride) {
            "rowStride ($rowStride) cannot be less than width * pixelStride (${width * pixelStride})"
        }

        val rowPadding = rowStride - pixelStride * width
        val paddedWidth = width + rowPadding / pixelStride

        // Copy into a bitmap wide enough to include the padding, or the rows shear.
        val padded = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)

        // No padding: the padded bitmap is already the right size.
        if (paddedWidth == width) return padded

        // Crop the padding off the right edge.
        val cropped = Bitmap.createBitmap(padded, 0, 0, width, height)
        if (cropped !== padded) padded.recycle()
        return cropped
    }

    /**
     * Bytes a buffer must hold for the given geometry.
     *
     * Used to validate a frame before trusting it, and by tests to build a
     * correctly-sized synthetic buffer.
     */
    fun requiredBufferBytes(height: Int, rowStride: Int): Int = height * rowStride
}
