package com.onemind.app.ui.composer

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onemind.app.ui.components.ExpressiveIconButton
import com.onemind.app.ui.components.ExpressiveTopBar
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking
import java.io.File

/**
 * M3 Expressive Composer Screen matching spec §4.5.
 *
 * Features:
 * - ExpressiveTopBar with Back navigation and dedicated Save filled button with pill status indicator.
 * - Clean, distraction-free note canvas with bodyLarge (16sp, line height 24sp) typography.
 * - Floating accessory pill docked above soft keyboard with 100dp pill corners and surfaceContainerHigh background.
 * - Preserves BackHandler and auto-save on leave commit behavior so no notes are lost.
 */
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

    PhoneFrame {
        ExpressiveTopBar(
            leading = {
                ExpressiveIconButton(onClick = handleBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val saved = uiState.showSavedIndicator
                    if (saved) {
                        Surface(
                            shape = PillShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Saved",
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = Tracking.Chip,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Button(
                        onClick = handleBack,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    // Attached images preview
                    if (uiState.imagePaths.isNotEmpty()) {
                        ImageAttachmentRow(
                            images = uiState.imagePaths,
                            onRemove = { index -> viewModel.onImageRemoved(index) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Distraction-free full-bleed note editor with bodyLarge typography (16sp, line height 24sp)
                    TextField(
                        value = uiState.text,
                        onValueChange = { viewModel.onTextChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = {
                            Text(
                                text = "What do you want to remember?",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    // Bottom clearance space for the floating accessory pill
                    Spacer(modifier = Modifier.height(76.dp))
                }

                // Floating accessory pill docked above the soft keyboard
                ComposerAccessoryPill(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
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
                    },
                    onAddTag = {
                        val currentText = uiState.text
                        val prefix = if (currentText.isEmpty() || currentText.endsWith(" ") || currentText.endsWith("\n")) "" else " "
                        viewModel.onTextChanged("$currentText$prefix#")
                    }
                )
            }
        }
    }
}

/**
 * Floating accessory pill docked above the soft keyboard matching M3 Expressive spec §4.5.
 *
 * Features:
 * - 100dp pill corners (`PillShape`), `surfaceContainerHigh` background, subtle elevation and border.
 * - Docked above keyboard using `imePadding` and `navigationBarsPadding`.
 * - Image attachment button, paste shortcut, and tags action.
 */
@Composable
private fun ComposerAccessoryPill(
    onAttachImage: () -> Unit,
    onPasteClipboard: () -> Unit,
    onAddTag: () -> Unit,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Image attachment action (40dp pill button with 48dp touch target)
            ExpressiveIconButton(
                onClick = onAttachImage,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Attach image"
                )
            }

            // Paste shortcut pill
            Surface(
                onClick = onPasteClipboard,
                modifier = Modifier.height(40.dp),
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Paste",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Paste",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Tag shortcut pill
            Surface(
                onClick = onAddTag,
                modifier = Modifier.height(40.dp),
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalOffer,
                        contentDescription = "Add tag",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )

                // Remove button
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
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
