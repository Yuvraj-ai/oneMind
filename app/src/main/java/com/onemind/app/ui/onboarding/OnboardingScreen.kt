package com.onemind.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Navigate away when complete
    if (uiState.step == OnboardingStep.COMPLETE) {
        onOnboardingComplete()
        return
    }

    AnimatedContent(
        targetState = uiState.step,
        label = "onboarding_step"
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> WelcomeScreen(
                onProceed = { viewModel.onProceedFromWelcome() }
            )
            OnboardingStep.MODEL_SELECTION -> ModelSelectionScreen(
                models = uiState.availableModels,
                recommendedModelId = uiState.recommendedModelId,
                selectedModel = uiState.selectedModel,
                isMeteredNetwork = uiState.isMeteredNetwork,
                onSelectModel = { viewModel.onSelectModel(it) },
                onStartDownload = { viewModel.onStartDownload() },
                onChooseCloud = { viewModel.onChooseCloud() }
            )
            OnboardingStep.DOWNLOADING -> DownloadScreen(
                modelName = uiState.selectedModel?.displayName ?: "",
                progressPercent = uiState.downloadProgress,
                downloadedMb = uiState.downloadedMb,
                totalMb = uiState.totalMb,
                error = uiState.downloadError,
                onCancel = { viewModel.onCancelDownload() },
                onRetry = { viewModel.onRetryDownload() }
            )
            OnboardingStep.CLOUD_CONFIG -> CloudConfigScreen(
                baseUrl = uiState.cloudBaseUrl,
                apiKey = uiState.cloudApiKey,
                modelName = uiState.cloudModelName,
                supportsVision = uiState.cloudSupportsVision,
                testResult = uiState.cloudTestResult,
                onBaseUrlChanged = { viewModel.onCloudBaseUrlChanged(it) },
                onApiKeyChanged = { viewModel.onCloudApiKeyChanged(it) },
                onModelNameChanged = { viewModel.onCloudModelNameChanged(it) },
                onVisionToggle = { viewModel.onCloudVisionToggle(it) },
                onTestConnection = { viewModel.onTestCloudConnection() },
                onConfirm = { viewModel.onConfirmCloudConfig() },
                onBack = { viewModel.onBackToModelSelection() }
            )
            OnboardingStep.COMPLETE -> { /* handled above */ }
        }
    }
}
