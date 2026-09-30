package com.onemind.app.ui.feed

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.domain.model.ContentBlock
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.processing.StageStatus
import com.onemind.app.ui.components.CategoryChips
import com.onemind.app.ui.components.ExpressiveIconButton
import com.onemind.app.ui.components.ExpressiveTopBar
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.StateChip
import com.onemind.app.ui.theme.EmberGradient
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * M3 Expressive Memory Detail Screen matching spec §4.6.
 *
 * Features:
 * - ExpressiveTopBar with Back button, header title, and Share action.
 * - Hero media frame: 24dp rounded corners, high-resolution Coil image rendering, EmberGradient fallback.
 * - Metadata and AI blocks: grouped into distinct surfaceContainer cards with 16dp rounded corners.
 * - Bottom floating action toolbar pill: 100dp pill corners floating at bottom center with
 *   Edit (filled icon / primaryContainer), Share (tonal icon / secondaryContainer), and
 *   Delete (standard icon with error tint).
 * - Delete confirmation dialog with 28dp rounded corners and accessible 48dp action buttons.
 */
@Composable
fun MemoryDetailScreen(
    memoryId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onDeleteMemory: ((Long) -> Unit)? = null,
    viewModel: MemoryDetailViewModel = hiltViewModel()
) {
    val memory by viewModel.memory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(memoryId) {
        viewModel.loadMemory(memoryId)
    }

    PhoneFrame {
        ExpressiveTopBar(
            title = "Memory",
            leading = {
                ExpressiveIconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            trailing = {
                val currentMemory = memory
                if (currentMemory != null) {
                    ExpressiveIconButton(
                        onClick = { shareMemory(context, currentMemory) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share"
                        )
                    }
                }
            }
        )

        when (val m = memory) {
            null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    MemoryDetailContent(
                        memory = m,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bottom floating action toolbar pill
                    FloatingActionToolbar(
                        onEdit = { onNavigateToEdit(m.id) },
                        onShare = { shareMemory(context, m) },
                        onDelete = { showDeleteDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp)
                    )

                    // Delete confirmation dialog with 28dp rounded corners and 48dp action buttons
                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            shape = RoundedCornerShape(28.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(28.dp)
                                )
                            },
                            title = {
                                Text(
                                    text = "Delete memory?",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Text(
                                    text = "This memory and any associated local files will be permanently deleted. This action cannot be undone.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteDialog = false
                                        if (onDeleteMemory != null) {
                                            onDeleteMemory(m.id)
                                        } else {
                                            viewModel.deleteMemory(m.id)
                                        }
                                        onNavigateBack()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                                }
                            },
                            dismissButton = {
                                OutlinedButton(
                                    onClick = { showDeleteDialog = false },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = PillShape
                                ) {
                                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating pill toolbar at bottom center with Edit, Share, and Delete actions.
 */
@Composable
private fun FloatingActionToolbar(
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Edit (filled icon / primaryContainer)
            ExpressiveIconButton(
                onClick = onEdit,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit memory"
                )
            }

            // Share (tonal icon / secondaryContainer)
            ExpressiveIconButton(
                onClick = onShare,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share memory"
                )
            }

            // Delete (standard icon with error tint)
            ExpressiveIconButton(
                onClick = onDelete,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.error
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete memory"
                )
            }
        }
    }
}

/**
 * Hero media frame with 24dp rounded corners, high-resolution Coil image rendering,
 * and EmberGradient fallback.
 */
@Composable
private fun HeroMediaFrame(
    block: ContentBlock,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(EmberGradient)
    ) {
        val imageSource = block.content
        val imageModel = if (imageSource.startsWith("content://") || imageSource.startsWith("file://")) {
            Uri.parse(imageSource)
        } else {
            File(imageSource)
        }

        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(imageModel)
                .crossfade(true)
                .build(),
            contentDescription = "Memory image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Shared M3 Expressive card container for metadata and AI sections with 16dp rounded corners.
 */
@Composable
private fun DetailCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (icon != null) {
                    icon()
                }
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = Tracking.Eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@Composable
private fun MemoryDetailContent(
    memory: Memory,
    modifier: Modifier = Modifier
) {
    val imageBlocks = memory.imageBlocks()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Timestamp, source, state and category card
        InfoSection(memory = memory)

        // Hero media frame with 24dp rounded corners
        if (imageBlocks.isNotEmpty()) {
            HeroMediaFrame(block = imageBlocks.first())
        }

        // AI Summary section in surfaceContainer card with 16dp rounded corners
        SummarySection(memory = memory)

        // User note text blocks
        val textBlocks = memory.contentBlocks.filter { it.type == ContentType.TEXT }
        if (textBlocks.isNotEmpty()) {
            DetailCard(title = "Note") {
                textBlocks.forEach { block ->
                    Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Additional images beyond the first hero image
        if (imageBlocks.size > 1) {
            imageBlocks.drop(1).forEach { block ->
                HeroMediaFrame(block = block)
            }
        }

        // What the pipeline extracted from images and content
        RecognizedTextSection(memory = memory)
        ImageDescriptionSection(memory = memory)
        ExtractedMetadataSection(memory = memory)

        // Clearance spacer for bottom floating toolbar pill
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun InfoSection(memory: Memory) {
    DetailCard(title = "Info") {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatter.format(memory.createdAt),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (memory.processingState != ProcessingState.READY) {
                StateChip(state = memory.processingState)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { },
                label = {
                    Text(
                        text = memory.sourceType.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
            SourceRow(memory = memory)
        }

        if (memory.derived.categories.isNotEmpty()) {
            CategoryChips(categories = memory.derived.categories)
        }
    }
}

@Composable
private fun SummarySection(memory: Memory) {
    val summary = memory.derived.summary ?: return
    if (summary.status != StageStatus.SUCCESS || summary.summaryText.isBlank()) return

    DetailCard(
        title = "AI Summary",
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    ) {
        Text(
            text = summary.summaryText,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = buildString {
                val model = summary.providerModel
                if (model != null) append("summarised by $model · ") else append("summarised ")
                append("on device")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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

    DetailCard(title = "Text in images") {
        when {
            withText.isNotEmpty() -> {
                withText.forEach { result ->
                    Text(
                        text = result.extractedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            allEmpty -> StatusNote("No text found in these images.")
            allFailed -> StatusNote("Could not read these images.")
            else -> StatusNote("No text found.")
        }
    }
}

@Composable
private fun ImageDescriptionSection(memory: Memory) {
    val visionResults = memory.derived.visionResults
    if (visionResults.isEmpty()) return

    val described = visionResults.filter {
        it.status == StageStatus.SUCCESS && it.description.isNotBlank()
    }

    DetailCard(title = "Source content") {
        when {
            described.isNotEmpty() -> {
                described.forEach { result ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = result.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        result.providerModel?.let { model ->
                            Text(
                                text = "by $model",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            visionResults.all { it.status == StageStatus.NOT_SUPPORTED } ->
                StatusNote("Vision unavailable with your current model.")
            visionResults.all { it.status == StageStatus.FAILED } ->
                StatusNote("Could not describe these images.")
            else -> StatusNote("No description produced.")
        }
    }
}

@Composable
private fun ExtractedMetadataSection(memory: Memory) {
    val urls = memory.derived.urls
    val dates = memory.derived.dates
    val entities = memory.derived.entities
    val urlBlocks = memory.contentBlocks.filter { it.type == ContentType.URL }

    if (urls.isEmpty() && dates.isEmpty() && entities.isEmpty() && urlBlocks.isEmpty()) return

    if (urls.isNotEmpty() || urlBlocks.isNotEmpty()) {
        DetailCard(title = "Links") {
            val context = LocalContext.current
            urls.forEach { url ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openUrl(context, url.rawUrl) }
                        .semantics { role = Role.Button }
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
            urlBlocks.forEach { block ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openUrl(context, block.content) }
                        .semantics { role = Role.Button }
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

    if (dates.isNotEmpty()) {
        DetailCard(title = "Dates mentioned") {
            dates.forEach { date ->
                Text(
                    text = date.rawText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (entities.isNotEmpty()) {
        DetailCard(title = "Mentioned") {
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
}

@Composable
private fun StatusNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Share memory text and summary using Android Intent.
 */
private fun shareMemory(context: Context, memory: Memory) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        val shareText = buildString {
            val summary = memory.derived.summary?.summaryText
            if (!summary.isNullOrBlank()) {
                append(summary)
                append("\n\n")
            }
            val textContent = memory.contentBlocks
                .filter { it.type == ContentType.TEXT }
                .joinToString("\n") { it.content }
            if (textContent.isNotBlank()) {
                append(textContent)
            }
        }
        putExtra(Intent.EXTRA_TEXT, shareText.ifBlank { "Memory #${memory.id}" })
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, "Share Memory"))
    } catch (e: ActivityNotFoundException) {
        // Silently do nothing if no app can handle share
    }
}

/**
 * Open a link in whatever the user has set as their browser.
 */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: ActivityNotFoundException) {
        // No browser installed.
    }
}

