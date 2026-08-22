package com.onemind.app

import com.onemind.app.data.ai.ModelRegistry
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelFormat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Properties of the registry that hold without touching the network.
 *
 * The earlier version of this class asserted "there are six models" and "RAM
 * filtering works", and passed happily while every URL in the registry was
 * broken. What it was missing is now in [ModelUrlResolutionTest]; what remains
 * here is deliberately about invariants rather than counts, so it cannot give
 * false comfort again.
 */
class ModelRegistryTest {

    private lateinit var registry: ModelRegistry

    @Before
    fun setup() {
        registry = ModelRegistry()
    }

    // --- the deferral (ADR-0002) -----------------------------------------

    @Test
    fun `no local generative models are offered while inference is deferred`() {
        // Offering a 1.5GB download that cannot run is worse than offering none.
        assertTrue(registry.generativeModels.isEmpty())
        assertFalse(registry.hasLocalGenerativeModels)
    }

    @Test
    fun `recommendation returns nothing rather than throwing when none are offered`() {
        // Onboarding must cope with an empty registry, not crash on it.
        assertNull(registry.getRecommendedModel(8192))
        assertTrue(registry.getCompatibleModels(8192).isEmpty())
        assertNull(registry.getModelById("anything"))
    }

    // --- the embedding model ---------------------------------------------

    @Test
    fun `the embedding model runs on the one format we can actually execute`() {
        // LiteRT 2.2.0 is stable. The other two formats have no stable runtime.
        assertEquals(ModelFormat.LITERT, registry.embeddingModel.format)
    }

    @Test
    fun `the embedding model declares its real dimensionality`() {
        // Gecko-110m emits 768. The vector column and every similarity
        // computation depend on this being right.
        assertEquals(768, registry.embeddingModel.outputDimensions)
    }

    @Test
    fun `the embedding model is small enough not to dominate first launch`() {
        assertTrue(
            "embedding model is ${registry.embeddingModel.downloadSizeMb}MB",
            registry.embeddingModel.downloadSizeMb < 250
        )
    }

    @Test
    fun `the embedding model is not a gated Gemma repository`() {
        // Every Gemma repo on LiteRT Community is gated, needing an authenticated
        // licence-accepting request. oneMind promises no accounts.
        assertFalse(
            registry.embeddingModel.downloadUrl.contains("gemma", ignoreCase = true)
        )
    }

    // --- invariants across everything we record --------------------------

    private val allRecorded
        get() = registry.generativeModels + ModelRegistry.candidateLocalModels

    @Test
    fun `every recorded model has an https huggingface url`() {
        allRecorded.forEach {
            assertTrue("${it.id}: ${it.downloadUrl}", it.downloadUrl.startsWith("https://"))
        }
        assertTrue(registry.embeddingModel.downloadUrl.startsWith("https://"))
    }

    @Test
    fun `no recorded model points at a gated repository`() {
        allRecorded.forEach {
            assertFalse(
                "${it.id} points at a gated Gemma repo and cannot be downloaded",
                it.downloadUrl.contains("gemma", ignoreCase = true)
            )
        }
    }

    @Test
    fun `every recorded model declares a plausible size`() {
        allRecorded.forEach {
            // A zero or negative size means nobody checked.
            assertTrue("${it.id} has size ${it.downloadSizeMb}", it.downloadSizeMb > 0)
        }
    }

    @Test
    fun `model ids are unique, since the id is the on-disk filename`() {
        val ids = allRecorded.map { it.id } + registry.embeddingModel.id
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every recorded model can at least generate text`() {
        allRecorded.forEach {
            assertTrue(
                "${it.id} cannot generate text",
                LlmCapability.TEXT_GENERATION in it.capabilities
            )
        }
    }

    @Test
    fun `required RAM always exceeds the download size`() {
        // Weights have to be resident to run. A model claiming to need less RAM
        // than its own file size has an unchecked number in it.
        allRecorded.forEach {
            assertTrue(
                "${it.id} claims ${it.requiredRamMb}MB RAM for a ${it.downloadSizeMb}MB file",
                it.requiredRamMb >= it.downloadSizeMb
            )
        }
    }

    // --- the deferred design question (ADR-0002) -------------------------

    @Test
    fun `vision-capable candidates exist, so vision does not require cloud`() {
        val vision = ModelRegistry.candidateLocalModels
            .filter { LlmCapability.VISION in it.capabilities }

        assertTrue("expected verified on-device vision candidates", vision.isNotEmpty())
    }

    @Test
    fun `vision candidates are distributed only as LiteRT-LM`() {
        // This is why on-device vision likely needs the alpha runtime, and part of
        // why local inference is deferred rather than partially shipped.
        ModelRegistry.candidateLocalModels
            .filter { LlmCapability.VISION in it.capabilities }
            .forEach { assertEquals(ModelFormat.LITERT_LM, it.format) }
    }

    @Test
    fun `choosing vision currently means choosing far fewer parameters`() {
        // The tradeoff the old flat list hid by badging vision as a bonus.
        val candidates = ModelRegistry.candidateLocalModels
        val biggestVision = candidates
            .filter { LlmCapability.VISION in it.capabilities }
            .minOf { it.parameterCountB }
        val biggestTextOnly = candidates
            .filter { LlmCapability.VISION !in it.capabilities }
            .maxOf { it.parameterCountB }

        assertTrue(
            "the smallest vision option should be materially smaller than the " +
                "largest text-only one, which is the tradeoff ADR-0002 defers",
            biggestVision < biggestTextOnly
        )
    }
}
