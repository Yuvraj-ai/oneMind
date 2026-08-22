package com.onemind.app

import com.onemind.app.data.ai.ModelRegistry
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asserts that every model oneMind offers can actually be downloaded.
 *
 * This test exists because the first registry was populated from recall and every
 * URL in it was wrong — two 404, one 401 — while the unit tests passed throughout
 * by only ever checking that six entries existed and that RAM filtering worked.
 * Verifying shape while the substance is fiction is exactly the failure this
 * closes.
 *
 * It reaches the network, so it is skipped when the network is unavailable rather
 * than failing and being dismissed as flaky. A skip is honest; a red build that
 * everyone learns to ignore is worse than no test.
 */
class ModelUrlResolutionTest {

    private val registry = ModelRegistry()

    private data class HeadResult(val status: Int, val contentLength: Long)

    /**
     * HEAD the URL, following redirects. HuggingFace serves files via a redirect
     * to a CDN, so a bare HEAD without following would report 302 and prove
     * nothing.
     */
    private fun head(url: String): HeadResult? = try {
        var current = URL(url)
        var status: Int
        var length: Long
        var hops = 0

        while (true) {
            val conn = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = false
            }
            status = conn.responseCode
            length = conn.getHeaderFieldLong("content-length", -1L)

            val location = conn.getHeaderField("location")
            conn.disconnect()

            if (status !in 300..399 || location == null || hops >= MAX_REDIRECTS) break
            current = URL(current, location)
            hops++
        }

        HeadResult(status, length)
    } catch (_: Exception) {
        // Network unavailable, DNS failure, timeout. Indistinguishable from an
        // offline machine, so treated as "cannot check" rather than "broken".
        null
    }

    @Test
    fun `the embedding model URL resolves without authentication`() {
        val model = registry.embeddingModel
        val result = head(model.downloadUrl)
        assumeTrue("network unavailable, skipping", result != null)

        assertEquals(
            "${model.displayName} at ${model.downloadUrl} did not resolve. " +
                "A 401 means the model is gated and unusable without an account.",
            200,
            result!!.status
        )
    }

    @Test
    fun `the embedding model download size is accurate to within ten percent`() {
        val model = registry.embeddingModel
        val result = head(model.downloadUrl)
        assumeTrue("network unavailable, skipping", result != null)
        assumeTrue("no content-length header", result!!.contentLength > 0)

        val actualMb = result.contentLength / (1024.0 * 1024.0)
        val declaredMb = model.downloadSizeMb.toDouble()
        val drift = kotlin.math.abs(actualMb - declaredMb) / actualMb

        assertTrue(
            "${model.displayName} declares ${declaredMb.toInt()}MB but is " +
                "${actualMb.toInt()}MB. The download UI and the RAM-based " +
                "recommendation both rely on this being real.",
            drift < SIZE_TOLERANCE
        )
    }

    @Test
    fun `every offered generative model URL resolves`() {
        // Empty while local generative inference is deferred (ADR-0002). This
        // test is what makes restoring them safe.
        registry.generativeModels.forEach { model ->
            val result = head(model.downloadUrl)
            assumeTrue("network unavailable, skipping", result != null)
            assertEquals(
                "${model.displayName} at ${model.downloadUrl} did not resolve",
                200,
                result!!.status
            )
        }
    }

    @Test
    fun `the recorded future candidates still resolve`() {
        // Not offered to users yet, but checked so the research does not rot
        // before local inference is revisited.
        ModelRegistry.candidateLocalModels.forEach { model ->
            val result = head(model.downloadUrl)
            assumeTrue("network unavailable, skipping", result != null)
            assertEquals(
                "Candidate ${model.displayName} at ${model.downloadUrl} no longer " +
                    "resolves. Update docs/research/2026-08-on-device-inference.md.",
                200,
                result!!.status
            )
        }
    }

    @Test
    fun `candidate sizes are accurate, since they informed the deferral decision`() {
        ModelRegistry.candidateLocalModels.forEach { model ->
            val result = head(model.downloadUrl)
            assumeTrue("network unavailable, skipping", result != null)
            assumeTrue("no content-length", result!!.contentLength > 0)

            val actualMb = result.contentLength / (1024.0 * 1024.0)
            val drift = kotlin.math.abs(actualMb - model.downloadSizeMb) / actualMb

            assertTrue(
                "Candidate ${model.displayName} declares ${model.downloadSizeMb}MB " +
                    "but is ${actualMb.toInt()}MB",
                drift < SIZE_TOLERANCE
            )
        }
    }

    @Test
    fun `the check itself detects a broken URL`() {
        // Guards the guard. A test that cannot fail is worthless, and this one is
        // the safety net for a bug that already shipped once: the original
        // registry's URLs returned 404 and 401, and nothing noticed. This asserts
        // the mechanism distinguishes a miss from a hit, so a future bad URL
        // cannot pass by quietly.
        val brokenButReachable = head(
            "https://huggingface.co/litert-community/Gecko-110m-en/resolve/main/this-file-does-not-exist.tflite"
        )
        assumeTrue("network unavailable, skipping", brokenButReachable != null)

        assertNotEquals(
            "a nonexistent file must not report 200",
            200,
            brokenButReachable!!.status
        )
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5

        /** Files can be re-uploaded slightly changed; 10% catches fiction. */
        const val SIZE_TOLERANCE = 0.10
    }
}
