package com.onemind.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.onemind.app.data.ai.MediaPipeEmbeddingGenerator
import com.onemind.app.data.ai.ModelDownloadManager
import com.onemind.app.data.ai.ModelRegistry
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises real on-device embedding generation.
 *
 * This is the part no unit test can reach: whether the model downloads, loads,
 * and produces vectors that actually carry meaning. It also establishes the real
 * dimensionality, which `ModelRegistry` records rather than guesses.
 *
 * Downloads a 25MB model on first run.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingGeneratorTest {

    private lateinit var generator: MediaPipeEmbeddingGenerator
    private lateinit var downloadManager: ModelDownloadManager
    private lateinit var registry: ModelRegistry

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        downloadManager = ModelDownloadManager(context)
        registry = ModelRegistry()
        generator = MediaPipeEmbeddingGenerator(context, downloadManager, registry)

        val model = registry.embeddingModel
        if (!downloadManager.isModelDownloaded(model.id)) {
            // Terminal emission tells us whether it completed.
            downloadManager.downloadModel(model.id, model.downloadUrl).last()
        }
    }

    @After
    fun teardown() = runTest {
        generator.unload()
    }

    @Test
    fun modelDownloadsAndLoads() = runTest {
        val result = generator.load()

        assertTrue("load failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertTrue(generator.isReady)
    }

    @Test
    fun producesAVectorAndReportsItsDimensionality() = runTest {
        val result = generator.embed("Research Qwen models for on-device inference")

        assertTrue("embed failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val vector = result.getOrThrow()

        assertTrue("vector should not be empty", vector.isNotEmpty())

        // Dimensionality is read from the model, then asserted against what the
        // registry records. If a model update changes the vector width, this fails
        // here rather than silently writing mismatched vectors into the index.
        assertEquals(
            "dimensionality reported by the model disagrees with ModelRegistry",
            registry.embeddingModel.outputDimensions,
            vector.size
        )
        assertEquals(vector.size, generator.dimensions)
    }

    @Test
    fun theSameTextEmbedsIdentically() = runTest {
        val text = "Deploying quantized LLMs on Android"

        val first = generator.embed(text).getOrThrow()
        val second = generator.embed(text).getOrThrow()

        // Determinism matters: a memory re-embedded after an unrelated edit should
        // not drift in the index.
        assertArrayEquals(first, second, 0.0001f)
    }

    @Test
    fun relatedTextScoresHigherThanUnrelatedText() = runTest {
        // The property the whole feature rests on. Without this, semantic search
        // is just an expensive way to return arbitrary results.
        val query = generator.embed("running AI models locally on a phone").getOrThrow()
        val related = generator.embed("deploying quantized LLMs on Android devices").getOrThrow()
        val unrelated = generator.embed("a recipe for lemon drizzle cake").getOrThrow()

        val relatedScore = generator.similarity(query, related)
        val unrelatedScore = generator.similarity(query, unrelated)

        assertTrue(
            "related=$relatedScore should beat unrelated=$unrelatedScore",
            relatedScore > unrelatedScore
        )
    }

    @Test
    fun identicalTextScoresNearOne() = runTest {
        val text = "AI Summit 2026 in Bangalore"
        val a = generator.embed(text).getOrThrow()
        val b = generator.embed(text).getOrThrow()

        // L2Normalize is enabled, so self-similarity should be essentially 1.
        assertEquals(1.0f, generator.similarity(a, b), 0.01f)
    }

    @Test
    fun blankTextFailsRatherThanReturningAZeroVector() = runTest {
        // A zero vector would sit in the index matching everything weakly, which
        // is worse than having no vector at all.
        assertTrue(generator.embed("").isFailure)
        assertTrue(generator.embed("   \n ").isFailure)
    }

    @Test
    fun handlesTextLongerThanTheModelWindow() = runTest {
        // Memories can be long. Truncation inside the task is fine; a crash is not.
        val long = "on-device machine learning ".repeat(400)

        val result = generator.embed(long)

        assertTrue("failed on long input: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals(registry.embeddingModel.outputDimensions, result.getOrThrow().size)
    }

    @Test
    fun survivesUnloadAndReload() = runTest {
        val before = generator.embed("stable across reload").getOrThrow()

        generator.unload()
        assertFalse(generator.isReady)

        val after = generator.embed("stable across reload").getOrThrow()

        // Switching provider in Settings unloads models; vectors written before
        // and after must remain comparable.
        assertArrayEquals(before, after, 0.0001f)
    }

    @Test
    fun similarityRejectsMismatchedVectorSizes() {
        // Guards against comparing vectors from two different models, which would
        // otherwise produce a meaningless number rather than an error.
        try {
            generator.similarity(FloatArray(4), FloatArray(8))
            fail("expected mismatched sizes to be rejected")
        } catch (_: IllegalArgumentException) {
        }
    }
}
