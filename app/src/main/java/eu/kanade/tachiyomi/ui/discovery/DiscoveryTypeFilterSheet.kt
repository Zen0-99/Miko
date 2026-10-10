package eu.kanade.tachiyomi.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.presentation.library.filter.FilterTagChip
import eu.kanade.tachiyomi.data.catalogrows.CatalogFilter
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.metadata.tmdb.TmdbSubGenreKeywords
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Shared discovery sheet — the reader-settings pattern (`TabbedDialog` →
 * `AdaptiveSheet`, a modal bottom sheet on phones) with two tabs:
 *
 *   Type   — the carrier's sub-types (Movies|Series, Manga|Webtoons,
 *            Novels|Books). No "All types" — every option maps to a feed.
 *   Filter — sort for every carrier; the shows carrier additionally gets
 *            the TMDB facet sections (genres, sub-genres, year, rating).
 *
 * One component serves all modes — chrome and interactions are identical;
 * only the type options and filter sections differ per carrier.
 */
@Composable
fun DiscoveryTypeFilterSheet(
    carrier: Carrier,
    typeOptions: List<StringResource>,
    selectedTypeIndex: Int,
    onTypeSelected: (Int) -> Unit,
    filter: DiscoveryFilter,
    mediaType: MediaType,
    onFilterChange: (DiscoveryFilter) -> Unit,
    catalogSort: DiscoverySort,
    onCatalogSortChange: (DiscoverySort) -> Unit,
    /** 22-01: manga/books browse facets — the worker's own genre
     *  vocabulary feeds the chips (`/v1/genres`); an empty list hides
     *  the genre section (books seeds carry no tags yet). */
    catalogFilter: CatalogFilter = CatalogFilter(),
    onCatalogFilterChange: (CatalogFilter) -> Unit = {},
    catalogGenres: List<String> = emptyList(),
    initialPage: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabTitles = persistentListOf(
        stringResource(AYMR.strings.type),
        stringResource(MR.strings.action_filter),
    )
    val pagerState = rememberPagerState(initialPage = initialPage) { tabTitles.size }

    // Half the sheet window — fixed like the reader sheet (same height
    // regardless of content), but the sparse Type/Filter pages don't
    // justify 72%. Applied INSIDE the content slot by TabbedDialog —
    // height on the sheet's own modifier collapses ModalBottomSheet's
    // Expanded anchor to 0 → top-anchored.
    TabbedDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        tabTitles = tabTitles,
        pagerState = pagerState,
        fillHeight = true,
        heightFraction = 0.5f,
    ) { page ->
        when (page) {
            0 -> DiscoveryTypePage(
                typeOptions = typeOptions,
                selectedIndex = selectedTypeIndex,
                onSelect = onTypeSelected,
            )
            else -> DiscoveryFilterPage(
                carrier = carrier,
                filter = filter,
                mediaType = mediaType,
                onFilterChange = onFilterChange,
                catalogSort = catalogSort,
                onCatalogSortChange = onCatalogSortChange,
                catalogFilter = catalogFilter,
                onCatalogFilterChange = onCatalogFilterChange,
                catalogGenres = catalogGenres,
                onDismiss = onDismiss,
            )
        }
    }
}

/** Type tab — radio-style rows, one per sub-type; the tap applies live. */
@Composable
private fun DiscoveryTypePage(
    typeOptions: List<StringResource>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxHeight(),
        contentPadding = PaddingValues(
            horizontal = TabbedDialogPaddings.Horizontal,
            vertical = TabbedDialogPaddings.Vertical,
        ),
    ) {
        typeOptions.forEachIndexed { index, labelRes ->
            item(key = "type-$index") {
                SheetOptionRow(
                    label = stringResource(labelRes),
                    isSelected = index == selectedIndex,
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

/** Filter tab — sort for every carrier plus the shows TMDB facets. */
@Composable
private fun DiscoveryFilterPage(
    carrier: Carrier,
    filter: DiscoveryFilter,
    mediaType: MediaType,
    onFilterChange: (DiscoveryFilter) -> Unit,
    catalogSort: DiscoverySort,
    onCatalogSortChange: (DiscoverySort) -> Unit,
    catalogFilter: CatalogFilter,
    onCatalogFilterChange: (CatalogFilter) -> Unit,
    catalogGenres: List<String>,
    onDismiss: () -> Unit,
) {
    val isShows = carrier == Carrier.SHOWS
    val sort = if (isShows) filter.sort else catalogSort
    val filtering = if (isShows) {
        filter.isActive
    } else {
        catalogSort != DiscoverySort.POPULARITY || catalogFilter.isActive
    }

    Column(modifier = Modifier.fillMaxHeight()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                horizontal = TabbedDialogPaddings.Horizontal,
                vertical = TabbedDialogPaddings.Vertical,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "sort") {
                FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_sort))
                DiscoverySort.entries.forEach { option ->
                    SheetOptionRow(
                        label = stringResource(option.labelRes),
                        isSelected = sort == option,
                        onClick = {
                            if (isShows) {
                                onFilterChange(filter.copy(sort = option))
                            } else {
                                onCatalogSortChange(option)
                            }
                        },
                    )
                }
            }
            if (isShows) {
                discoveryFacetItems(
                    filter = filter,
                    mediaType = mediaType,
                    onFilterChange = onFilterChange,
                )
            } else {
                catalogFacetItems(
                    filter = catalogFilter,
                    genres = catalogGenres,
                    onFilterChange = onCatalogFilterChange,
                )
            }
        }

        // Footer — Clear resets the carrier's facets (sort included);
        // Apply just closes: edits already applied live.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (isShows) {
                        onFilterChange(DiscoveryFilter())
                    } else {
                        onCatalogSortChange(DiscoverySort.POPULARITY)
                        onCatalogFilterChange(CatalogFilter())
                    }
                },
                enabled = filtering,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.clear_filters),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Button(
                onClick = onDismiss,
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

/**
 * The trigger row that replaced the old sub-type pills — label + caret
 * opens the shared sheet's Type tab, styled like a library collection
 * header. One chrome for every carrier.
 */
@Composable
fun DiscoveryTypeRow(
    label: String,
    topPadding: Dp,
    onTypeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onTypeClick)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Shows-carrier facet sections — the moved DiscoveryFilterSheet body. */
private fun androidx.compose.foundation.lazy.LazyListScope.discoveryFacetItems(
    filter: DiscoveryFilter,
    mediaType: MediaType,
    onFilterChange: (DiscoveryFilter) -> Unit,
) {
    item(key = "genres") {
        FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_genre))
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

    // Sub-genre rows appear only under included (ENABLED_IS) genres —
    // nothing renders until a genre is chosen.
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
                                // Rating floor without a vote floor is
                                // noise — default 100 votes (query maps it).
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

/** Manga/books facet sections (22-01) — `/v1/works/browse` params.
 *  Genre chips cycle TriState (off → include → exclude) like the TMDB
 *  set; the vocabulary comes from `/v1/genres`, so carriers without a
 *  tagged catalog (books today) simply skip the section. */
private fun androidx.compose.foundation.lazy.LazyListScope.catalogFacetItems(
    filter: CatalogFilter,
    genres: List<String>,
    onFilterChange: (CatalogFilter) -> Unit,
) {
    if (genres.isNotEmpty()) {
        item(key = "catalog-genres") {
            FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_genre))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                genres.forEach { genre ->
                    val state = filter.genreStates[genre] ?: TriState.DISABLED
                    FilterTagChip(
                        label = genre,
                        state = state,
                        enabled = true,
                        onToggle = {
                            onFilterChange(filter.withGenreState(genre, state.next()))
                        },
                    )
                }
            }
        }
    }

    item(key = "catalog-year") {
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

    item(key = "catalog-rating") {
        FilterSectionLabel(stringResource(AYMR.strings.discovery_filter_rating))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CATALOG_RATING_STEPS.forEach { step ->
                val selected = filter.ratingGte == step
                FilterTagChip(
                    label = "★ ${step.toInt()}+",
                    state = if (selected) TriState.ENABLED_IS else TriState.DISABLED,
                    enabled = true,
                    onToggle = {
                        onFilterChange(
                            filter.copy(ratingGte = if (selected) null else step),
                        )
                    },
                )
            }
        }
    }
}

private val RATING_STEPS = listOf(5.0, 6.0, 7.0, 8.0)
private val VOTE_STEPS = listOf(100, 500, 1000)

/** Rating floors on the worker's 0–100 MangaBaka scale. */
private val CATALOG_RATING_STEPS = listOf(60.0, 70.0, 80.0, 90.0)

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
private fun SheetOptionRow(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    Color.Transparent
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
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
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
