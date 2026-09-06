package com.onemind.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
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
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.onboarding.CloudTestResult
import com.onemind.app.ui.onboarding.formatParams
import com.onemind.app.ui.theme.CardShapeLarge
import com.onemind.app.ui.theme.OneMindSuccess
import com.onemind.app.ui.theme.Tracking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhoneFrame {
        HeroHeader(
            eyebrow = "oneMind",
            title = "Settings",
            leading = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Current provider section
            CurrentProviderSection(
                providerType = uiState.providerType,
                activeModelName = uiState.activeModelName
            )

            // Change local model
            LocalModelSection(
                uiState = uiState,
                onShowPicker = { viewModel.onShowModelPicker() },
                onCancelDownload = { viewModel.onCancelDownload() }
            )

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

/**
 * A labelled settings block.
 *
 * The label sits outside the panel, as an eyebrow, rather than inside it as a heading. That
 * is what lets the panel itself be tonal and borderless while the page still reads as a
 * list of named sections.
 */
@Composable
private fun SettingsSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = Tracking.Eyebrow,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun CurrentProviderSection(
    providerType: ProviderType,
    activeModelName: String
) {
    SettingsSection(label = "Active AI provider") {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            // The summary-block treatment: the shared `CardShapeLarge` token (40 dp
            // corners except 16 dp top-right).
            shape = CardShapeLarge,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeModelName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = when (providerType) {
                            ProviderType.LOCAL -> "Local (on-device)"
                            ProviderType.CLOUD -> "Cloud provider"
                            ProviderType.NONE -> "Not configured"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
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
    SettingsSection(label = "Local model") {
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
    SettingsSection(label = "Cloud provider") {
        val fieldShape = MaterialTheme.shapes.medium
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = uiState.cloudBaseUrl,
            onValueChange = onBaseUrlChanged,
            label = { Text("Base URL") },
            placeholder = { Text("https://api.openai.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors
        )

        OutlinedTextField(
            value = uiState.cloudApiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = fieldShape,
            colors = fieldColors
        )

        OutlinedTextField(
            value = uiState.cloudModelName,
            onValueChange = onModelNameChanged,
            label = { Text("Model Name") },
            placeholder = { Text("gpt-4o-mini") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = fieldShape,
            colors = fieldColors
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Supports vision",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.cloudSupportsVision,
                    onCheckedChange = onVisionToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        val canTest = uiState.cloudBaseUrl.isNotBlank() &&
            uiState.cloudApiKey.isNotBlank() &&
            uiState.cloudModelName.isNotBlank() &&
            uiState.cloudTestResult != CloudTestResult.TESTING
        val canConfirm = uiState.cloudTestResult == CloudTestResult.SUCCESS

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                // Neither is a selection — they are two actions that happen to be
                // connected visually. `selected = false` on both keeps the group from
                // claiming one of them is the current state.
                selected = false,
                onClick = onTestConnection,
                enabled = canTest,
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Test") }
            )
            SegmentedButton(
                selected = false,
                onClick = onConfirm,
                enabled = canConfirm,
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Use cloud") }
            )
        }

        // Kept as its own row below the group. Inside it, a "Failed" label would sit
        // where a third action goes and read as one.
        when (uiState.cloudTestResult) {
            CloudTestResult.TESTING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
            CloudTestResult.SUCCESS -> Text(
                text = "Connected",
                style = MaterialTheme.typography.bodySmall,
                color = OneMindSuccess
            )
            CloudTestResult.FAILED -> Text(
                text = "Failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            null -> Unit
        }
    }
}

@Composable
private fun StorageSection(
    storageUsedBytes: Long,
    onDeleteCached: () -> Unit
) {
    SettingsSection(label = "Storage") {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val storageMb = storageUsedBytes / (1024 * 1024)
                Text(
                    text = "Cached models · $storageMb MB",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Memories and embeddings never leave this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    onClick = onDeleteCached,
                    enabled = storageUsedBytes > 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = "Delete cached models",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp)
                    )
                }
            }
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
                                    text = "${formatParams(model.parameterCountB)}B · ${model.downloadSizeMb} MB" +
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
