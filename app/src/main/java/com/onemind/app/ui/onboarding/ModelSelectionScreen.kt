package com.onemind.app.ui.onboarding

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelInfo

@Composable
fun ModelSelectionScreen(
    models: List<ModelInfo>,
    recommendedModelId: String?,
    selectedModel: ModelInfo?,
    isMeteredNetwork: Boolean,
    localModelsAvailable: Boolean,
    onSelectModel: (ModelInfo) -> Unit,
    onStartDownload: () -> Unit,
    onChooseCloud: () -> Unit,
    onSkip: () -> Unit
) {
    if (!localModelsAvailable) {
        NoLocalModelsScreen(onChooseCloud = onChooseCloud, onSkip = onSkip)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Choose your AI model",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a local model to run on your device. Larger models are smarter but need more storage and RAM.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isMeteredNetwork) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "You're on mobile data. Consider using WiFi for the download.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(models) { model ->
                ModelCard(
                    model = model,
                    isRecommended = model.id == recommendedModelId,
                    isSelected = model.id == selectedModel?.id,
                    onClick = { onSelectModel(model) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartDownload,
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedModel != null
        ) {
            Text("Download & Continue")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onChooseCloud,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use a cloud provider instead")
        }
    }
}

@Composable
private fun NoLocalModelsScreen(onChooseCloud: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Set up AI enrichment",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Honest about what works and what does not, rather than offering a
        // download that cannot run. See ADR-0002.
        Text(
            text = "oneMind reads text out of your screenshots on-device, with no " +
                "account and nothing leaving your phone. That part always works.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Summaries, categories and image descriptions need a language " +
                "model. On-device models aren't ready yet on Android, so for now " +
                "these come from an AI provider you choose and configure yourself.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "You can skip this and add it later. Saving and searching your " +
                "memories works either way.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onChooseCloud,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Configure a provider")
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                if (isRecommended) {
                    SuggestionChip(
                        onClick = { },
                        label = { Text("Recommended", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "${model.parameterCountB}B params",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${model.downloadSizeMb} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = model.quantizationFormat,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (model.capabilities.contains(LlmCapability.VISION)) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Supports vision (image understanding)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
