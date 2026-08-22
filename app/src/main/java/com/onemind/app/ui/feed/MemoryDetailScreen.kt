package com.onemind.app.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.processing.StageStatus
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryDetailScreen(
    memoryId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: MemoryDetailViewModel = hiltViewModel()
) {
    val memory by viewModel.memory.collectAsStateWithLifecycle()

    LaunchedEffect(memoryId) {
        viewModel.loadMemory(memoryId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(memoryId) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val m = memory) {
            null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                MemoryDetailContent(
                    memory = m,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
        }
    }
}

@Composable
private fun MemoryDetailContent(
    memory: Memory,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timestamp
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
        Text(
            text = formatter.format(memory.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Source type badge
        SuggestionChip(
            onClick = { },
            label = {
                Text(
                    text = memory.sourceType.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Summary first: it says what the Memory is about, which is what the user
        // needs when scanning. Falls back to raw content when absent.
        SummarySection(memory = memory)

        CategoryChips(categories = memory.derived.categories)

        // Source
        SourceRow(memory = memory)

        // Content blocks
        memory.contentBlocks.sortedBy { it.position }.forEach { block ->
            ContentBlockView(block = block)
        }

        // What the pipeline read out of the images. Kept visually distinct from
        // the content above, because this is a machine's reading of the Memory,
        // not something the user wrote.
        RecognizedTextSection(memory = memory)
        ImageDescriptionSection(memory = memory)
        ExtractedMetadataSection(memory = memory)
    }
}

/**
 * Structured metadata the pipeline found.
 *
 * Links appear even when no model is configured, since they are found by regex.
 * Dates keep the wording the user actually saw rather than only a resolved
 * timestamp, because "sometime next spring" is real information that no
 * timestamp captures.
 */
@Composable
private fun ExtractedMetadataSection(memory: Memory) {
    val urls = memory.derived.urls
    val dates = memory.derived.dates
    val entities = memory.derived.entities

    if (urls.isEmpty() && dates.isEmpty() && entities.isEmpty()) return

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    if (urls.isNotEmpty()) {
        MetadataLabel("Links")
        urls.forEach { url ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = url.domain,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = url.rawUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (dates.isNotEmpty()) {
        MetadataLabel("Dates mentioned")
        dates.forEach { date ->
            Text(
                text = date.rawText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (entities.isNotEmpty()) {
        MetadataLabel("Mentioned")
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            entities.forEach { entity ->
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(entity.name, style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
        }
    }
}

@Composable
private fun SummarySection(memory: Memory) {
    val summary = memory.derived.summary ?: return
    if (summary.status != StageStatus.SUCCESS || summary.summaryText.isBlank()) return

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = summary.summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            summary.providerModel?.let { model ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "summarised by $model",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun MetadataLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun RecognizedTextSection(memory: Memory) {
    val ocrResults = memory.derived.ocrResults
    if (ocrResults.isEmpty()) return

    val withText = ocrResults.filter {
        it.status == StageStatus.SUCCESS && it.extractedText.isNotBlank()
    }
    val allEmpty = ocrResults.all { it.status == StageStatus.EMPTY }
    val allFailed = ocrResults.all { it.status == StageStatus.FAILED }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Text in images",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when {
        withText.isNotEmpty() -> {
            withText.forEach { result ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.extractedText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        // Say which of the three it is. "No text found" and "could not read the
        // image" are different facts and the user can act on the second one.
        allEmpty -> StatusNote("No text found in these images.")
        allFailed -> StatusNote("Could not read these images.")
        else -> StatusNote("No text found.")
    }
}

@Composable
private fun ImageDescriptionSection(memory: Memory) {
    val visionResults = memory.derived.visionResults
    if (visionResults.isEmpty()) return

    val described = visionResults.filter {
        it.status == StageStatus.SUCCESS && it.description.isNotBlank()
    }

    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Image description",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    when {
        described.isNotEmpty() -> {
            described.forEach { result ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = result.description,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        result.providerModel?.let { model ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "by $model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        // These three are genuinely different facts. Only the first is something
        // the user can act on, by choosing a vision-capable model.
        visionResults.all { it.status == StageStatus.NOT_SUPPORTED } ->
            StatusNote("Vision unavailable with your current model.")
        visionResults.all { it.status == StageStatus.FAILED } ->
            StatusNote("Could not describe these images.")
        else -> StatusNote("No description produced.")
    }
}

@Composable
private fun StatusNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ContentBlockView(block: ContentBlock) {
    when (block.type) {
        ContentType.TEXT -> {
            Text(
                text = block.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        ContentType.IMAGE -> {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(block.content))
                    .crossfade(true)
                    .build(),
                contentDescription = "Memory image",
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
        ContentType.URL -> {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = block.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
