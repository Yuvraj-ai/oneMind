package com.onemind.app.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.LlmCapability
import com.onemind.app.domain.model.ModelInfo
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.StatusPill
import com.onemind.app.ui.theme.PillShape

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

    PhoneFrame {
        // No `leading` back arrow: this screen's signature carries no back callback and it
        // is reached straight from the welcome step, so there is nothing to navigate back
        // to without adding state the constraints forbid. Recorded as a deviation.
        HeroHeader(
            eyebrow = "Runs on this device",
            title = "Pick a mind"
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Select a local model to run on your device. Larger models are smarter but need more storage and RAM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isMeteredNetwork) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You're on mobile data. Consider using WiFi for the download.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            items(models) { model ->
                ModelCard(
                    model = model,
                    isRecommended = model.id == recommendedModelId,
                    isSelected = model.id == selectedModel?.id,
                    onClick = { onSelectModel(model) }
                )
            }
        }

        // Sticky CTA: a 56 dp-tall pill in `primary`, pinned below the scrolling list with
        // the navigation-bar inset, per `onboarding.html`.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = PillShape,
                enabled = selectedModel != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = selectedModel?.let { "Download ${it.displayName}" }
                        ?: "Download & Continue",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            TextButton(
                onClick = onChooseCloud,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Use a cloud provider instead")
            }
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

/**
 * One model, as a selectable pill row.
 *
 * The size badge on the left is the reference's idea and a good one: parameter count is the
 * single number that decides whether a model will run acceptably on a given phone, and
 * putting it in a fixed 48 dp slot makes six models comparable at a glance in a way three
 * metadata strings per row do not.
 *
 * The download icon appears only on the selected row. On every row it would read as six
 * things to download rather than one choice to confirm — and the sticky CTA below is what
 * actually starts the download.
 */
@Composable
private fun ModelCard(
    model: ModelInfo,
    isRecommended: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${formatParams(model.parameterCountB)}B",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (isRecommended) {
                        StatusPill(
                            label = "Recommended",
                            container = MaterialTheme.colorScheme.tertiary,
                            content = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }
                Text(
                    // One line instead of three separate metadata strings: size, format,
                    // and whether it can see. Everything that changes a decision, nothing
                    // that does not.
                    text = buildString {
                        append("${model.downloadSizeMb} MB · ${model.quantizationFormat}")
                        if (model.capabilities.contains(LlmCapability.VISION)) {
                            append(" · understands images")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * Render a parameter count as the reference does: "1B", "1.5B", "2B" — a whole number when
 * the fraction is zero, one decimal place otherwise. `parameterCountB` is a `Float`, so the
 * naive `"$it"` prints "1.0B"; this is formatting only and does not change the model.
 */
internal fun formatParams(count: Float): String =
    if (count == count.toLong().toFloat()) count.toLong().toString() else count.toString()
