package com.onemind.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.data.ai.ProviderType
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.ui.onboarding.CloudTestResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current provider section
            CurrentProviderSection(
                providerType = uiState.providerType,
                activeModelName = uiState.activeModelName
            )

            HorizontalDivider()

            // Change local model
            LocalModelSection(
                uiState = uiState,
                onShowPicker = { viewModel.onShowModelPicker() },
                onCancelDownload = { viewModel.onCancelDownload() }
            )

            HorizontalDivider()

            // Cloud provider config
            CloudProviderSection(
                uiState = uiState,
                onBaseUrlChanged = { viewModel.onCloudBaseUrlChanged(it) },
                onApiKeyChanged = { viewModel.onCloudApiKeyChanged(it) },
                onModelNameChanged = { viewModel.onCloudModelNameChanged(it) },
                onVisionToggle = { viewModel.onCloudVisionToggle(it) },
                onTestConnection = { viewModel.onTestCloudConnection() },
                onConfirm = { viewModel.onConfirmCloudConfig() }
            )

            HorizontalDivider()

            // Storage management
            StorageSection(
                storageUsedBytes = uiState.storageUsedBytes,
                onDeleteCached = { viewModel.onShowDeleteConfirmation() }
            )
        }
    }

    // Model picker dialog
    if (uiState.showModelPicker) {
        ModelPickerDialog(
            models = uiState.availableModels,
            cachedModelIds = uiState.cachedModelIds,
            activeModelId = uiState.activeModelId,
            onSelect = { viewModel.onSelectLocalModel(it) },
            onDismiss = { viewModel.onDismissModelPicker() }
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteConfirmation() },
            title = { Text("Delete cached models?") },
            text = { Text("All downloaded models except the currently active one will be removed. You can re-download them later.") },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteCachedModels() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CurrentProviderSection(
    providerType: ProviderType,
    activeModelName: String
) {
    Column {
        Text("Active AI Provider", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = activeModelName,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = when (providerType) {
                        ProviderType.LOCAL -> "Local (on-device)"
                        ProviderType.CLOUD -> "Cloud provider"
                        ProviderType.NONE -> "Not configured"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LocalModelSection(
    uiState: SettingsUiState,
    onShowPicker: () -> Unit,
    onCancelDownload: () -> Unit
) {
    Column {
        Text("Local Model", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.isDownloading) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Downloading ${uiState.downloadModelName}...")
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uiState.downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.downloadProgress}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onCancelDownload) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            OutlinedButton(
                onClick = onShowPicker,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change local model")
            }
        }

        if (uiState.downloadError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.downloadError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun CloudProviderSection(
    uiState: SettingsUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onVisionToggle: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onConfirm: () -> Unit
) {
    Column {
        Text("Cloud Provider", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.cloudBaseUrl,
            onValueChange = onBaseUrlChanged,
            label = { Text("Base URL") },
            placeholder = { Text("https://api.openai.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.cloudApiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.cloudModelName,
            onValueChange = onModelNameChanged,
            label = { Text("Model Name") },
            placeholder = { Text("gpt-4o-mini") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Supports vision", modifier = Modifier.weight(1f))
            Switch(checked = uiState.cloudSupportsVision, onCheckedChange = onVisionToggle)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onTestConnection,
                enabled = uiState.cloudBaseUrl.isNotBlank()
                    && uiState.cloudApiKey.isNotBlank()
                    && uiState.cloudModelName.isNotBlank()
                    && uiState.cloudTestResult != CloudTestResult.TESTING
            ) {
                Text("Test")
            }

            when (uiState.cloudTestResult) {
                CloudTestResult.TESTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                CloudTestResult.SUCCESS -> Text("Connected!", color = MaterialTheme.colorScheme.primary)
                CloudTestResult.FAILED -> Text("Failed", color = MaterialTheme.colorScheme.error)
                null -> {}
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onConfirm,
                enabled = uiState.cloudTestResult == CloudTestResult.SUCCESS
            ) {
                Text("Use Cloud")
            }
        }
    }
}

@Composable
private fun StorageSection(
    storageUsedBytes: Long,
    onDeleteCached: () -> Unit
) {
    Column {
        Text("Storage", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        val storageMb = storageUsedBytes / (1024 * 1024)
        Text(
            text = "Cached models: $storageMb MB",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onDeleteCached,
            enabled = storageUsedBytes > 0
        ) {
            Text("Delete cached models")
        }
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<com.onemind.app.domain.model.ModelInfo>,
    cachedModelIds: Set<String>,
    activeModelId: String?,
    onSelect: (com.onemind.app.domain.model.ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select local model") },
        text = {
            Column {
                models.forEach { model ->
                    val isCached = model.id in cachedModelIds
                    val isActive = model.id == activeModelId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!isActive) onSelect(model) }
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "${model.parameterCountB}B · ${model.downloadSizeMb} MB" +
                                        if (model.capabilities.contains(LlmCapability.VISION)) " · Vision" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            when {
                                isActive -> Icon(
                                    Icons.Default.CheckCircle,
                                    "Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                isCached -> Text(
                                    "Cached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                else -> Icon(
                                    Icons.Default.Download,
                                    "Download needed",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
