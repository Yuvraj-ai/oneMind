package com.onemind.app.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.ai.*
import com.onemind.app.domain.model.ModelInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRegistry: ModelRegistry,
    private val modelDownloadManager: ModelDownloadManager,
    private val providerManager: ProviderManager,
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        loadModels()
        checkNetwork()
    }

    private fun loadModels() {
        val availableRam = getDeviceRamMb()
        val compatible = modelRegistry.getCompatibleModels(availableRam)
        val recommended = modelRegistry.getRecommendedModel(availableRam)

        _uiState.update {
            it.copy(
                availableModels = compatible,
                recommendedModelId = recommended?.id,
                // No local generative runtime exists yet (ADR-0002), so the only
                // route to enrichment is a cloud provider. Onboarding says so
                // rather than offering a download that cannot run.
                localModelsAvailable = modelRegistry.hasLocalGenerativeModels
            )
        }
    }

    private fun checkNetwork() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(network)
        val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false

        _uiState.update { it.copy(isMeteredNetwork = isMetered) }
    }

    fun onProceedFromWelcome() {
        _uiState.update { it.copy(step = OnboardingStep.MODEL_SELECTION) }
    }

    fun onSelectModel(model: ModelInfo) {
        _uiState.update { it.copy(selectedModel = model) }
    }

    fun onStartDownload() {
        val model = _uiState.value.selectedModel ?: return

        _uiState.update {
            it.copy(
                step = OnboardingStep.DOWNLOADING,
                isDownloading = true,
                downloadProgress = 0,
                downloadError = null
            )
        }

        downloadJob = viewModelScope.launch {
            // Download generative model
            modelDownloadManager.downloadModel(model.id, model.downloadUrl).collect { progress ->
                when (progress) {
                    is DownloadProgress.Started -> {
                        _uiState.update { it.copy(isDownloading = true) }
                    }
                    is DownloadProgress.Downloading -> {
                        _uiState.update {
                            it.copy(
                                downloadProgress = progress.progressPercent,
                                downloadedMb = progress.bytesDownloaded / (1024 * 1024),
                                totalMb = progress.totalBytes / (1024 * 1024)
                            )
                        }
                    }
                    is DownloadProgress.Completed -> {
                        // Now download embedding model
                        downloadEmbeddingModel(model)
                    }
                    is DownloadProgress.Failed -> {
                        _uiState.update {
                            it.copy(
                                isDownloading = false,
                                downloadError = progress.error
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadEmbeddingModel(generativeModel: ModelInfo) {
        val embedding = modelRegistry.embeddingModel

        modelDownloadManager.downloadModel(embedding.id, embedding.downloadUrl).collect { progress ->
            when (progress) {
                is DownloadProgress.Completed -> {
                    // Both models downloaded — activate provider
                    activateLocalProvider(generativeModel)
                }
                is DownloadProgress.Failed -> {
                    _uiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadError = "Embedding model download failed: ${progress.error}"
                        )
                    }
                }
                is DownloadProgress.Downloading -> {
                    // Show continued progress (beyond 100% of generative = embedding progress)
                    // Keep at 100% for the main model, embedding is small and fast
                }
                is DownloadProgress.Started -> {}
            }
        }
    }

    private suspend fun activateLocalProvider(model: ModelInfo) {
        try {
            providerManager.activateLocal(model)
            onboardingPreferences.setActiveLocalModel(model.id)
            onboardingPreferences.setOnboardingComplete()
            _uiState.update {
                it.copy(
                    step = OnboardingStep.COMPLETE,
                    isDownloading = false
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isDownloading = false,
                    downloadError = "Failed to load model: ${e.message}"
                )
            }
        }
    }

    fun onCancelDownload() {
        downloadJob?.cancel()
        val model = _uiState.value.selectedModel
        model?.let { modelDownloadManager.cancelDownload(it.id) }
        _uiState.update {
            it.copy(
                step = OnboardingStep.MODEL_SELECTION,
                isDownloading = false,
                downloadProgress = 0,
                downloadError = null
            )
        }
    }

    fun onRetryDownload() {
        _uiState.update { it.copy(downloadError = null) }
        onStartDownload()
    }

    fun onChooseCloud() {
        _uiState.update { it.copy(step = OnboardingStep.CLOUD_CONFIG) }
    }

    /**
     * Proceed without any generative provider.
     *
     * Entirely viable: capture, OCR and search all work with no provider at all.
     * Only the interpretive layer - summaries, categories, image descriptions - is
     * absent, and stages record NOT_SUPPORTED for those.
     */
    fun onSkipProvider() {
        downloadEmbeddingModelThenFinish()
    }

    /**
     * Fetch the embedding model and finish onboarding.
     *
     * This runs on every route out of onboarding, because semantic search needs
     * it and it is the one model with a stable runtime (LiteRT 2.2.0). At ~115MB
     * it is a small enough ask to be unconditional.
     */
    private fun downloadEmbeddingModelThenFinish() {
        val embedding = modelRegistry.embeddingModel

        if (modelDownloadManager.isModelDownloaded(embedding.id)) {
            completeOnboarding()
            return
        }

        _uiState.update {
            it.copy(
                step = OnboardingStep.DOWNLOADING,
                isDownloading = true,
                downloadProgress = 0,
                downloadError = null
            )
        }

        downloadJob = viewModelScope.launch {
            modelDownloadManager
                .downloadModel(embedding.id, embedding.downloadUrl)
                .collect { progress ->
                    when (progress) {
                        is DownloadProgress.Downloading -> _uiState.update {
                            it.copy(
                                downloadProgress = progress.progressPercent,
                                downloadedMb = progress.bytesDownloaded / (1024 * 1024),
                                totalMb = progress.totalBytes / (1024 * 1024)
                            )
                        }
                        is DownloadProgress.Completed -> completeOnboarding()
                        is DownloadProgress.Failed -> _uiState.update {
                            it.copy(isDownloading = false, downloadError = progress.error)
                        }
                        is DownloadProgress.Started -> Unit
                    }
                }
        }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            onboardingPreferences.setOnboardingComplete()
            _uiState.update {
                it.copy(step = OnboardingStep.COMPLETE, isDownloading = false)
            }
        }
    }

    fun onCloudBaseUrlChanged(url: String) {
        _uiState.update { it.copy(cloudBaseUrl = url, cloudTestResult = null) }
    }

    fun onCloudApiKeyChanged(key: String) {
        _uiState.update { it.copy(cloudApiKey = key, cloudTestResult = null) }
    }

    fun onCloudModelNameChanged(name: String) {
        _uiState.update { it.copy(cloudModelName = name, cloudTestResult = null) }
    }

    fun onCloudVisionToggle(enabled: Boolean) {
        _uiState.update { it.copy(cloudSupportsVision = enabled) }
    }

    fun onTestCloudConnection() {
        val state = _uiState.value
        if (state.cloudBaseUrl.isBlank() || state.cloudApiKey.isBlank() || state.cloudModelName.isBlank()) return

        _uiState.update { it.copy(cloudTestResult = CloudTestResult.TESTING) }

        viewModelScope.launch {
            try {
                val config = CloudConfig(
                    baseUrl = state.cloudBaseUrl.trimEnd('/'),
                    apiKey = state.cloudApiKey.trim(),
                    modelName = state.cloudModelName.trim(),
                    supportsVision = state.cloudSupportsVision
                )
                providerManager.activateCloud(config)
                val result = providerManager.getProvider()?.generateText("Say hello in one word.")
                if (result?.isSuccess == true) {
                    _uiState.update { it.copy(cloudTestResult = CloudTestResult.SUCCESS) }
                } else {
                    providerManager.deactivate()
                    _uiState.update { it.copy(cloudTestResult = CloudTestResult.FAILED) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(cloudTestResult = CloudTestResult.FAILED) }
            }
        }
    }

    fun onConfirmCloudConfig() {
        val state = _uiState.value
        viewModelScope.launch {
            onboardingPreferences.setActiveCloudProvider(
                baseUrl = state.cloudBaseUrl.trimEnd('/'),
                apiKey = state.cloudApiKey.trim(),
                modelName = state.cloudModelName.trim(),
                supportsVision = state.cloudSupportsVision
            )
            // Search needs the embedding model regardless of the provider choice.
            downloadEmbeddingModelThenFinish()
        }
    }

    fun onBackToModelSelection() {
        _uiState.update { it.copy(step = OnboardingStep.MODEL_SELECTION) }
    }

    private fun getDeviceRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return (memInfo.totalMem / (1024 * 1024)).toInt()
    }
}
