package com.onemind.app.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.onemind.app.ui.components.ExpressiveIconButton
import com.onemind.app.ui.components.PhoneFrame
import com.onemind.app.ui.components.pressScale
import com.onemind.app.ui.components.rememberPressMorph
import com.onemind.app.ui.theme.PillShape
import com.onemind.app.ui.theme.Tracking

/**
 * Quick category suggestion chips displayed when the search query is blank.
 */
private val QUICK_SUGGESTIONS = listOf(
    "Screenshots",
    "Recent Notes",
    "Links",
    "Extracted Events"
)

/**
 * Natural language query examples demonstrating unified retrieval across keyword, meaning, and time.
 */
private val EXAMPLE_QUERIES = listOf(
    "that repo about embeddings someone sent me",
    "screenshots from last night",
    "the meeting I noted before midnight"
)

/**
 * M3 Expressive Search Screen.
 *
 * Features:
 * - Docked search bar surface with 28dp pill corners, surfaceContainerHigh background, and hairline outlineVariant border.
 * - Leading back affordance using [ExpressiveIconButton].
 * - Trailing clear button when query is not empty.
 * - Active vector search indicator: [LinearProgressIndicator] below the search bar when [SearchUiState.isSearching] is true.
 * - Interactive suggestion pills with 100dp pill corners (PillShape) and spring press scale.
 * - Warm tonal empty result state card in surfaceContainer when 0 results match.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onNavigateToMemory: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PhoneFrame {
        // Docked M3 Expressive search surface
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leading back affordance using ExpressiveIconButton
                ExpressiveIconButton(
                    onClick = onNavigateBack,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (uiState.query.isEmpty()) {
                        Text(
                            text = "Ask in your own words…",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                    )
                }

                // Trailing clear button when query is not empty
                if (uiState.query.isNotEmpty()) {
                    ExpressiveIconButton(
                        onClick = viewModel::clear,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        }

        // Active vector search indicator
        if (uiState.isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        }

        when {
            !uiState.isActive -> SuggestionsState(onSuggestion = viewModel::onQueryChanged)

            uiState.isSearching && uiState.results.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Active vector search indicator runs above; content remains clean
            }

            uiState.results.isEmpty() -> EmptyResultsState()

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
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
 * Empty query suggestion state featuring quick category suggestion chips and natural language prompts.
 *
 * Each pill is styled with surfaceContainerHigh, 100dp pill corners (PillShape), and spring press scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionsState(onSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick suggestion chips
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    text = "Quick suggestions",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = Tracking.Eyebrow,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_SUGGESTIONS.forEach { suggestion ->
                    SuggestionPill(
                        label = suggestion,
                        onClick = { onSuggestion(suggestion) }
                    )
                }
            }
        }

        // Example queries
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
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

            EXAMPLE_QUERIES.forEach { suggestion ->
                SuggestionPill(
                    label = suggestion,
                    icon = Icons.Default.Search,
                    onClick = { onSuggestion(suggestion) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(
            text = "Keyword, meaning and time are searched together — no filters to fiddle with.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/**
 * Interactive suggestion pill with 100dp pill corners, surfaceContainerHigh background,
 * and spring press scale feedback.
 */
@Composable
private fun SuggestionPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val morph = rememberPressMorph(interaction, restCorner = 100.dp, pressedCorner = 100.dp)

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = modifier.pressScale(morph),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Warm tonal empty result state card in surfaceContainer when query returned 0 results.
 */
@Composable
private fun EmptyResultsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "No memories matched",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Try describing it differently or searching for different keywords",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
