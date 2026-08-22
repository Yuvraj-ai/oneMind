package com.onemind.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.data.ocr.MlKitTextRecognizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Exercises the real ML Kit recognizer against real images.
 *
 * This is the part of OCR that cannot be unit tested: whether the pinned ML Kit
 * version actually reads text on device. [OcrStageTest] covers the aggregation
 * logic with a fake recognizer; this covers the library itself.
 */
@RunWith(AndroidJUnit4::class)
class MlKitTextRecognizerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var recognizer: MlKitTextRecognizer

    @Before
    fun setup() {
        recognizer = MlKitTextRecognizer()
    }

    /** Renders [text] large and high-contrast, which is what OCR reads best. */
    private fun imageContaining(text: String, name: String = "text.png"): File {
        val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(
                text,
                50f,
                180f,
                Paint().apply {
                    color = Color.BLACK
                    textSize = 90f
                    isAntiAlias = true
                }
            )
        }
        return writeBitmap(bitmap, name)
    }

    private fun blankImage(name: String = "blank.png"): File {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.WHITE)
        return writeBitmap(bitmap, name)
    }

    private fun writeBitmap(bitmap: Bitmap, name: String): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun readsTextOutOfAnImage() = runTest {
        val file = imageContaining("Qwen")

        val result = recognizer.recognize(file.absolutePath)

        assertTrue("recognition should succeed", result.isSuccess)
        // Asserting containment rather than equality: OCR is inherently fuzzy and
        // a brittle exact-match here would fail on harmless rendering differences.
        assertTrue(
            "expected to find 'Qwen', got '${result.getOrNull()}'",
            result.getOrNull()?.contains("Qwen", ignoreCase = true) == true
        )
    }

    @Test
    fun readsMultipleWords() = runTest {
        val file = imageContaining("AI Summit")

        val text = recognizer.recognize(file.absolutePath).getOrNull().orEmpty()

        assertTrue("got '$text'", text.contains("AI", ignoreCase = true))
        assertTrue("got '$text'", text.contains("Summit", ignoreCase = true))
    }

    @Test
    fun aBlankImageSucceedsWithNoText() = runTest {
        val file = blankImage()

        val result = recognizer.recognize(file.absolutePath)

        // The distinction the whole status model rests on: this is success with
        // nothing found, not a failure.
        assertTrue("a blank image is not an error", result.isSuccess)
        assertTrue(
            "expected no text, got '${result.getOrNull()}'",
            result.getOrNull().isNullOrBlank()
        )
    }

    @Test
    fun anUndecodableFileFails() = runTest {
        val notAnImage = tempFolder.newFile("junk.png")
        notAnImage.writeText("this is not image data")

        val result = recognizer.recognize(notAnImage.absolutePath)

        assertTrue("garbage should not decode", result.isFailure)
    }

    @Test
    fun aMissingFileFailsWithoutThrowing() = runTest {
        val result = recognizer.recognize("/does/not/exist.webp")

        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun survivesAWebpImage() = runTest {
        // WebP is what ImageFileStorage actually writes, so the recognizer has to
        // handle it, not just the PNGs the other tests use.
        val bitmap = Bitmap.createBitmap(1000, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(
                "Bangalore",
                50f,
                180f,
                Paint().apply {
                    color = Color.BLACK
                    textSize = 90f
                    isAntiAlias = true
                }
            )
        }
        val file = tempFolder.newFile("text.webp")
        @Suppress("DEPRECATION")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it)
        }
        bitmap.recycle()

        val text = recognizer.recognize(file.absolutePath).getOrNull().orEmpty()

        assertTrue("got '$text'", text.contains("Bangalore", ignoreCase = true))
    }
}
