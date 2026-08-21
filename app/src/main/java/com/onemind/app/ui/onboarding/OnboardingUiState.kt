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
    val isMeteredNetwork: Boolean = false
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
