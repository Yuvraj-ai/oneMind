package com.onemind.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onemind.app.domain.model.Category

/**
 * Category chips.
 *
 * Shared by the detail view and a search result so both render the vocabulary
 * identically — a category that reads one way in a list and another way on a
 * detail screen is not recognisably the same category.
 *
 * Wraps rather than scrolls horizontally: a card is not a place to hide content
 * behind a gesture the user has no reason to suspect is available.
 *
 * Moved here from `ui/feed/MemoryCard.kt` when that file was deleted, unchanged. It is not
 * the same thing as [CategoryChip] — this is a wrapping row in `secondaryContainer` at a
 * 6 dp radius, where the single chip follows DESIGN-GUIDE §5.4's `primaryContainer` at 12 dp.
 * `BentoCard` grows its own row from [CategoryChip] with a `+N` overflow marker, so there are
 * now two treatments of the same data. Reconciling them belongs with the screens that still
 * use this one, which the remaining-screens plan restyles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    categories: List<Category>,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty()) return

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEach { category ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
