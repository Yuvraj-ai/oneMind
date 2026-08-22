package com.onemind.app.ui.composer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
                    AnimatedVisibility(
                        visible = uiState.showSavedIndicator,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Text(
                            text = "Saved",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
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
                    .verticalScroll(rememberScrollState())
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

                // Text editor
                TextField(
                    value = uiState.text,
                    onValueChange = { viewModel.onTextChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 200.dp),
                    placeholder = {
                        Text("What do you want to remember?")
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.surface,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surface
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun ComposerBottomBar(
    onAttachImage: () -> Unit,
    onPasteClipboard: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onAttachImage) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach image"
                )
            }
            TextButton(onClick = onPasteClipboard) {
                Text("Paste")
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
