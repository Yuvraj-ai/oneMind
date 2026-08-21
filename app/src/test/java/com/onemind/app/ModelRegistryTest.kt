package com.onemind.app

import com.onemind.app.data.ai.ModelRegistry
import com.onemind.app.domain.model.LlmCapability
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ModelRegistryTest {

    private lateinit var registry: ModelRegistry

    @Before
    fun setup() {
        registry = ModelRegistry()
    }

    @Test
    fun `registry has 6 generative models`() {
        assertEquals(6, registry.generativeModels.size)
    }

    @Test
    fun `all models have TEXT_GENERATION capability`() {
        registry.generativeModels.forEach { model ->
            assertTrue(
                "${model.displayName} should have TEXT_GENERATION",
                model.capabilities.contains(LlmCapability.TEXT_GENERATION)
            )
        }
    }

    @Test
    fun `embedding model has 384 dimensions`() {
        assertEquals(384, registry.embeddingModel.outputDimensions)
    }

    @Test
    fun `getCompatibleModels filters by RAM`() {
        val smallRamModels = registry.getCompatibleModels(2048)
        assertTrue(smallRamModels.all { it.requiredRamMb <= 2048 })
    }

    @Test
    fun `getCompatibleModels with 6GB returns all models`() {
        val allModels = registry.getCompatibleModels(6144)
        assertEquals(6, allModels.size)
    }

    @Test
    fun `getRecommendedModel returns largest fitting model with headroom`() {
        // 6GB = 6144MB, minus 1024 headroom = 5120MB budget
        val recommended = registry.getRecommendedModel(6144)
        assertNotNull(recommended)
        // Should recommend one of the 4B models (requiredRamMb = 4096, fits in 5120)
        assertTrue(recommended!!.requiredRamMb <= 5120)
    }

    @Test
    fun `getRecommendedModel returns null for insufficient RAM`() {
        val recommended = registry.getRecommendedModel(1024)
        assertNull(recommended)
    }

    @Test
    fun `getModelById returns correct model`() {
        val model = registry.getModelById("gemma3-1b-int4")
        assertNotNull(model)
        assertEquals("Gemma 3 1B", model!!.displayName)
    }

    @Test
    fun `getModelById returns null for unknown ID`() {
        val model = registry.getModelById("nonexistent-model")
        assertNull(model)
    }

    @Test
    fun `at least one model supports VISION`() {
        val visionModels = registry.generativeModels.filter {
            it.capabilities.contains(LlmCapability.VISION)
        }
        assertTrue("At least one model should support vision", visionModels.isNotEmpty())
    }
}
