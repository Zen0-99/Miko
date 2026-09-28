package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.filter.FilterTagChip
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.metadata.tmdb.TmdbSubGenreKeywords
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Discovery facet sheet (Phase 15-06) — the library `FullFilterSheet`
 * pattern (modal bottom sheet, sectioned LazyColumn, Clear/Apply
 * footer) adapted to facets its radio-only option model can't express:
 * tri-state genre chips, grouped sub-genre keyword chips, a year range
 * and rating/vote floors, plus a single-select sort section.
 *
 * Edits apply live through [onFilterChange] (the screen model debounces
 * the discover reload); Apply just closes, matching FullFilterSheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryFilterSheet(
    filter: DiscoveryFilter,
    mediaType: MediaType,
    onFilterChange: (DiscoveryFilter) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
        ) {
            // Header — same layout as FullFilterSheet (title + close).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(MR.strings.action_filter),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(MR.strings.action_close),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "sort") {
                    FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_sort))
                    DiscoverySort.entries.forEach { sort ->
                        SortOptionRow(
                            label = stringResource(sort.labelRes),
                            isSelected = filter.sort == sort,
                            onClick = { onFilterChange(filter.copy(sort = sort)) },
                        )
                    }
                }

                item(key = "genres") {
                    FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_genre))
                    // Horizontal pill scroll — one scrolling row instead of
                    // a wrapping FlowRow so the sheet stays compact.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TmdbGenres.forType(mediaType).forEach { genre ->
                            val state = filter.genreStates[genre.id] ?: TriState.DISABLED
                            FilterTagChip(
                                label = genre.name,
                                state = state,
                                enabled = true,
                                onToggle = {
                                    onFilterChange(filter.withGenreState(genre.id, state.next()))
                                },
                            )
                        }
                    }
                }

                // Sub-genre rows appear only under included (ENABLED_IS)
                // genres — nothing renders until a genre is chosen.
                val includedGenreNames = TmdbGenres.forType(mediaType)
                    .filter { filter.genreStates[it.id] == TriState.ENABLED_IS }
                    .map { it.name }
                val subGenreGroups = TmdbSubGenreKeywords.groupsFor(includedGenreNames)
                if (subGenreGroups.isNotEmpty()) {
                    item(key = "subgenres") {
                        FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_subgenre))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            subGenreGroups.forEach { (genreName, subGenres) ->
                                Text(
                                    text = genreName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    subGenres.forEach { sub ->
                                        val state = filter.keywordStates[sub.keywordId]
                                            ?: TriState.DISABLED
                                        FilterTagChip(
                                            label = sub.name,
                                            state = state,
                                            enabled = true,
                                            onToggle = {
                                                onFilterChange(
                                                    filter.withKeywordState(sub.keywordId, state.next()),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "year") {
                    FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_year))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        YearField(
                            value = filter.yearFrom,
                            label = stringResource(AYMR.strings.discovery_filter_year_from),
                            onValueChange = { onFilterChange(filter.copy(yearFrom = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        YearField(
                            value = filter.yearTo,
                            label = stringResource(AYMR.strings.discovery_filter_year_to),
                            onValueChange = { onFilterChange(filter.copy(yearTo = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item(key = "rating") {
                    FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_rating))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RATING_STEPS.forEach { step ->
                            val selected = filter.voteAverageGte == step
                            FilterTagChip(
                                label = "★ $step+",
                                state = if (selected) TriState.ENABLED_IS else TriState.DISABLED,
                                enabled = true,
                                onToggle = {
                                    onFilterChange(
                                        filter.copy(
                                            voteAverageGte = if (selected) null else step,
                                            // Rating floor without a vote
                                            // floor is noise — default
                                            // 100 votes (query maps it).
                                            voteCountGte = if (selected) {
                                                null
                                            } else {
                                                filter.voteCountGte
                                                    ?: TmdbDiscover.DEFAULT_VOTE_COUNT_GTE
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        VOTE_STEPS.forEach { step ->
                            val selected = filter.voteCountGte == step
                            FilterTagChip(
                                label = "$step+ ${stringResource(AYMR.strings.discovery_filter_votes)}",
                                state = if (selected) TriState.ENABLED_IS else TriState.DISABLED,
                                enabled = true,
                                onToggle = {
                                    onFilterChange(
                                        filter.copy(voteCountGte = if (selected) null else step),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // Bottom buttons — Clear / Apply, matching FullFilterSheet.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onFilterChange(DiscoveryFilter()) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.clear_filters),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Button(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = stringResource(MR.strings.action_apply),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

private val RATING_STEPS = listOf(5.0, 6.0, 7.0, 8.0)
private val VOTE_STEPS = listOf(100, 500, 1000)

@Composable
private fun FilterSectionLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun SortOptionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun YearField(
    value: Int?,
    label: String,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { raw ->
            onValueChange(raw.filter { it.isDigit() }.take(4).toIntOrNull())
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
