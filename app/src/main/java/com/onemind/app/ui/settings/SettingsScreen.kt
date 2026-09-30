package com.onemind.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.data.ai.ProviderType
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelInfo
import com.onemind.app.ui.components.ExpressiveIconButton
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.StatusPill
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.onboarding.CloudTestResult
import com.onemind.app.ui.onboarding.formatParams
import com.onemind.app.ui.theme.OneMindSuccess
import com.onemind.app.ui.theme.PillShape
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
                ExpressiveIconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
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
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // 1. General Preferences
            GeneralPreferenceSection(
                providerType = uiState.providerType,
                activeModelName = uiState.activeModelName,
                uiState = uiState,
                onBaseUrlChanged = { viewModel.onCloudBaseUrlChanged(it) },
                onApiKeyChanged = { viewModel.onCloudApiKeyChanged(it) },
                onModelNameChanged = { viewModel.onCloudModelNameChanged(it) },
                onVisionToggle = { viewModel.onCloudVisionToggle(it) },
                onTestConnection = { viewModel.onTestCloudConnection() },
                onConfirm = { viewModel.onConfirmCloudConfig() }
            )

            // 2. On-Device AI Models
            OnDeviceAiModelsSection(
                uiState = uiState,
                onShowPicker = { viewModel.onShowModelPicker() },
                onCancelDownload = { viewModel.onCancelDownload() }
            )

            // 3. Storage & Cache
            StorageCacheSection(
                storageUsedBytes = uiState.storageUsedBytes,
                onDeleteCached = { viewModel.onShowDeleteConfirmation() }
            )

            // 4. About oneMind
            AboutSection()
        }
    }

    // Model picker dialog with 28dp rounded corners and 48dp touch targets
    if (uiState.showModelPicker) {
        ModelPickerDialog(
            models = uiState.availableModels,
            cachedModelIds = uiState.cachedModelIds,
            activeModelId = uiState.activeModelId,
            onSelect = { viewModel.onSelectLocalModel(it) },
            onDismiss = { viewModel.onDismissModelPicker() }
        )
    }

    // Delete confirmation dialog with 28dp rounded corners and 48dp action buttons
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissDeleteConfirmation() },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Delete cached models?",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "All downloaded models except the currently active one will be removed. You can re-download them later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                Button(
                    onClick = { viewModel.onDeleteCachedModels() },
                    shape = PillShape,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.onDismissDeleteConfirmation() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * A labelled settings block with an uppercase eyebrow header.
 */
@Composable
private fun SettingsSection(
    label: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = Tracking.Eyebrow,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}

/**
 * 1. General Preferences: Active provider overview and Cloud configuration.
 */
@Composable
private fun GeneralPreferenceSection(
    providerType: ProviderType,
    activeModelName: String,
    uiState: SettingsUiState,
    onBaseUrlChanged: (String) -> Unit,
    onApiKeyChanged: (String) -> Unit,
    onModelNameChanged: (String) -> Unit,
    onVisionToggle: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onConfirm: () -> Unit
) {
    SettingsSection(label = "General") {
        // Active Provider Summary Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Active AI Provider",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = activeModelName.ifEmpty { "Not configured" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    label = when (providerType) {
                        ProviderType.LOCAL -> "Local"
                        ProviderType.CLOUD -> "Cloud"
                        ProviderType.NONE -> "None"
                    },
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Cloud Provider Configuration Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Cloud Provider",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "OpenAI, Groq, Together.ai, or Ollama",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val fieldShape = RoundedCornerShape(12.dp)
                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
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

                // Vision switch with M3 Expressive tokens
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Text(
                            text = "Supports vision",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.cloudSupportsVision,
                            onCheckedChange = onVisionToggle,
                            thumbContent = if (uiState.cloudSupportsVision) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize)
                                    )
                                }
                            } else null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant
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
                        selected = false,
                        onClick = onTestConnection,
                        enabled = canTest,
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Test") }
                    )
                    SegmentedButton(
                        selected = false,
                        onClick = onConfirm,
                        enabled = canConfirm,
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.heightIn(min = 48.dp),
                        label = { Text("Use cloud") }
                    )
                }

                when (uiState.cloudTestResult) {
                    CloudTestResult.TESTING -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Testing connection...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    CloudTestResult.SUCCESS -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = OneMindSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Connected! Tap 'Use cloud' to activate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OneMindSuccess
                        )
                    }
                    CloudTestResult.FAILED -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Connection failed. Please check credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    null -> Unit
                }
            }
        }
    }
}

/**
 * 2. On-Device AI Models: Local inference engine & "Change local model" pill card.
 */
@Composable
private fun OnDeviceAiModelsSection(
    uiState: SettingsUiState,
    onShowPicker: () -> Unit,
    onCancelDownload: () -> Unit
) {
    SettingsSection(label = "On-Device AI Models") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (uiState.isDownloading) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Downloading ${uiState.downloadModelName}...",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${uiState.downloadProgress}% completed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (uiState.downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(PillShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )

                            TextButton(
                                onClick = onCancelDownload,
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Cancel download")
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Inference Engine",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (uiState.providerType == ProviderType.LOCAL) {
                                    "Active model: ${uiState.activeModelName}"
                                } else {
                                    "No local model currently active"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Expressive .row-link pill card
                    val interaction = remember { MutableInteractionSource() }
                    val morph = rememberPressMorph(interaction, restCorner = 28.dp)
                    Surface(
                        onClick = onShowPicker,
                        interactionSource = interaction,
                        shape = RoundedCornerShape(morph.corner),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScale(morph)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.SwapHoriz,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Change local model",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Switch or download GGUF models",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (uiState.downloadError != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = uiState.downloadError,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 3. Storage & Cache Preferences: M3 storage meter and cached model clearing.
 */
@Composable
private fun StorageCacheSection(
    storageUsedBytes: Long,
    onDeleteCached: () -> Unit
) {
    SettingsSection(label = "Storage & Cache") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    val storageMb = storageUsedBytes / (1024 * 1024)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Model Storage",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$storageMb MB used by cached models",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Storage progress bar with M3 Expressive tokens
                val storageMb = storageUsedBytes / (1024 * 1024)
                val storageFraction = if (storageMb > 0) {
                    (storageMb / 4096f).coerceIn(0.02f, 1f)
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { storageFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(PillShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Text(
                    text = "Memories, embeddings, and database indexes never leave this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Accessible 48dp action button
                OutlinedButton(
                    onClick = onDeleteCached,
                    enabled = storageUsedBytes > 0,
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (storageUsedBytes > 0) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete cached models",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

/**
 * 4. About Preferences: App identity and on-device privacy guarantee.
 */
@Composable
private fun AboutSection() {
    SettingsSection(label = "About") {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "oneMind",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "v0.4.0 · Material 3 Expressive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "On-device personal memory and knowledge capture. All OCR, vector embeddings, and semantic search operate completely locally.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "100% On-Device · Zero Telemetry".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = Tracking.Eyebrow,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Model picker dialog with 28dp rounded corners and 48dp accessible touch targets.
 */
@Composable
private fun ModelPickerDialog(
    models: List<ModelInfo>,
    cachedModelIds: Set<String>,
    activeModelId: String?,
    onSelect: (ModelInfo) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Select local model",
                style = MaterialTheme.typography.titleLarge
            )
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { model ->
                    val isCached = model.id in cachedModelIds
                    val isActive = model.id == activeModelId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (!isActive) onSelect(model) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = "${formatParams(model.parameterCountB)}B · ${model.downloadSizeMb} MB" +
                                        if (model.capabilities.contains(LlmCapability.VISION)) " · Vision" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            when {
                                isActive -> Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                isCached -> Text(
                                    text = "Cached",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                else -> Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download needed",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
