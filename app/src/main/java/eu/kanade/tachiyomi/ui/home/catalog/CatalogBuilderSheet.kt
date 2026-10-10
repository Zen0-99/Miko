package eu.kanade.tachiyomi.ui.home.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.library.filter.FilterTagChip
import eu.kanade.tachiyomi.data.catalogrows.CatalogGenreVocab
import eu.kanade.tachiyomi.data.catalogrows.CatalogRowSpec
import eu.kanade.tachiyomi.data.catalogrows.CatalogTitleLookup
import eu.kanade.tachiyomi.data.catalogrows.ExtKind
import eu.kanade.tachiyomi.data.catalogrows.Feed
import eu.kanade.tachiyomi.data.catalogrows.LibrarySort
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.metadata.tmdb.TmdbDiscover
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType
import eu.kanade.tachiyomi.metadata.tmdb.TmdbSubGenreKeywords
import eu.kanade.tachiyomi.ui.discovery.DiscoverySort
import eu.kanade.tachiyomi.ui.home.hub.HomeHubScreenModel
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.UUID

/**
 * Stepped catalog builder (Phase 18-3) — Type → Feed → Facets → Name.
 *
 * Type is two-level: a carrier category (Manga/Shows/Books) expands its
 * sub-categories (Manga→Manga/Webtoons, Shows→Series/Movies, Books→
 * Novels/Books). The feed step groups its choices into Discovery feeds /
 * Extensions / Library sections; each extension source appears once —
 * its Popular-vs-Latest listing is a facet of the feed, picked on the
 * next step (Latest only offered when the source supports it).
 *
 * Genre facets reuse the discovery filter's tri-state logic: chips cycle
 * include → exclude, and sub-genre pills appear under included genres.
 * A Cinemeta pick skips the facet step entirely — the catalog IS the
 * facet surface. Edits prefill every step and keep the spec's id/order.
 */
@Composable
fun CatalogBuilderSheet(
    existing: CatalogRowSpec?,
    lockedMedium: String? = null,
    loadFeedOptions: suspend (String) -> HomeHubScreenModel.CatalogFeedOptions,
    loadWatchProviders: suspend (MediaType) -> List<TmdbDiscover.WatchProvider>,
    loadExtensionGenreOptions: suspend (String, Long) -> List<String>,
    loadLibraryGenreOptions: suspend (String) -> List<String>,
    onMediumPicked: (String) -> Unit,
    onCommit: (CatalogRowSpec) -> Unit,
    onDismissRequest: () -> Unit,
) {
    // Rows are medium-scoped — switching the home mode to the picked
    // category (or an edit's existing medium) means the finished row
    // lands where the user is looking when the sheet closes.
    LaunchedEffect(Unit) {
        (existing?.medium ?: lockedMedium)?.let(onMediumPicked)
    }

    var step by remember { mutableIntStateOf(0) }

    var subType by remember { mutableStateOf(existing?.toCategorySubType()?.second) }
    var feed by remember { mutableStateOf(existing?.feed) }
    var genreStates by remember {
        mutableStateOf(existing.toTriStates { it.genreIds to it.excludedGenreIds })
    }
    var keywordStates by remember {
        mutableStateOf(existing.toTriStates { it.keywordIds to it.excludedKeywordIds })
    }
    var watchProviderIds by remember { mutableStateOf(existing?.watchProviderIds.orEmpty()) }
    var genreNameStates by remember {
        mutableStateOf(existing.toNameTriStates())
    }
    var yearFrom by remember { mutableStateOf(existing?.yearFrom) }
    var yearTo by remember { mutableStateOf(existing?.yearTo) }
    var voteAverageGte by remember { mutableStateOf(existing?.voteAverageGte) }
    var voteCountGte by remember { mutableStateOf(existing?.voteCountGte) }
    var sort by remember { mutableStateOf(existing?.sort ?: "default") }
    var customTitle by remember { mutableStateOf(existing?.title.orEmpty()) }

    val medium = subType?.category?.medium
    val tmdbType = subType?.tmdbType

    // Feed options for the picked medium — providers for the facet step.
    val feedOptions by produceState<HomeHubScreenModel.CatalogFeedOptions?>(null, medium) {
        value = medium?.let { loadFeedOptions(it) } ?: HomeHubScreenModel.CatalogFeedOptions()
    }
    val watchProviders by produceState<List<TmdbDiscover.WatchProvider>>(emptyList(), tmdbType, step) {
        if (step == STEP_FACETS && feed is Feed.Tmdb && tmdbType != null) {
            value = loadWatchProviders(tmdbType)
        }
    }

    // Genre-pill vocabularies for the non-TMDB feeds — the extension's
    // own filter list for source rows, the live library genres for
    // library rows, the curated tag vocab for canonical rows.
    val extSourceId = (feed as? Feed.Extension)?.sourceId
    val extensionGenreOptions by produceState(emptyList<String>(), extSourceId, medium) {
        value = if (extSourceId != null && medium != null) {
            loadExtensionGenreOptions(medium, extSourceId)
        } else {
            emptyList()
        }
    }
    val libraryGenreOptions by produceState(emptyList<String>(), feed is Feed.Library, medium) {
        value = if (feed is Feed.Library && medium != null) {
            loadLibraryGenreOptions(medium)
        } else {
            emptyList()
        }
    }

    fun buildSpec(): CatalogRowSpec = CatalogRowSpec(
        id = existing?.id ?: "row_${UUID.randomUUID()}",
        title = customTitle.ifBlank { null },
        medium = subType?.category?.medium ?: WorkIdResolver.MEDIUM_SHOWS,
        feed = feed ?: Feed.Tmdb((tmdbType ?: MediaType.TV).name),
        genreIds = genreStates.included(),
        excludedGenreIds = genreStates.excluded(),
        keywordIds = keywordStates.included(),
        excludedKeywordIds = keywordStates.excluded(),
        watchProviderIds = watchProviderIds,
        genreNames = genreNameStates.includedNames(),
        excludedGenreNames = genreNameStates.excludedNames(),
        yearFrom = yearFrom,
        yearTo = yearTo,
        voteAverageGte = voteAverageGte,
        voteCountGte = voteCountGte,
        sort = sort,
        order = existing?.order ?: 0,
        visible = existing?.visible ?: true,
    )

    // The step sequence is feed-shaped: extensions get a dedicated
    // listing step (Popular/Latest on its own), Cinemeta and History
    // have no facets to show.
    val steps = stepFlow(feed)

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
        ) {
            Text(
                text = stringResource(
                    when (step) {
                        STEP_TYPE -> AYMR.strings.catalog_step_type
                        STEP_FEED -> AYMR.strings.catalog_step_feed
                        STEP_LISTING -> AYMR.strings.catalog_step_listing
                        STEP_FACETS -> AYMR.strings.catalog_step_facets
                        else -> AYMR.strings.catalog_step_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 4.dp,
                ),
            )

            // Breadcrumb — the picks made so far, "Shows › Series › TMDB
            // Discover". Keeps orientation in a stepped sheet where the
            // earlier choices scroll away.
            val crumbs = buildList {
                subType?.let { st ->
                    add(stringResource(st.category.labelRes))
                    add(stringResource(st.labelRes))
                }
                feed?.let { f ->
                    add(feedCrumb(f, feedOptions, subType?.category?.medium))
                    // The listing pick joins the crumb once past its
                    // step — "WeebCentral › Latest".
                    if (f is Feed.Extension && steps.indexOf(step) > steps.indexOf(STEP_LISTING)) {
                        add(
                            stringResource(
                                if (f.kind == ExtKind.LATEST) MR.strings.latest else MR.strings.popular,
                            ),
                        )
                    }
                }
            }
            if (crumbs.isNotEmpty()) {
                Text(
                    text = crumbs.joinToString("  ›  "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, bottom = 12.dp, end = 24.dp),
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    STEP_TYPE -> item(key = "type") {
                        val pickSubType: (BuilderSubType) -> Unit = { st ->
                            if (subType != st) {
                                subType = st
                                onMediumPicked(st.category.medium)
                                // A different sub-type invalidates feed+facets.
                                feed = null
                                genreStates = emptyMap()
                                keywordStates = emptyMap()
                                genreNameStates = emptyMap()
                                watchProviderIds = emptyList()
                                sort = "default"
                            }
                        }
                        val lockedCat = lockedMedium?.let { m ->
                            BuilderCategory.entries.firstOrNull { it.medium == m }
                        }
                        if (lockedCat != null) {
                            // "New catalog" from a manage-sheet medium pill —
                            // the category is already chosen, so the step is
                            // just its sub-types as the discovery pill track.
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                FacetLabel(stringResource(lockedCat.labelRes))
                                SubTypePills(
                                    pills = lockedCat.subTypes,
                                    selected = subType,
                                    onPick = pickSubType,
                                )
                            }
                        } else {
                            // Three always-visible sections — the same look as
                            // the feed step, not an expanding category.
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                BuilderCategory.entries.forEach { cat ->
                                    Column {
                                        FacetLabel(stringResource(cat.labelRes))
                                        cat.subTypes.forEach { st ->
                                            BuilderOptionRow(
                                                label = stringResource(st.labelRes),
                                                isSelected = subType == st,
                                                onClick = { pickSubType(st) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    STEP_FEED -> item(key = "feed") {
                        val options = feedOptions
                        if (options == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                builderFeedSections(subType, options).forEach { section ->
                                    Column {
                                        FacetLabel(section.label)
                                        section.choices.forEach { choice ->
                                            BuilderOptionRow(
                                                label = choice.label,
                                                isSelected = feed != null &&
                                                    sameFeed(choice.feed, feed!!),
                                                onClick = {
                                                    feed = choice.feed
                                                    sort = "default"
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    STEP_LISTING -> item(key = "listing") {
                        (feed as? Feed.Extension)?.let { f ->
                            ExtensionListingSection(
                                feed = f,
                                supportsLatest = feedOptions?.extensionSources
                                    ?.firstOrNull { it.sourceId == f.sourceId }
                                    ?.supportsLatest ?: false,
                                onKind = { feed = f.copy(kind = it) },
                            )
                        }
                    }

                    STEP_FACETS -> item(key = "facets") {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            when (val f = feed) {
                                is Feed.Tmdb -> TmdbFacetSections(
                                    tmdbType = tmdbType ?: MediaType.TV,
                                    genreStates = genreStates,
                                    keywordStates = keywordStates,
                                    watchProviderIds = watchProviderIds,
                                    watchProviders = watchProviders,
                                    yearFrom = yearFrom,
                                    yearTo = yearTo,
                                    voteAverageGte = voteAverageGte,
                                    voteCountGte = voteCountGte,
                                    sort = sort,
                                    onGenreToggle = { id, s ->
                                        genreStates = genreStates.next(id, s)
                                    },
                                    onKeywordToggle = { id, s ->
                                        keywordStates = keywordStates.next(id, s)
                                    },
                                    onProviderToggle = { watchProviderIds = watchProviderIds.toggle(it) },
                                    onYearFrom = { yearFrom = it },
                                    onYearTo = { yearTo = it },
                                    onRating = { rating ->
                                        voteAverageGte = rating
                                        // Rating floor without a vote floor is
                                        // noise — same default the discovery
                                        // filter applies.
                                        voteCountGte = if (rating == null) {
                                            null
                                        } else {
                                            voteCountGte
                                                ?: TmdbDiscover.DEFAULT_VOTE_COUNT_GTE
                                        }
                                    },
                                    onVoteCount = { voteCountGte = it },
                                    onSort = { sort = it },
                                )
                                is Feed.Extension -> GenrePillSection(
                                    options = extensionGenreOptions,
                                    states = genreNameStates,
                                    onToggle = { name, s ->
                                        genreNameStates = genreNameStates.next(name, s)
                                    },
                                )
                                is Feed.Canonical -> GenrePillSection(
                                    options = CatalogGenreVocab.forMedium(
                                        medium ?: WorkIdResolver.MEDIUM_MANGA,
                                    ),
                                    states = genreNameStates,
                                    onToggle = { name, s ->
                                        genreNameStates = genreNameStates.next(name, s)
                                    },
                                )
                                is Feed.Library -> {
                                    GenrePillSection(
                                        options = libraryGenreOptions,
                                        states = genreNameStates,
                                        onToggle = { name, s ->
                                            genreNameStates = genreNameStates.next(name, s)
                                        },
                                    )
                                    LibrarySortSection(sort) { sort = it }
                                }
                                else -> Unit
                            }
                        }
                    }

                    else -> item(key = "title") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val autoTitle = buildSpec().copy(title = null).displayTitle(
                                CatalogTitleLookup(
                                    providerNames = watchProviders
                                        .associate { it.provider_id to it.provider_name },
                                    sourceName = (feed as? Feed.Extension)?.let { f ->
                                        feedOptions?.extensionSources
                                            ?.firstOrNull { it.sourceId == f.sourceId }?.name
                                    },
                                ),
                            )
                            // Rounded pill field — the global search
                            // bar's shape/tint without the haze glass.
                            Text(
                                text = stringResource(AYMR.strings.catalog_name),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = customTitle,
                                    onValueChange = { customTitle = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                                        MaterialTheme.colorScheme.primary,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        androidx.compose.foundation.layout.Box {
                                            if (customTitle.isBlank()) {
                                                Text(
                                                    text = autoTitle,
                                                    color = MaterialTheme.colorScheme
                                                        .onSurfaceVariant.copy(alpha = 0.5f),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                )
                                            }
                                            inner()
                                        }
                                    },
                                )
                            }
                            Text(
                                text = stringResource(AYMR.strings.catalog_title_auto_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Footer — Back / Next|Save.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step = steps[steps.indexOf(step) - 1] },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(AYMR.strings.catalog_action_back))
                    }
                }
                val nextEnabled = when (step) {
                    STEP_TYPE -> subType != null
                    STEP_FEED -> feed != null
                    else -> true
                }
                Button(
                    onClick = {
                        if (step == STEP_TITLE) {
                            onCommit(buildSpec())
                            onDismissRequest()
                        } else {
                            step = steps[steps.indexOf(step) + 1]
                        }
                    },
                    enabled = nextEnabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        stringResource(
                            if (step == STEP_TITLE) {
                                MR.strings.action_save
                            } else {
                                MR.strings.onboarding_action_next
                            },
                        ),
                    )
                }
            }
        }
    }
}

private const val STEP_TYPE = 0
private const val STEP_FEED = 1
private const val STEP_LISTING = 2
private const val STEP_FACETS = 3
private const val STEP_TITLE = 4

/**
 * The steps a given feed walks: extension feeds add the standalone
 * listing pick (Popular/Latest on its own, not sharing a step with
 * facets); Cinemeta and History have no facet surface at all.
 */
private fun stepFlow(feed: Feed?): List<Int> = buildList {
    add(STEP_TYPE)
    add(STEP_FEED)
    if (feed is Feed.Extension) add(STEP_LISTING)
    if (feed != null && feed !is Feed.Cinemeta && feed !is Feed.History) {
        add(STEP_FACETS)
    }
    add(STEP_TITLE)
}

// --- Type step: category + sub-category ---

private enum class BuilderCategory(
    val labelRes: dev.icerock.moko.resources.StringResource,
    val medium: String,
    val subTypes: List<BuilderSubType>,
) {
    SHOWS(
        AYMR.strings.carrier_shows,
        WorkIdResolver.MEDIUM_SHOWS,
        listOf(BuilderSubType.SERIES, BuilderSubType.MOVIES),
    ),
    MANGA(
        AYMR.strings.carrier_manga,
        WorkIdResolver.MEDIUM_MANGA,
        listOf(BuilderSubType.MANGA, BuilderSubType.WEBTOONS),
    ),
    BOOKS(
        AYMR.strings.carrier_books,
        WorkIdResolver.MEDIUM_BOOKS,
        listOf(BuilderSubType.NOVELS, BuilderSubType.BOOKS),
    ),
}

private enum class BuilderSubType(
    val labelRes: dev.icerock.moko.resources.StringResource,
    val tmdbType: MediaType?,
    /** Canonical kindsCsv this sub-type's Miko-catalog feed serves;
     *  `"ol"` = OpenLibrary trending. */
    val kindsCsv: String?,
    /**
     * Keywords matched against an extension source's own type/format
     * filter options — the sub-type constraint carried on
     * [Feed.Extension.kindsCsv]. Baseline kinds soft-apply: a source
     * with no type vocabulary serves its plain listing, which on a
     * single-kind source is already right.
     */
    val extKindsCsv: String?,
    /** Narrowing kinds fail loudly when the source can't express them;
     *  baseline kinds degrade to the unfiltered listing. */
    val extKindStrict: Boolean,
) {
    SERIES(AYMR.strings.catalog_type_series, MediaType.TV, null, "tv,series,ona,ova", false),
    MOVIES(AYMR.strings.catalog_type_movie, MediaType.MOVIE, null, "movie,film", true),
    MANGA(AYMR.strings.carrier_manga, null, "manga,comic", "manga", false),
    WEBTOONS(
        AYMR.strings.discovery_pill_webtoons,
        null,
        "manhwa,manhua,webtoon,oel,other",
        // Origin vocab (Weeb Central "Series Type": Manhwa/Manhua/OEL)
        // PLUS format vocab — MangaDex's Format group tags "Long Strip"/
        // "Web Comic", which is the real read-direction distinction.
        // Sources matching neither (strict) fail rather than mix.
        "manhwa,manhua,webtoon,webtoons,oel,long strip,longstrip,web comic,webcomic",
        true,
    ),
    NOVELS(AYMR.strings.discovery_pill_novels, null, "novel", "novel,light novel,web novel", false),
    BOOKS(AYMR.strings.carrier_books, null, "ol", null, false),
    ;

    /** Owning category — computed at use-time, NOT a constructor arg:
     *  BuilderCategory's entries reference these enum constants, so a
     *  ctor backref would read BuilderCategory's still-uninitialised
     *  statics during its <clinit> and leave `category` null forever. */
    val category: BuilderCategory
        get() = BuilderCategory.entries.first { this in it.subTypes }
}

private fun CatalogRowSpec.toCategorySubType(): Pair<BuilderCategory, BuilderSubType> = when (medium) {
    WorkIdResolver.MEDIUM_SHOWS -> BuilderCategory.SHOWS to when {
        (feed as? Feed.Tmdb)?.tmdbMediaType() == MediaType.MOVIE -> BuilderSubType.MOVIES
        (feed as? Feed.Cinemeta)?.cinemetaType == "movie" -> BuilderSubType.MOVIES
        (feed as? Feed.Extension)?.kindsCsv == BuilderSubType.MOVIES.extKindsCsv ->
            BuilderSubType.MOVIES
        else -> BuilderSubType.SERIES
    }
    WorkIdResolver.MEDIUM_MANGA -> BuilderCategory.MANGA to when {
        (feed as? Feed.Canonical)?.kindsCsv == BuilderSubType.WEBTOONS.kindsCsv ->
            BuilderSubType.WEBTOONS
        // kindStrict is the durable discriminator — the kindsCsv keyword
        // string drifts as format vocab (long strip/web comic) gets added,
        // while the strict flag stays stable across edits.
        (feed as? Feed.Extension)?.kindStrict == true ->
            BuilderSubType.WEBTOONS
        else -> BuilderSubType.MANGA
    }
    else ->
        BuilderCategory.BOOKS to
            when ((feed as? Feed.Canonical)?.kindsCsv) {
                "ol" -> BuilderSubType.BOOKS
                else -> BuilderSubType.NOVELS
            }
}

private fun CatalogRowSpec?.toTriStates(
    lists: (CatalogRowSpec) -> Pair<List<Int>, List<Int>>,
): Map<Int, TriState> = this?.let { lists(it) }?.let { (inc, exc) ->
    buildMap {
        inc.forEach { put(it, TriState.ENABLED_IS) }
        exc.forEach { put(it, TriState.ENABLED_NOT) }
    }
} ?: emptyMap()

private fun Map<Int, TriState>.included(): List<Int> =
    filterValues { it == TriState.ENABLED_IS }.keys.sorted()

private fun Map<Int, TriState>.excluded(): List<Int> =
    filterValues { it == TriState.ENABLED_NOT }.keys.sorted()

private fun Map<Int, TriState>.next(id: Int, current: TriState): Map<Int, TriState> =
    this + (id to current.next())

// --- String-keyed genre pills (extension/canonical/library facets) ---

private fun CatalogRowSpec?.toNameTriStates(): Map<String, TriState> = this?.let {
    buildMap {
        it.genreNames.forEach { n -> put(n, TriState.ENABLED_IS) }
        it.excludedGenreNames.forEach { n -> put(n, TriState.ENABLED_NOT) }
    }
} ?: emptyMap()

private fun Map<String, TriState>.includedNames(): List<String> =
    filterValues { it == TriState.ENABLED_IS }.keys.sorted()

private fun Map<String, TriState>.excludedNames(): List<String> =
    filterValues { it == TriState.ENABLED_NOT }.keys.sorted()

private fun Map<String, TriState>.next(name: String, current: TriState): Map<String, TriState> =
    this + (name to current.next())

// --- Feed step: grouped sections ---

private data class BuilderFeedChoice(val label: String, val feed: Feed)

private data class BuilderFeedSection(val label: String, val choices: List<BuilderFeedChoice>)

/**
 * Feed choices for the sub-type, grouped into Discovery feeds /
 * Extensions / Library sections. Extension sources appear once — their
 * Popular/Latest listing is authored on the facet step.
 */
@Composable
private fun builderFeedSections(
    subType: BuilderSubType?,
    options: HomeHubScreenModel.CatalogFeedOptions,
): List<BuilderFeedSection> = buildList {
    val discovery = buildList {
        when (subType) {
            BuilderSubType.SERIES, BuilderSubType.MOVIES -> {
                add(
                    BuilderFeedChoice(
                        stringResource(AYMR.strings.catalog_feed_tmdb),
                        Feed.Tmdb((subType.tmdbType ?: MediaType.TV).name),
                    ),
                )
                val cinemetaType =
                    if (subType.tmdbType == MediaType.MOVIE) "movie" else "series"
                options.cinemetaCatalogs
                    .filter { it.type == cinemetaType }
                    .forEach {
                        add(
                            BuilderFeedChoice(
                                it.name ?: it.id,
                                Feed.Cinemeta(it.type, it.id),
                            ),
                        )
                    }
            }
            BuilderSubType.MANGA, BuilderSubType.WEBTOONS,
            BuilderSubType.NOVELS, BuilderSubType.BOOKS,
            -> {
                subType.kindsCsv?.let { kinds ->
                    add(
                        BuilderFeedChoice(
                            stringResource(
                                if (kinds == "ol") {
                                    AYMR.strings.catalog_feed_openlibrary
                                } else {
                                    AYMR.strings.catalog_feed_canonical
                                },
                            ),
                            Feed.Canonical(kinds),
                        ),
                    )
                }
            }
            null -> Unit
        }
    }
    if (discovery.isNotEmpty()) {
        add(BuilderFeedSection(stringResource(AYMR.strings.catalog_section_feeds), discovery))
    }
    if (options.extensionSources.isNotEmpty()) {
        add(
            BuilderFeedSection(
                stringResource(MR.strings.label_extensions),
                options.extensionSources.map { src ->
                    BuilderFeedChoice(
                        src.name,
                        Feed.Extension(
                            src.pkgName,
                            src.sourceId,
                            ExtKind.POPULAR,
                            kindsCsv = subType?.extKindsCsv,
                            kindStrict = subType?.extKindStrict ?: false,
                        ),
                    )
                },
            ),
        )
    }
    add(
        BuilderFeedSection(
            stringResource(MR.strings.label_library),
            listOf(
                BuilderFeedChoice(
                    stringResource(
                        when (subType?.category?.medium) {
                            WorkIdResolver.MEDIUM_SHOWS -> AYMR.strings.label_recent_anime
                            WorkIdResolver.MEDIUM_MANGA -> AYMR.strings.label_recently_read
                            else -> AYMR.strings.label_recent_novels
                        },
                    ),
                    Feed.History,
                ),
                BuilderFeedChoice(stringResource(MR.strings.label_library), Feed.Library),
            ),
        ),
    )
}

/** Structural feed match for preselect — extension rows match by
 *  sourceId alone since the listing kind is authored on the facet step
 *  (and a reinstall under a new pkgName shouldn't orphan the pick). */
private fun sameFeed(a: Feed, b: Feed): Boolean = when {
    a is Feed.Tmdb && b is Feed.Tmdb -> a.tmdbMediaType() == b.tmdbMediaType()
    a is Feed.Cinemeta && b is Feed.Cinemeta ->
        a.cinemetaType == b.cinemetaType && a.catalogId == b.catalogId
    a is Feed.Canonical && b is Feed.Canonical -> a.kindsCsv == b.kindsCsv
    a is Feed.Extension && b is Feed.Extension -> a.sourceId == b.sourceId
    else -> a == b
}

private fun List<Int>.toggle(id: Int): List<Int> =
    if (id in this) this - id else this + id

/** Breadcrumb label for a picked feed — dynamic names (extension source,
 *  Cinemeta catalog) resolve from the session's feed options. */
@Composable
private fun feedCrumb(
    feed: Feed,
    options: HomeHubScreenModel.CatalogFeedOptions?,
    medium: String?,
): String = when (feed) {
    is Feed.Tmdb -> stringResource(AYMR.strings.catalog_feed_tmdb)
    is Feed.Cinemeta ->
        options?.cinemetaCatalogs
            ?.firstOrNull { it.type == feed.cinemetaType && it.id == feed.catalogId }
            ?.name ?: feed.catalogId.replaceFirstChar { it.uppercase() }
    is Feed.Canonical -> stringResource(
        if (feed.kindsCsv == "ol") {
            AYMR.strings.catalog_feed_openlibrary
        } else {
            AYMR.strings.catalog_feed_canonical
        },
    )
    is Feed.Extension ->
        options?.extensionSources?.firstOrNull { it.sourceId == feed.sourceId }?.name
            ?: feed.pkgName.substringAfterLast('.')
    is Feed.Library -> stringResource(MR.strings.label_library)
    Feed.History -> stringResource(
        when (medium) {
            WorkIdResolver.MEDIUM_SHOWS -> AYMR.strings.label_recent_anime
            WorkIdResolver.MEDIUM_MANGA -> AYMR.strings.label_recently_read
            else -> AYMR.strings.label_recent_novels
        },
    )
}

// --- Rows & facet sections ---

@Composable
private fun BuilderOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/**
 * Full-width pill track — the exact styling of the discovery
 * [SubTypePillRow] (extraLarge rounded track on onSurface@6%, weight(1f)
 * pills, primary@15% + bold primary on the pick). Used by the type step
 * when a manage-sheet medium pill already fixed the category.
 */
@Composable
private fun SubTypePills(
    pills: List<BuilderSubType>,
    selected: BuilderSubType?,
    onPick: (BuilderSubType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        pills.forEach { pill ->
            val isSelected = pill == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .then(
                        if (isSelected) {
                            Modifier.background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onPick(pill) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(pill.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Extension listing kind — the "popular vs latest" facet asked on the
 *  step after the feed pick (Latest only when the source supports it). */
@Composable
private fun ExtensionListingSection(
    feed: Feed.Extension,
    supportsLatest: Boolean,
    onKind: (ExtKind) -> Unit,
) {
    FacetLabel(stringResource(AYMR.strings.catalog_facet_listing))
    BuilderOptionRow(
        label = stringResource(MR.strings.popular),
        isSelected = feed.kind == ExtKind.POPULAR,
        onClick = { onKind(ExtKind.POPULAR) },
    )
    if (supportsLatest) {
        BuilderOptionRow(
            label = stringResource(MR.strings.latest),
            isSelected = feed.kind == ExtKind.LATEST,
            onClick = { onKind(ExtKind.LATEST) },
        )
    }
}

/**
 * TMDB facets — mirrors the discovery filter sheet's logic exactly:
 * tri-state genre chips in a scrolling row, sub-genre pills appearing
 * under included genres, streaming services, year window, rating +
 * vote-count floors, sort.
 */
@Composable
private fun TmdbFacetSections(
    tmdbType: MediaType,
    genreStates: Map<Int, TriState>,
    keywordStates: Map<Int, TriState>,
    watchProviderIds: List<Int>,
    watchProviders: List<TmdbDiscover.WatchProvider>,
    yearFrom: Int?,
    yearTo: Int?,
    voteAverageGte: Double?,
    voteCountGte: Int?,
    sort: String,
    onGenreToggle: (Int, TriState) -> Unit,
    onKeywordToggle: (Int, TriState) -> Unit,
    onProviderToggle: (Int) -> Unit,
    onYearFrom: (Int?) -> Unit,
    onYearTo: (Int?) -> Unit,
    onRating: (Double?) -> Unit,
    onVoteCount: (Int?) -> Unit,
    onSort: (String) -> Unit,
) {
    FacetLabel(stringResource(AYMR.strings.discovery_filter_genre))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TmdbGenres.forType(tmdbType).forEach { genre ->
            val state = genreStates[genre.id] ?: TriState.DISABLED
            FilterTagChip(
                label = genre.name,
                state = state,
                enabled = true,
                onToggle = { onGenreToggle(genre.id, state) },
            )
        }
    }

    // Sub-genre pills appear under included (ENABLED_IS) genres only.
    val includedGenreNames = TmdbGenres.forType(tmdbType)
        .filter { genreStates[it.id] == TriState.ENABLED_IS }
        .map { it.name }
    val subGenreGroups = TmdbSubGenreKeywords.groupsFor(includedGenreNames)
    if (subGenreGroups.isNotEmpty()) {
        FacetLabel(stringResource(AYMR.strings.discovery_filter_subgenre))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subGenreGroups.forEach { (genreName, subs) ->
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
                    subs.forEach { sub ->
                        val state = keywordStates[sub.keywordId] ?: TriState.DISABLED
                        FilterTagChip(
                            label = sub.name,
                            state = state,
                            enabled = true,
                            onToggle = { onKeywordToggle(sub.keywordId, state) },
                        )
                    }
                }
            }
        }
    }

    if (watchProviders.isNotEmpty()) {
        FacetLabel(stringResource(AYMR.strings.catalog_facet_streaming))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            watchProviders.forEach { provider ->
                FilterTagChip(
                    label = provider.provider_name,
                    state = if (provider.provider_id in watchProviderIds) {
                        TriState.ENABLED_IS
                    } else {
                        TriState.DISABLED
                    },
                    enabled = true,
                    onToggle = { onProviderToggle(provider.provider_id) },
                )
            }
        }
    }

    FacetLabel(stringResource(AYMR.strings.discovery_filter_year))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BuilderYearField(
            value = yearFrom,
            label = stringResource(AYMR.strings.discovery_filter_year_from),
            onValueChange = onYearFrom,
            modifier = Modifier.weight(1f),
        )
        BuilderYearField(
            value = yearTo,
            label = stringResource(AYMR.strings.discovery_filter_year_to),
            onValueChange = onYearTo,
            modifier = Modifier.weight(1f),
        )
    }

    FacetLabel(stringResource(AYMR.strings.discovery_filter_rating))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(5.0, 6.0, 7.0, 8.0).forEach { step ->
            FilterTagChip(
                label = "★ $step+",
                state = if (voteAverageGte == step) TriState.ENABLED_IS else TriState.DISABLED,
                enabled = true,
                onToggle = { onRating(if (voteAverageGte == step) null else step) },
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        listOf(100, 500, 1000).forEach { step ->
            FilterTagChip(
                label = "$step+ ${stringResource(AYMR.strings.discovery_filter_votes)}",
                state = if (voteCountGte == step) TriState.ENABLED_IS else TriState.DISABLED,
                enabled = true,
                onToggle = { onVoteCount(if (voteCountGte == step) null else step) },
            )
        }
    }

    FacetLabel(stringResource(AYMR.strings.discovery_filter_sort))
    DiscoverySort.entries.forEach { s ->
        BuilderOptionRow(
            label = stringResource(s.labelRes),
            isSelected = sort == s.name,
            onClick = { onSort(s.name) },
        )
    }
}

/**
 * Tri-state genre pills over a feed's name vocabulary — the same
 * include → exclude cycling as the discovery/TMDB facet chips. Empty
 * vocabulary renders the honest "source exposes no filters" note.
 */
@Composable
private fun GenrePillSection(
    options: List<String>,
    states: Map<String, TriState>,
    onToggle: (String, TriState) -> Unit,
) {
    FacetLabel(stringResource(AYMR.strings.discovery_filter_genre))
    if (options.isEmpty()) {
        Text(
            text = stringResource(AYMR.strings.catalog_facet_no_filters),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { name ->
            val state = states[name] ?: TriState.DISABLED
            FilterTagChip(
                label = name,
                state = state,
                enabled = true,
                onToggle = { onToggle(name, state) },
            )
        }
    }
}

@Composable
private fun LibrarySortSection(sort: String, onSort: (String) -> Unit) {
    FacetLabel(stringResource(AYMR.strings.discovery_filter_sort))
    LibrarySort.entries.forEach { s ->
        BuilderOptionRow(
            label = stringResource(
                when (s) {
                    LibrarySort.TITLE -> MR.strings.action_sort_alpha
                    LibrarySort.RECENTLY_ADDED -> MR.strings.action_sort_date_added
                    LibrarySort.LAST_READ -> MR.strings.action_sort_last_read
                },
            ),
            isSelected = sort == s.name,
            onClick = { onSort(s.name) },
        )
    }
}

@Composable
private fun FacetLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun BuilderYearField(
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
