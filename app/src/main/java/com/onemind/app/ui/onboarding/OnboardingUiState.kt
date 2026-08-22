package com.onemind.app.ui.onboarding

import com.onemind.app.domain.model.ModelInfo

/**
 * UI state for the onboarding flow.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val availableModels: List<ModelInfo> = emptyList(),
    val recommendedModelId: String? = null,
    val selectedModel: ModelInfo? = null,
    val downloadProgress: Int = 0,
    val downloadedMb: Long = 0,
    val totalMb: Long = 0,
    val downloadError: String? = null,
    val isDownloading: Boolean = false,
    /** Cloud config fields */
    val cloudBaseUrl: String = "https://api.openai.com",
    val cloudApiKey: String = "",
    val cloudModelName: String = "",
    val cloudSupportsVision: Boolean = false,
    val cloudTestResult: CloudTestResult? = null,
    val isMeteredNetwork: Boolean = false,
    /**
     * Whether any local generative model can be offered. False while local
     * inference is deferred (ADR-0002), which changes what the model step says.
     */
    val localModelsAvailable: Boolean = false,

    /**
     * Display name of the embedding model.
     *
     * Shown on the download screen, because that is the only download every route
     * out of onboarding performs. Without it the header read "Downloading " with a
     * blank name for every user.
     */
    val embeddingModelName: String = "the search model"
)

enum class OnboardingStep {
    WELCOME,
    MODEL_SELECTION,
    DOWNLOADING,
    CLOUD_CONFIG,
    COMPLETE
}

enum class CloudTestResult {
    SUCCESS,
    FAILED,
    TESTING
}
