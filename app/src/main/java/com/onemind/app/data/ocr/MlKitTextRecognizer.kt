package com.onemind.app.data.ocr

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.onemind.app.domain.processing.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR via Google ML Kit.
 *
 * Local by default and by design: OCR never needs a configured AI provider and
 * never sends an image anywhere. The Latin model is bundled into the APK, so it
 * works on first launch with no download and no network.
 */
@Singleton
class MlKitTextRecognizer @Inject constructor() : TextRecognizer {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(imagePath: String): Result<String> = withContext(Dispatchers.Default) {
        val file = File(imagePath)
        if (!file.exists()) {
            return@withContext Result.failure(
                IllegalArgumentException("Image file is missing: $imagePath")
            )
        }

        // Decode first, so an unreadable file fails here with a clear reason
        // rather than somewhere inside ML Kit.
        val bitmap = runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()
            ?: return@withContext Result.failure(
                IllegalArgumentException("Image could not be decoded: $imagePath")
            )

        try {
            // The stored image is already upright: capture and import both
            // normalise orientation before writing, so rotation is 0.
            val input = InputImage.fromBitmap(bitmap, 0)
            val text = recognizer.awaitText(input)
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Bridge ML Kit's Task API to a coroutine.
     *
     * Hand-rolled rather than pulling in kotlinx-coroutines-play-services for one
     * call site. Failures resume with the exception rather than cancelling the
     * continuation: a recognizer error is an error, and surfacing it as a
     * CancellationException would both misreport it and interfere with the
     * cancellation of the surrounding coroutine.
     */
    private suspend fun com.google.mlkit.vision.text.TextRecognizer.awaitText(
        image: InputImage
    ): String = suspendCancellableCoroutine { continuation ->
        process(image)
            .addOnSuccessListener { result -> continuation.resume(result.text) }
            .addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
}
