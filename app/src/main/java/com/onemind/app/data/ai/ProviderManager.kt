package com.onemind.app.data.ai

import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.LlmProvider
import com.onemind.app.domain.model.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the active LLM provider (local or cloud).
 * Singleton — there is one active provider at a time for the entire app.
 */
@Singleton
class ProviderManager @Inject constructor(
    private val localModelProvider: LocalModelProvider,
    private val cloudModelProvider: CloudModelProvider
) {
    private val _activeProvider = MutableStateFlow<LlmProvider?>(null)
    val activeProvider: StateFlow<LlmProvider?> = _activeProvider.asStateFlow()

    private val _providerType = MutableStateFlow(ProviderType.NONE)
    val providerType: StateFlow<ProviderType> = _providerType.asStateFlow()

    /**
     * Activate the local model provider with the given model.
     */
    suspend fun activateLocal(model: ModelInfo) {
        // Unload current if loaded
        _activeProvider.value?.unload()

        localModelProvider.configure(model)
        localModelProvider.load()

        _activeProvider.value = localModelProvider
        _providerType.value = ProviderType.LOCAL
    }

    /**
     * Activate the cloud model provider with the given config.
     */
    suspend fun activateCloud(config: CloudConfig) {
        // Unload current if loaded
        _activeProvider.value?.unload()

        cloudModelProvider.configure(config)
        cloudModelProvider.load()

        _activeProvider.value = cloudModelProvider
        _providerType.value = ProviderType.CLOUD
    }

    /**
     * Check whether a config actually works, without touching the active provider.
     *
     * Deliberately builds a throwaway [CloudModelProvider] rather than activating the
     * shared one. Testing used to activate it, which meant a mistyped API key
     * *replaced* a working provider with a broken one, or on the failure branch left
     * the app with none at all — while Settings still displayed the old provider as
     * current. The UI lied about a state the user had just broken.
     *
     * Nothing here is persisted. Committing is [activateCloud]'s job, called when the
     * user explicitly chooses the provider.
     */
    suspend fun testCloudConfig(config: CloudConfig): Result<String> {
        val probe = CloudModelProvider()
        probe.configure(config)
        return try {
            probe.load()
            probe.generateText("Say hello in one word.")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            probe.unload()
        }
    }

    /**
     * Unload the current provider and clear state.
     */
    suspend fun deactivate() {
        _activeProvider.value?.unload()
        _activeProvider.value = null
        _providerType.value = ProviderType.NONE
    }

    /**
     * Check if the active provider supports a given capability.
     */
    fun hasCapability(capability: LlmCapability): Boolean {
        return _activeProvider.value?.capabilities()?.contains(capability) == true
    }

    /**
     * Get the active provider, or null if none is loaded.
     */
    fun getProvider(): LlmProvider? = _activeProvider.value
}

/**
 * Which type of provider is currently active.
 */
enum class ProviderType {
    NONE,
    LOCAL,
    CLOUD
}
