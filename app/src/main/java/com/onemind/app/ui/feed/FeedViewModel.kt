package com.onemind.app.ui.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.data.local.dao.MemoryDao
import com.onemind.app.data.processing.ProcessingScheduler
import com.onemind.app.data.storage.ImageFileStorage
import com.onemind.app.domain.model.ContentType
import com.onemind.app.domain.model.Memory
import com.onemind.app.domain.model.ProcessingState
import com.onemind.app.domain.model.SourceType
import com.onemind.app.domain.repository.MemoryRepository
import com.onemind.app.domain.search.FtsQuery
import com.onemind.app.domain.search.SearchOrchestrator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val memoryDao: MemoryDao,
    private val imageFileStorage: ImageFileStorage,
    private val processingScheduler: ProcessingScheduler,
    private val searchOrchestrator: SearchOrchestrator,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    /**
     * Raw keystrokes, kept separate from [_uiState] so debouncing does not have to
     * reason about unrelated state changes.
     */
    private val searchQueryFlow = MutableStateFlow("")

    init {
        observeMemories()
        loadSourceCounts()
        observeSearchQuery()
    }

    /**
     * Run a search a short pause after typing stops.
     *
     * `debounce` keeps a fast typist from issuing a query per keystroke;
     * `flatMapLatest` cancels a search whose results are already obsolete, which
     * matters because otherwise a slow early query can land after a fast later one
     * and overwrite it with results for text the user has moved on from.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    flow {
                        if (FtsQuery.build(query) == null) {
                            // Nothing usable typed — punctuation, or a single
                            // character. Emit no results and let the feed show.
                            emit(emptyList())
                            return@flow
                        }
                        emit(searchMemories(query))
                    }
                }
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results, isSearching = false) }
                }
        }
    }

    private suspend fun searchMemories(query: String): List<Memory> {
        // The orchestrator owns understanding, both retrieval paths, hard filters
        // and ranking. It returns Memories already hydrated and in order, so there
        // is nothing left here but to hand them to the UI.
        return searchOrchestrator.search(query).map { it.memory }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                // Only claim to be searching when there is something to search for,
                // so a stray keystroke does not flash a spinner.
                isSearching = FtsQuery.build(query) != null,
                // Drop stale results immediately rather than showing results for the
                // previous query under the new one.
                searchResults = if (query.isBlank()) emptyList() else it.searchResults
            )
        }
        searchQueryFlow.value = query
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
        }
        searchQueryFlow.value = ""
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

    private fun loadSourceCounts() {
        viewModelScope.launch {
            val counts = memoryDao.getSourceCounts()
            val options = counts.mapNotNull { row ->
                val sourceType = try {
                    SourceType.valueOf(row.sourceType)
                } catch (e: IllegalArgumentException) { null }
                    ?: return@mapNotNull null

                val label = resolveSourceLabel(sourceType, row.sourcePackage)
                SourceFilterOption(
                    sourceType = sourceType,
                    sourcePackage = row.sourcePackage,
                    label = label,
                    count = row.count
                )
            }
            _uiState.update { it.copy(availableSources = options) }
        }
    }

    fun setSourceFilter(filter: SourceFilter?) {
        _uiState.update { it.copy(sourceFilter = filter) }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    private fun resolveSourceLabel(sourceType: SourceType, sourcePackage: String?): String {
        if (sourcePackage != null) {
            try {
                val pm = appContext.packageManager
                val appInfo = pm.getApplicationInfo(sourcePackage, 0)
                return pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) { }
        }
        return when (sourceType) {
            SourceType.SCREENSHOT -> "Screenshots"
            SourceType.CLIPBOARD -> "Clipboard"
            SourceType.SHARE -> "Shared"
            SourceType.MANUAL -> "Manual"
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
            // Stop any queued enrichment for a Memory that is going away.
            processingScheduler.cancel(memory.id)

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

    /**
     * Re-queue a Memory whose enrichment failed. The pipeline drives the state
     * transition itself, so this only needs to hand the work back.
     */
    fun retryProcessing(memory: Memory) {
        if (memory.processingState != ProcessingState.FAILED) return
        processingScheduler.enqueue(memory.id)
    }

    companion object {
        /** Pause after the last keystroke before searching. */
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
