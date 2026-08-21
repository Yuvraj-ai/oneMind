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
