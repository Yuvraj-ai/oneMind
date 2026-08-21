package com.onemind.app.ui.settings

import com.onemind.app.data.ai.ProviderType
import com.onemind.app.domain.model.ModelInfo
import com.onemind.app.ui.onboarding.CloudTestResult

data class SettingsUiState(
    /** Current active provider type */
    val providerType: ProviderType = ProviderType.NONE,
    /** Active model display name */
    val activeModelName: String = "",
    /** Active model ID (for local) */
    val activeModelId: String? = null,
    /** All available local models */
    val availableModels: List<ModelInfo> = emptyList(),
    /** Models that have been downloaded and are cached */
    val cachedModelIds: Set<String> = emptySet(),
    /** Total storage used by cached models in bytes */
    val storageUsedBytes: Long = 0,
    /** Whether a model download is in progress */
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadModelName: String = "",
    val downloadError: String? = null,
    /** Cloud configuration fields */
    val cloudBaseUrl: String = "",
    val cloudApiKey: String = "",
    val cloudModelName: String = "",
    val cloudSupportsVision: Boolean = false,
    val cloudTestResult: CloudTestResult? = null,
    /** Show model picker dialog */
    val showModelPicker: Boolean = false,
    /** Show delete confirmation dialog */
    val showDeleteConfirmation: Boolean = false
)
