package com.onemind.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.ai.*
import com.onemind.app.domain.model.ModelInfo
import com.onemind.app.ui.onboarding.CloudTestResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val providerManager: ProviderManager,
    private val modelRegistry: ModelRegistry,
    private val modelDownloadManager: ModelDownloadManager,
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        loadCurrentState()
    }

    private fun loadCurrentState() {
        viewModelScope.launch {
            val providerType = providerManager.providerType.value
            val activeModelName = providerManager.getProvider()?.name ?: "None"
            val activeModelId = onboardingPreferences.activeModelId.first()
            val storageUsed = modelDownloadManager.getTotalStorageUsedBytes()
            val cachedIds = modelRegistry.generativeModels
                .filter { modelDownloadManager.isModelDownloaded(it.id) }
                .map { it.id }
                .toSet()

            _uiState.update {
                it.copy(
                    providerType = providerType,
                    activeModelName = activeModelName,
                    activeModelId = activeModelId,
                    availableModels = modelRegistry.generativeModels,
                    cachedModelIds = cachedIds,
                    storageUsedBytes = storageUsed
                )
            }
        }
    }

    fun onShowModelPicker() {
        _uiState.update { it.copy(showModelPicker = true) }
    }

    fun onDismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    fun onSelectLocalModel(model: ModelInfo) {
        _uiState.update { it.copy(showModelPicker = false) }

        if (modelDownloadManager.isModelDownloaded(model.id)) {
            // Already cached — just swap
            viewModelScope.launch {
                providerManager.activateLocal(model)
                onboardingPreferences.setActiveLocalModel(model.id)
                loadCurrentState()
            }
        } else {
            // Need to download first
            startModelDownload(model)
        }
    }

    private fun startModelDownload(model: ModelInfo) {
        _uiState.update {
            it.copy(
                isDownloading = true,
                downloadProgress = 0,
                downloadModelName = model.displayName,
                downloadError = null
            )
        }

        downloadJob = viewModelScope.launch {
            modelDownloadManager.downloadModel(model.id, model.downloadUrl).collect { progress ->
                when (progress) {
                    is DownloadProgress.Downloading -> {
                        _uiState.update { it.copy(downloadProgress = progress.progressPercent) }
                    }
                    is DownloadProgress.Completed -> {
                        // Activate the new model
                        providerManager.activateLocal(model)
                        onboardingPreferences.setActiveLocalModel(model.id)
                        _uiState.update { it.copy(isDownloading = false) }
                        loadCurrentState()
                    }
                    is DownloadProgress.Failed -> {
                        _uiState.update {
                            it.copy(isDownloading = false, downloadError = progress.error)
                        }
                    }
                    is DownloadProgress.Started -> {}
                }
            }
        }
    }

    fun onCancelDownload() {
        downloadJob?.cancel()
        _uiState.update { it.copy(isDownloading = false, downloadProgress = 0) }
    }

    // Cloud provider configuration

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
            val config = CloudConfig(
                baseUrl = state.cloudBaseUrl.trimEnd('/'),
                apiKey = state.cloudApiKey.trim(),
                modelName = state.cloudModelName.trim(),
                supportsVision = state.cloudSupportsVision
            )

            // Tested against a throwaway provider. Activating the shared one here
            // meant a mistyped key replaced a working provider with a broken one, or
            // left none at all, while this screen still showed the old one as current.
            val result = providerManager.testCloudConfig(config)

            _uiState.update {
                it.copy(
                    cloudTestResult = if (result.isSuccess) {
                        CloudTestResult.SUCCESS
                    } else {
                        CloudTestResult.FAILED
                    }
                )
            }
        }
    }

    fun onConfirmCloudConfig() {
        val state = _uiState.value
        viewModelScope.launch {
            val config = CloudConfig(
                baseUrl = state.cloudBaseUrl.trimEnd('/'),
                apiKey = state.cloudApiKey.trim(),
                modelName = state.cloudModelName.trim(),
                supportsVision = state.cloudSupportsVision
            )

            // Persist *and* activate. Previously this only persisted, so the provider
            // was live only as a side effect of having pressed Test — meaning a user
            // who skipped Test saved a config that did nothing until the next launch.
            onboardingPreferences.setActiveCloudProvider(
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                modelName = config.modelName,
                supportsVision = config.supportsVision
            )
            providerManager.activateCloud(config)
            loadCurrentState()
        }
    }

    // Storage management

    fun onShowDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    fun onDismissDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    fun onDeleteCachedModels() {
        viewModelScope.launch {
            val activeId = _uiState.value.activeModelId
            modelRegistry.generativeModels.forEach { model ->
                // Don't delete the currently active model
                if (model.id != activeId && modelDownloadManager.isModelDownloaded(model.id)) {
                    modelDownloadManager.deleteModel(model.id)
                }
            }
            _uiState.update { it.copy(showDeleteConfirmation = false) }
            loadCurrentState()
        }
    }
}
