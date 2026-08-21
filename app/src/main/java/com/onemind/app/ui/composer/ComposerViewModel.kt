package com.onemind.app.ui.composer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.storage.ImageFileStorage
import com.onemind.app.domain.model.*
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class ComposerViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val imageFileStorage: ImageFileStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        /** Auto-save debounce delay in milliseconds */
        const val AUTO_SAVE_DELAY_MS = 3000L
        /** How long the "saved" indicator shows */
        const val SAVED_INDICATOR_DURATION_MS = 1500L
    }

    private val _uiState = MutableStateFlow(ComposerUiState())
    val uiState: StateFlow<ComposerUiState> = _uiState.asStateFlow()

    private var autoSaveJob: Job? = null
    private var savedIndicatorJob: Job? = null

    /**
     * Load an existing memory for editing.
     */
    fun loadMemory(memoryId: Long) {
        _uiState.update { it.copy(isLoading = true, memoryId = memoryId) }

        viewModelScope.launch {
            val memory = memoryRepository.getMemoryById(memoryId)
            if (memory != null) {
                val text = memory.contentBlocks
                    .filter { it.type == ContentType.TEXT }
                    .joinToString("\n") { it.content }

                val images = memory.contentBlocks
                    .filter { it.type == ContentType.IMAGE }
                    .map { block ->
                        ImageAttachment(
                            sourceUri = block.content,
                            canonicalPath = block.content,
                            thumbnailPath = block.thumbnailPath
                        )
                    }

                _uiState.update {
                    it.copy(
                        text = text,
                        imagePaths = images,
                        isLoading = false,
                        memoryId = memoryId
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Called when the user changes text in the editor.
     * Resets the auto-save debounce timer.
     */
    fun onTextChanged(newText: String) {
        _uiState.update { it.copy(text = newText) }
        scheduleAutoSave()
    }

    /**
     * Called when the user attaches an image via the photo picker.
     */
    fun onImageAttached(uri: Uri) {
        val attachment = ImageAttachment(sourceUri = uri.toString())
        _uiState.update { it.copy(imagePaths = it.imagePaths + attachment) }
        scheduleAutoSave()
    }

    /**
     * Remove an attached image by index.
     */
    fun onImageRemoved(index: Int) {
        _uiState.update {
            it.copy(imagePaths = it.imagePaths.toMutableList().apply { removeAt(index) })
        }
        scheduleAutoSave()
    }

    /**
     * Called when the user pastes text from clipboard.
     */
    fun onClipboardPaste(clipText: String) {
        val currentText = _uiState.value.text
        val newText = if (currentText.isEmpty()) clipText else "$currentText\n$clipText"
        _uiState.update { it.copy(text = newText) }
        scheduleAutoSave()
    }

    /**
     * Called when the user leaves the composer (back navigation).
     * Commits the memory: transitions to SAVED state.
     */
    fun onLeaveComposer() {
        autoSaveJob?.cancel()

        viewModelScope.launch {
            val state = _uiState.value
            if (state.text.isBlank() && state.imagePaths.isEmpty()) {
                // Nothing to save — if we had a draft, delete it
                state.memoryId?.let { memoryRepository.deleteMemory(it) }
                return@launch
            }

            val memoryId = saveMemory()
            if (memoryId != null) {
                // Transition from DRAFT to SAVED
                try {
                    memoryRepository.transitionState(memoryId, ProcessingState.SAVED)
                } catch (_: Exception) {
                    // Already in SAVED or beyond — that's fine
                }
                _uiState.update { it.copy(isCommitted = true) }
            }
        }
    }

    /**
     * Schedule an auto-save after the debounce delay.
     */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DELAY_MS)
            saveMemory()
            showSavedIndicator()
        }
    }

    /**
     * Show the "saved" indicator briefly.
     */
    private fun showSavedIndicator() {
        _uiState.update { it.copy(showSavedIndicator = true) }
        savedIndicatorJob?.cancel()
        savedIndicatorJob = viewModelScope.launch {
            delay(SAVED_INDICATOR_DURATION_MS)
            _uiState.update { it.copy(showSavedIndicator = false) }
        }
    }

    /**
     * Persist the current composer state as a Memory (DRAFT state).
     * Creates new or updates existing.
     * Returns the memory ID.
     */
    private suspend fun saveMemory(): Long? {
        val state = _uiState.value
        if (state.text.isBlank() && state.imagePaths.isEmpty()) return null

        // Process any new image attachments that haven't been optimized yet
        val processedImages = state.imagePaths.map { attachment ->
            if (attachment.canonicalPath != null) {
                attachment // Already processed
            } else {
                processImageAttachment(attachment)
            }
        }

        // Update state with processed images
        _uiState.update { it.copy(imagePaths = processedImages) }

        // Build content blocks
        val contentBlocks = mutableListOf<ContentBlock>()
        var position = 0

        // Text block
        if (state.text.isNotBlank()) {
            contentBlocks.add(
                ContentBlock(
                    position = position++,
                    type = ContentType.TEXT,
                    content = state.text
                )
            )
        }

        // Image blocks
        processedImages.forEach { img ->
            if (img.canonicalPath != null) {
                contentBlocks.add(
                    ContentBlock(
                        position = position++,
                        type = ContentType.IMAGE,
                        content = img.canonicalPath,
                        thumbnailPath = img.thumbnailPath
                    )
                )
            }
        }

        // Extract URLs from text and add as URL blocks
        extractUrls(state.text).forEach { url ->
            contentBlocks.add(
                ContentBlock(
                    position = position++,
                    type = ContentType.URL,
                    content = url
                )
            )
        }

        val existingId = state.memoryId
        return if (existingId != null) {
            // Update existing memory
            val existing = memoryRepository.getMemoryById(existingId)
            if (existing != null) {
                memoryRepository.updateMemory(
                    existing.copy(
                        updatedAt = Instant.now(),
                        contentBlocks = contentBlocks
                    )
                )
                // If memory was READY, transition to EDITED for reprocessing
                if (existing.processingState == ProcessingState.READY) {
                    try {
                        memoryRepository.transitionState(existingId, ProcessingState.EDITED)
                    } catch (_: Exception) { }
                }
            }
            existingId
        } else {
            // Create new memory
            val memory = Memory(
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                sourceType = SourceType.MANUAL,
                processingState = ProcessingState.DRAFT,
                contentBlocks = contentBlocks
            )
            val newId = memoryRepository.createMemory(memory)
            _uiState.update { it.copy(memoryId = newId) }
            newId
        }
    }

    /**
     * Process a new image attachment: copy from URI to internal storage,
     * optimize, and generate thumbnail.
     */
    private suspend fun processImageAttachment(attachment: ImageAttachment): ImageAttachment {
        return try {
            val uri = Uri.parse(attachment.sourceUri)
            val tempFile = copyUriToTempFile(uri)
            val (canonicalPath, thumbnailPath) = imageFileStorage.saveImage(tempFile.absolutePath)
            tempFile.delete()
            attachment.copy(canonicalPath = canonicalPath, thumbnailPath = thumbnailPath)
        } catch (e: Exception) {
            // If image processing fails, keep the attachment without paths
            attachment
        }
    }

    /**
     * Copy content from a URI to a temporary file.
     */
    private suspend fun copyUriToTempFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("img_", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Could not open URI: $uri")
        tempFile
    }

    /**
     * Simple URL extraction from text using regex.
     */
    private fun extractUrls(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val urlPattern = Regex(
            """https?://[^\s<>"{}|\\^`\[\]]+""",
            RegexOption.IGNORE_CASE
        )
        return urlPattern.findAll(text).map { it.value }.distinct().toList()
    }
}
