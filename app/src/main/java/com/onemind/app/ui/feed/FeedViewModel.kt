package com.onemind.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.storage.ImageFileStorage
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val imageFileStorage: ImageFileStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        observeMemories()
    }

    private fun observeMemories() {
        viewModelScope.launch {
            memoryRepository.observeAllMemories().collect { memories ->
                _uiState.update {
                    it.copy(memories = memories, isLoading = false)
                }
            }
        }
    }

    /**
     * Request delete confirmation for a memory.
     */
    fun requestDelete(memory: Memory) {
        _uiState.update { it.copy(memoryToDelete = memory) }
    }

    /**
     * Dismiss the delete confirmation dialog.
     */
    fun dismissDelete() {
        _uiState.update { it.copy(memoryToDelete = null) }
    }

    /**
     * Confirm and execute the deletion of the pending memory.
     */
    fun confirmDelete() {
        val memory = _uiState.value.memoryToDelete ?: return
        _uiState.update { it.copy(memoryToDelete = null) }

        viewModelScope.launch {
            // Delete associated image files
            val imagePaths = memory.contentBlocks
                .filter { it.type == ContentType.IMAGE }
                .flatMap { block ->
                    listOfNotNull(block.content, block.thumbnailPath)
                }
            if (imagePaths.isNotEmpty()) {
                imageFileStorage.deleteImages(imagePaths)
            }

            // Delete the memory from database
            memoryRepository.deleteMemory(memory.id)
        }
    }
}
