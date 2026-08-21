package com.onemind.app

import com.onemind.app.data.ai.CloudConfig
import com.onemind.app.data.ai.CloudModelProvider
import com.onemind.app.data.ai.LocalModelProvider
import com.onemind.app.data.ai.ProviderManager
import com.onemind.app.data.ai.ProviderType
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelFormat
import com.onemind.app.domain.model.ModelInfo
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProviderManagerTest {

    private lateinit var localProvider: LocalModelProvider
    private lateinit var cloudProvider: CloudModelProvider
    private lateinit var providerManager: ProviderManager

    @Before
    fun setup() {
        localProvider = mockk(relaxed = true)
        cloudProvider = mockk(relaxed = true)
        providerManager = ProviderManager(localProvider, cloudProvider)
    }

    @Test
    fun `initial state has no active provider`() {
        assertNull(providerManager.getProvider())
        assertEquals(ProviderType.NONE, providerManager.providerType.value)
    }

    @Test
    fun `activateLocal configures and loads local provider`() = runTest {
        val model = ModelInfo(
            id = "test-model",
            displayName = "Test",
            parameterCountB = 1.0f,
            downloadSizeMb = 500,
            downloadUrl = "https://example.com/model",
            quantizationFormat = "int4",
            requiredRamMb = 2048,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.MEDIAPIPE
        )

        providerManager.activateLocal(model)

        coVerify { localProvider.configure(model) }
        coVerify { localProvider.load() }
        assertEquals(ProviderType.LOCAL, providerManager.providerType.value)
        assertEquals(localProvider, providerManager.getProvider())
    }

    @Test
    fun `activateCloud configures and loads cloud provider`() = runTest {
        val config = CloudConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "sk-test",
            modelName = "gpt-4o-mini"
        )

        providerManager.activateCloud(config)

        verify { cloudProvider.configure(config) }
        coVerify { cloudProvider.load() }
        assertEquals(ProviderType.CLOUD, providerManager.providerType.value)
        assertEquals(cloudProvider, providerManager.getProvider())
    }

    @Test
    fun `switching providers unloads the previous one`() = runTest {
        val model = ModelInfo(
            id = "test", displayName = "Test", parameterCountB = 1.0f,
            downloadSizeMb = 500, downloadUrl = "https://example.com",
            quantizationFormat = "int4", requiredRamMb = 2048,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.MEDIAPIPE
        )

        providerManager.activateLocal(model)
        providerManager.activateCloud(CloudConfig("https://api.example.com", "key", "model"))

        // Local should have been unloaded when switching to cloud
        coVerify { localProvider.unload() }
    }

    @Test
    fun `deactivate unloads and clears`() = runTest {
        val model = ModelInfo(
            id = "test", displayName = "Test", parameterCountB = 1.0f,
            downloadSizeMb = 500, downloadUrl = "https://example.com",
            quantizationFormat = "int4", requiredRamMb = 2048,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.MEDIAPIPE
        )

        providerManager.activateLocal(model)
        providerManager.deactivate()

        assertNull(providerManager.getProvider())
        assertEquals(ProviderType.NONE, providerManager.providerType.value)
    }

    @Test
    fun `hasCapability delegates to active provider`() = runTest {
        every { localProvider.capabilities() } returns setOf(LlmCapability.TEXT_GENERATION)

        val model = ModelInfo(
            id = "test", displayName = "Test", parameterCountB = 1.0f,
            downloadSizeMb = 500, downloadUrl = "https://example.com",
            quantizationFormat = "int4", requiredRamMb = 2048,
            capabilities = setOf(LlmCapability.TEXT_GENERATION),
            format = ModelFormat.MEDIAPIPE
        )

        providerManager.activateLocal(model)

        assertTrue(providerManager.hasCapability(LlmCapability.TEXT_GENERATION))
        assertFalse(providerManager.hasCapability(LlmCapability.VISION))
    }
}
