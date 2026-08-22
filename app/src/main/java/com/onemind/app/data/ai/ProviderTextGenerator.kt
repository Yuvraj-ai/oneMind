package com.onemind.app.data.ai

import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.processing.TextGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates text using whichever provider the user actively configured.
 *
 * As with [ProviderImageDescriber], the only route to a provider is
 * [ProviderManager.getProvider], which returns the user's choice. There is
 * deliberately no path that could reach a different provider, so a cloud call can
 * only ever be one the user opted into.
 */
@Singleton
class ProviderTextGenerator @Inject constructor(
    private val providerManager: ProviderManager
) : TextGenerator {

    override fun isAvailable(): Boolean =
        providerManager.hasCapability(LlmCapability.TEXT_GENERATION)

    override fun modelIdentifier(): String? =
        providerManager.getProvider()?.takeIf { isAvailable() }?.name

    override suspend fun generate(prompt: String, maxTokens: Int): Result<String> {
        val provider = providerManager.getProvider()
            ?: return Result.failure(IllegalStateException("No AI provider is active"))

        if (LlmCapability.TEXT_GENERATION !in provider.capabilities()) {
            return Result.failure(
                UnsupportedOperationException("${provider.name} cannot generate text")
            )
        }

        return provider.generateText(prompt, maxTokens)
    }
}
