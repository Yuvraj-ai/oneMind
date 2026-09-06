package com.onemind.app.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.ui.components.HeroHeader
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * Examples of what to type, not filters, and verbatim from `search.html`.
 *
 * They are chosen rather than arbitrary: one is purely semantic, one names a source and a
 * time, one is relative time. Between them they demonstrate the claim the footnote then
 * makes — keyword, meaning and time all go in the one bar. Three vaguely semantic examples
 * would leave the user with no reason to believe the other two dimensions exist.
 */
private val SUGGESTIONS = listOf(
    "that repo about embeddings someone sent me",
    "screenshots from last night",
    "the meeting I noted before midnight"
)

/**
 * Unified retrieval behind one bar.
 *
 * No filter chips, and that is a locked product decision rather than an omission: context
 * belongs in the query text. The suggestion pills below the bar are examples of things to
 * type, and tapping one types it.
 *
 * Three states, kept distinct because each asks something different of the user — wait,
 * try different words, or carry on. Collapsing them would tell someone their search failed
 * while it was still running, and the empty-query case is not a failed search at all.
 */
@Composable
fun SearchScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhoneFrame {
        HeroHeader(
            eyebrow = "Unified retrieval",
            title = "What are you looking for?",
            // `leading`, not `trailing`: the reference puts the back button on its own row
            // above the eyebrow. In `trailing` it would sit beside the title on the right,
            // reading as an action rather than a way out.
            leading = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )

        TextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp),
            placeholder = { Text("Ask in your own words…") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Default.Close, "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )

        when {
            !uiState.isActive -> SuggestionsState(onSuggestion = viewModel::onQueryChanged)

            uiState.isSearching && uiState.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No memories matched",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Try describing it differently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 20.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "${uiState.results.size} " +
                            if (uiState.results.size == 1) "memory matched" else "memories matched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(uiState.results, key = { it.memory.id }) { result ->
                    SearchResultCard(
                        result = result,
                        queryTerms = uiState.terms,
                        onClick = { onNavigateToMemory(result.memory.id) },
                        onLongClick = { }
                    )
                }
            }
        }
    }
}

/**
 * What to type, before anything has been typed.
 *
 * The heading carries the reference's `Sparkles` mark, and the footnote is its exact
 * wording: the point being made is that there is nothing to configure, so paraphrasing it
 * into "all from one bar" would drop the half that says *no filters to fiddle with*.
 */
@Composable
private fun SuggestionsState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Try asking",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = Tracking.Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SUGGESTIONS.forEach { suggestion ->
            Surface(
                onClick = { onSuggestion(suggestion) },
                modifier = Modifier.fillMaxWidth(),
                shape = PillShape,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Text(
            text = "Keyword, meaning and time are searched together — no filters to fiddle with.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
