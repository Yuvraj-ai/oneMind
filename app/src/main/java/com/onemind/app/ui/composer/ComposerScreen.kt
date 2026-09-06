package com.onemind.app.ui.composer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(
    memoryId: Long? = null,
    onNavigateBack: () -> Unit,
    viewModel: ComposerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    // Load existing memory if editing
    LaunchedEffect(memoryId) {
        if (memoryId != null && memoryId > 0L) {
            viewModel.loadMemory(memoryId)
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { viewModel.onImageAttached(it) }
    }

    // Commit on the way out, however the user leaves.
    val handleBack: () -> Unit = {
        viewModel.onLeaveComposer()
        onNavigateBack()
    }

    // The system back gesture has to route through the same commit as the toolbar
    // arrow. Without this it bypassed onLeaveComposer entirely: anything typed
    // inside the autosave window was lost outright, and an autosaved Memory stayed
    // in DRAFT — never enqueued, never enriched, never searchable. Back is how most
    // people leave a screen, so this was the likeliest way to lose content.
    BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Present in both states rather than fading in and out. The reference
                    // shows "Draft" from the start and switches it to "Draft saved"; a
                    // pill that appears from nowhere reads as an alert, when what it is
                    // reporting is that nothing has gone wrong.
                    val saved = uiState.showSavedIndicator
                    Surface(
                        shape = PillShape,
                        color = if (saved) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (saved) "Draft saved" else "Draft",
                                style = MaterialTheme.typography.labelSmall,
                                letterSpacing = Tracking.Chip,
                                color = if (saved) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            if (saved) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            ComposerBottomBar(
                onAttachImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPasteClipboard = {
                    val clip = clipboardManager.getText()
                    clip?.toString()?.let { text ->
                        if (text.isNotBlank()) {
                            viewModel.onClipboardPaste(text)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                // Attached images
                if (uiState.imagePaths.isNotEmpty()) {
                    ImageAttachmentRow(
                        images = uiState.imagePaths,
                        onRemove = { index -> viewModel.onImageRemoved(index) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // 52 vh, per `capture.html`: tall enough that the field is obviously the
                // point of the screen, short enough that the toolbar stays visible.
                val composerHeight = LocalConfiguration.current.screenHeightDp.dp * 0.52f

                TextField(
                    value = uiState.text,
                    onValueChange = { viewModel.onTextChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(composerHeight),
                    placeholder = {
                        Text(
                            text = "What do you want to remember?",
                            style = MaterialTheme.typography.displayMedium.copy(fontSize = 32.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    // Outfit at 32 sp: the reference sets the composer in the display face,
                    // which is what makes typing feel like writing rather than filling in
                    // a form.
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 32.sp,
                        lineHeight = 44.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        }
    }
}

/**
 * The pinned toolbar: attach, paste, and a note about where the work happens.
 *
 * A 1 dp top border rather than tonal elevation, which is what the reference uses and what
 * keeps the toolbar readable against a `surfaceContainerLow` fill on a dark background —
 * elevation alone is nearly invisible at these tonal steps.
 *
 * The attach button is 56 dp, not 48. It is the only control here that opens something, and
 * §5.5 names that size specifically.
 */
@Composable
private fun ComposerBottomBar(
    onAttachImage: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    onClick = onAttachImage,
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = "Attach image",
                            tint = MaterialTheme.colorScheme.onTertiary
                        )
                    }
                }

                Surface(
                    onClick = onPasteClipboard,
                    modifier = Modifier.height(48.dp),
                    shape = PillShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Paste",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "Auto-saves · processed on-device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ImageAttachmentRow(
    images: List<ImageAttachment>,
    onRemove: (Int) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(images) { index, image ->
            Box {
                val imageSource = image.thumbnailPath ?: image.canonicalPath ?: image.sourceUri
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(
                            if (imageSource.startsWith("content://") || imageSource.startsWith("file://")) {
                                Uri.parse(imageSource)
                            } else {
                                File(imageSource)
                            }
                        )
                        .crossfade(true)
                        .build(),
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                // Remove button
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove image",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
