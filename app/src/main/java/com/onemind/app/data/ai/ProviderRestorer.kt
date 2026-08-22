package com.onemind.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reinstates the user's chosen AI provider when the process starts.
 *
 * [ProviderManager] holds the active provider in memory only, and the only things
 * that ever set it were the onboarding and settings screens. So the provider existed
 * exactly as long as the process that configured it.
 *
 * That mattered far more than it looks. Enrichment runs in a WorkManager worker,
 * which Android typically starts in a *fresh* process — one where no screen has run,
 * so nothing had configured a provider. Summarisation, categorisation, metadata
 * extraction and vision would each find no provider available and record
 * NOT_SUPPORTED. Every AI feature in the app silently did nothing in the background,
 * while appearing correctly configured in Settings.
 *
 * Idempotent: activating a provider that is already active is harmless, so this can
 * be called from application start and again before background work without needing
 * to coordinate.
 */
@Singleton
class ProviderRestorer @Inject constructor(
    private val providerManager: ProviderManager,
    private val onboardingPreferences: OnboardingPreferences
) {

    /**
     * Restore the persisted provider, if any.
     *
     * Failures are swallowed deliberately. A provider that cannot be restored — an
     * unreachable endpoint, a revoked key — must not stop the app from starting or a
     * worker from doing the parts of its job that need no model. The stages already
     * treat an absent provider as an ordinary state.
     *
     * @return true if a provider is now active.
     */
    suspend fun restore(): Boolean {
        if (providerManager.activeProvider.value != null) return true

        return try {
            when (onboardingPreferences.getActiveProviderType()) {
                "CLOUD" -> {
                    val config = onboardingPreferences.getCloudConfig() ?: return false
                    providerManager.activateCloud(config)
                    true
                }
                // Local generative inference is deferred (ADR-0002), so there is no
                // local provider to restore. Left explicit rather than folded into the
                // else branch, so whoever revives local inference finds the hook.
                "LOCAL" -> false
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }
}
