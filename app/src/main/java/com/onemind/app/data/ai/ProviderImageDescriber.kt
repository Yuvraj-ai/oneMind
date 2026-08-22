package com.onemind.app.data.ai

import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.processing.ImageDescriber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes images using whichever provider the user actively configured.
 *
 * The only route to a provider here is [ProviderManager.getProvider], which
 * returns the one the user chose. There is deliberately no path that could send
 * an image to a provider merely because its credentials happen to be stored, so
 * a cloud upload can only ever be one the user opted into.
 */
@Singleton
class ProviderImageDescriber @Inject constructor(
    private val providerManager: ProviderManager
) : ImageDescriber {

    override fun isAvailable(): Boolean =
        providerManager.hasCapability(LlmCapability.VISION)

    override fun modelIdentifier(): String? =
        providerManager.getProvider()?.takeIf { isAvailable() }?.name

    override suspend fun describe(imagePath: String, prompt: String): Result<String> {
        val provider = providerManager.getProvider()
            ?: return Result.failure(IllegalStateException("No AI provider is active"))

        if (LlmCapability.VISION !in provider.capabilities()) {
            return Result.failure(
                UnsupportedOperationException("${provider.name} cannot describe images")
            )
        }

        return provider.describeImage(imagePath, prompt)
    }
}
