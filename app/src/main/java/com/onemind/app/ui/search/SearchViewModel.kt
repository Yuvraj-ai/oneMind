package com.onemind.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onemind.app.domain.search.FtsQuery
import com.onemind.app.domain.search.SearchOrchestrator
import com.onemind.app.domain.search.SearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
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

/**
 * Search, on its own screen and in its own view model.
 *
 * This state used to live in `FeedViewModel`, which meant every feed — including one
 * belonging to a user who never searched — was constructed with a debounced query flow and
 * a `SearchOrchestrator` attached. The reference puts search behind a bar on the feed that
 * is a navigation affordance, so the state follows it.
 *
 * Nothing below `SearchOrchestrator` changed. The debounce, the `flatMapLatest` and the
 * `FtsQuery.build` null check are carried over exactly, comments included, because those
 * comments record defects that were fixed and the reasoning still applies.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchOrchestrator: SearchOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * Raw keystrokes, kept separate from [_uiState] so debouncing does not have to
     * reason about unrelated state changes.
     */
    private val queryFlow = MutableStateFlow("")

    init {
        observeQuery()
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
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { query ->
                    flow {
                        if (FtsQuery.build(query) == null) {
                            // Nothing usable typed — punctuation, or a single
                            // character. Emit no results and let the empty state show.
                            emit(emptyList<SearchResult>() to emptyList<String>())
                            return@flow
                        }
                        emit(searchOrchestrator.search(query) to FtsQuery.terms(query))
                    }
                }
                .collect { (results, terms) ->
                    _uiState.update {
                        it.copy(results = results, terms = terms, isSearching = false)
                    }
                }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                // Only claim to be searching when there is something to search for,
                // so a stray keystroke does not flash a spinner.
                isSearching = FtsQuery.build(query) != null,
                // Drop stale results immediately rather than showing results for the
                // previous query under the new one.
                results = if (query.isBlank()) emptyList() else it.results
            )
        }
        queryFlow.value = query
    }

    fun clear() {
        _uiState.update {
            it.copy(query = "", results = emptyList(), terms = emptyList(), isSearching = false)
        }
        queryFlow.value = ""
    }

    companion object {
        /** Pause after the last keystroke before searching. */
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}

data class SearchUiState(
    /** Exactly what the user typed. */
    val query: String = "",

    /** Matches for [query], best first. Meaningless while [isSearching]. */
    val results: List<SearchResult> = emptyList(),

    /**
     * Terms the current search matched on, for highlighting snippets.
     *
     * Held here rather than recomputed per card: every result needs the same list, and
     * re-parsing the query for each one would repeat the work on every frame.
     */
    val terms: List<String> = emptyList(),

    val isSearching: Boolean = false
) {
    /**
     * True once the user has typed something the search can actually act on.
     *
     * Keyed on whether a query could be *built*, not on whether text was typed. Those
     * differ: `FtsQuery.build` returns null for a single character or a query made only of
     * stopwords, so keying on raw text made typing "the" or "a" show a hard "No memories
     * found" — telling the user their memories were missing when nothing had been searched
     * for.
     */
    val isActive: Boolean get() = FtsQuery.build(query) != null
}
