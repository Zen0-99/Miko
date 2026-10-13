package miko.ui.home

import eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import miko.ui.MikoUiGraph
import miko.ui.carrier.BrowseCarrier
import miko.ui.carrier.NavTarget
import miko.ui.catalog.CatalogRowSpec
import miko.ui.catalog.CatalogTitleLookup
import miko.ui.catalog.ExtensionUnavailableException
import miko.ui.catalog.Feed
import miko.ui.catalog.FeedNotImplementedException
import miko.ui.catalog.RowItem
import miko.ui.catalog.RowTarget
import miko.ui.catalog.TmdbDiscover
import miko.ui.catalog.TmdbGenres
import miko.ui.catalog.TmdbKeyMissingException

/**
 * Home tab — Android's HomeHub: greeting + hero "continue" carousel +
 * persisted catalog rows ([CatalogRowSpec]s — seeded one History row per
 * medium, like Android's `CatalogRowStore`). History rows are served live
 * from the carrier history feeds; other feeds resolve through the shared
 * `CatalogRowResolver` and error to honest row chips.
 */
class HomeScreenModel(
    private val graph: MikoUiGraph,
    private val carrierFlow: StateFlow<BrowseCarrier>,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class RecentItem(
        val entryId: Long,
        val itemId: Long,
        val entryTitle: String,
        val itemName: String,
        val coverUrl: String?,
        val readAt: Long?,
    )

    enum class Greeting { Morning, Afternoon, Evening, Night }

    /** Android's `CatalogRowState` — one rendered row's items/loading/error. */
    data class CatalogRowState(
        val items: List<RowItem> = emptyList(),
        val loading: Boolean = false,
        val error: CatalogRowError? = null,
    )

    enum class CatalogRowError {
        NO_TMDB_KEY,
        UNSUPPORTED_FEED,
        EXTENSION_MISSING,
        FETCH_FAILED,
    }

    data class State(
        val initialized: Boolean = false,
        /** The active carrier's recents — the hero's source. */
        val recents: List<RecentItem> = emptyList(),
        val catalogSpecs: List<CatalogRowSpec> = emptyList(),
        val catalogRows: Map<String, CatalogRowState> = emptyMap(),
        /** medium → that medium's deduped recents — feeds History spec
         *  rows for every medium, not just the active carrier's (Android
         *  combines all three history feeds on Home). */
        val historyByMedium: Map<String, List<RecentItem>> = emptyMap(),
        /** Android's `HomeHubDialog` — the open catalog sheet. */
        val dialog: HomeDialog? = null,
    )

    /** Android's `HomeHubDialog` — Manage/Build sheets over Home. */
    sealed interface HomeDialog {
        data object ManageCatalogs : HomeDialog
        data class BuildCatalog(
            val existing: CatalogRowSpec?,
            val lockedMedium: String?,
        ) : HomeDialog
    }

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** spec id → content key already fetched/fetching (Android's
     *  `catalogFetchKeys` — reorder/visibility edits don't refetch). */
    private val catalogFetchKeys = mutableMapOf<String, String>()

    init {
        // Cross-medium history — every medium's recents feed the hero
        // (active carrier) and the History spec rows (all mediums).
        for ((medium, carrier) in MEDIUM_CARRIERS) {
            scope.launch {
                recentsFor(carrier).collect { recents ->
                    _state.update { s ->
                        val next = s.copy(
                            initialized = true,
                            historyByMedium = s.historyByMedium + (medium to recents),
                        )
                        next.copy(
                            catalogRows = next.catalogRows + next.catalogSpecs
                                .filter { it.visible && it.feed is Feed.History && it.medium == medium }
                                .associate { it.id to CatalogRowState(items = historyRowItems(medium, next)) },
                        )
                    }
                }
            }
        }

        // Hero — derive the active carrier's slice.
        scope.launch {
            combine(carrierFlow, _state) { c, s -> s.historyByMedium[carrierMedium(c)].orEmpty() }
                .distinctUntilChanged()
                .collect { recents -> _state.update { it.copy(recents = recents) } }
        }

        // Mirror persisted catalog specs into state — deleted/hidden
        // specs drop their rows.
        scope.launch {
            graph.catalogRowStore.rows.collectLatest { specs ->
                _state.update { s ->
                    s.copy(
                        catalogSpecs = specs,
                        catalogRows = s.catalogRows.filterKeys { id -> specs.any { it.id == id } },
                    )
                }
            }
        }
    }

    /** Time-of-day greeting, Android's home_greeting_* split. */
    fun greeting(hour: Int): Greeting = when (hour) {
        in 5..11 -> Greeting.Morning
        in 12..16 -> Greeting.Afternoon
        in 17..22 -> Greeting.Evening
        else -> Greeting.Night
    }

    /**
     * Android's `fetchCatalogRowIfNeeded` — composition is the fetch
     * signal. History specs are served live (never fetch-cached); other
     * feeds resolve once per content key.
     */
    fun onRowVisible(spec: CatalogRowSpec) {
        if (spec.feed is Feed.History) {
            // Ensure the row exists even while its medium's history is
            // still collecting — a first paint shouldn't wait on the DB.
            _state.update { s ->
                if (spec.id in s.catalogRows) {
                    s
                } else {
                    s.copy(
                        catalogRows = s.catalogRows + (
                            spec.id to CatalogRowState(items = historyRowItems(spec.medium, s))
                            ),
                    )
                }
            }
            return
        }
        val key = graph.catalogRowStore.contentKey(spec)
        if (catalogFetchKeys[spec.id] == key) return
        catalogFetchKeys[spec.id] = key
        scope.launch {
            _state.update { s ->
                s.copy(catalogRows = s.catalogRows + (spec.id to CatalogRowState(loading = true)))
            }
            val result = runCatching { graph.catalogRowResolver.resolve(spec) }
            _state.update { s ->
                s.copy(
                    catalogRows = s.catalogRows + (
                        spec.id to CatalogRowState(
                            items = result.getOrDefault(emptyList()),
                            error = when (val e = result.exceptionOrNull()) {
                                null -> null
                                is TmdbKeyMissingException -> CatalogRowError.NO_TMDB_KEY
                                is FeedNotImplementedException -> CatalogRowError.UNSUPPORTED_FEED
                                is ExtensionUnavailableException -> CatalogRowError.EXTENSION_MISSING
                                else -> CatalogRowError.FETCH_FAILED
                            },
                        )
                        ),
                )
            }
        }
    }

    /** The spec's content-affecting key — keys the composition effect so
     *  facet/feed edits refetch but reorder/visibility don't. */
    fun rowFetchKey(spec: CatalogRowSpec): String = graph.catalogRowStore.contentKey(spec)

    /**
     * Header title for a spec — Android's `catalogRowTitle`: resolves
     * dynamic lookup data (watch-provider names for TMDB specs,
     * extension source names) on demand; providers are cached in
     * [TmdbDiscover] after the first call.
     */
    suspend fun resolveCatalogRowTitle(spec: CatalogRowSpec): String {
        val providerNames = if (spec.watchProviderIds.isNotEmpty()) {
            val type = (spec.feed as? Feed.Tmdb)?.tmdbMediaType() ?: TmdbGenres.MediaType.TV
            graph.tmdbDiscover.watchProviders(type)
                .associate { it.provider_id to it.provider_name }
        } else {
            emptyMap()
        }
        val sourceName = (spec.feed as? Feed.Extension)
            ?.let { f ->
                graph.binding(carrierForMedium(spec.medium)).source(f.sourceId)?.name
            }
        return spec.displayTitle(
            CatalogTitleLookup(providerNames = providerNames, sourceName = sourceName),
        )
    }

    /** Tap resolution — details for library/extension entries, resume
     *  for history rows. `itemUrl` empty = open details, not an item. */
    suspend fun openRowItem(item: RowItem): NavTarget? = when (val t = item.target) {
        is RowTarget.HistoryEntry ->
            graph.binding(carrierForMedium(t.medium)).resolveNavTarget(t.entryId, t.itemId)
        is RowTarget.LibraryEntry ->
            graph.binding(carrierForMedium(t.medium)).localEntryById(t.entryId)
                ?.let { NavTarget(it.sourceId, it.url, "") }
        is RowTarget.ExtensionEntry -> NavTarget(t.sourceId, t.url, "")
        // Canonical-shows / Cinemeta materialization (external_ids →
        // IMDB → meta → local entry) is part of the mikoDB-deferred
        // infra — the card resolves but the tap is inert until it lands.
        is RowTarget.CanonicalShows -> null
        is RowTarget.CinemetaEntry -> null
    }

    suspend fun resolveTarget(entryId: Long, itemId: Long): NavTarget? =
        graph.binding(carrierFlow.value).resolveNavTarget(entryId, itemId)

    fun dispose() = scope.cancel()

    // --- Catalog Manage/Build plumbing (Android's HomeHubScreenModel) ---

    fun setDialog(dialog: HomeDialog?) = _state.update { it.copy(dialog = dialog) }

    fun upsertCatalogSpec(spec: CatalogRowSpec) = graph.catalogRowStore.upsert(spec)

    fun deleteCatalogSpec(id: String) = graph.catalogRowStore.delete(id)

    fun reorderCatalogSpecs(orderedIds: List<String>) =
        graph.catalogRowStore.reorder(orderedIds)

    fun setCatalogSpecVisible(id: String, visible: Boolean) =
        graph.catalogRowStore.setVisible(id, visible)

    /** One installed extension source's feed choices for the builder. */
    data class CatalogSourceOption(
        val sourceId: Long,
        val name: String,
        val pkgName: String,
        val supportsLatest: Boolean,
    )

    /** Everything the builder's feed step needs for one medium —
     *  Cinemeta manifest catalogs (shows only — empty on iOS: the
     *  manifest lives in the deferred metadata-source stack) + installed
     *  extension sources. Fetches once per sheet session. */
    data class CatalogFeedOptions(
        val cinemetaCatalogs: List<MikoCatalogDescriptor> = emptyList(),
        val extensionSources: List<CatalogSourceOption> = emptyList(),
    )

    suspend fun catalogFeedOptions(medium: String): CatalogFeedOptions {
        val binding = graph.binding(carrierForMedium(medium))
        // A sheet can compose while plugin classloading is mid-flight —
        // bounded wait before the honest empty-sources list.
        if (!binding.isInitialized.value) {
            withTimeoutOrNull(EXTENSION_ENUM_TIMEOUT_MS) {
                binding.isInitialized.first { it }
            }
        }
        val sources = binding.catalogueSources.first()
            .map {
                // `pkgName` is display-only in our builder (subtitles);
                // plugin ids can't round-trip through `ui`, so the
                // source's own name carries the label slot.
                CatalogSourceOption(it.id, it.name, it.name, it.supportsLatest)
            }
            .sortedBy { it.name.lowercase() }
        return CatalogFeedOptions(extensionSources = sources)
    }

    /** TMDB watch-provider list for the streaming-service facet. */
    suspend fun catalogWatchProviders(
        mediaType: TmdbGenres.MediaType,
    ): List<TmdbDiscover.WatchProvider> =
        runCatching { graph.tmdbDiscover.watchProviders(mediaType) }
            .getOrDefault(emptyList())

    /** Extension sources' own genre vocabulary for the facet pills. */
    suspend fun catalogExtensionGenreOptions(medium: String, sourceId: Long): List<String> =
        graph.catalogRowResolver.genreOptions(medium, sourceId)

    /** Library genres actually present on the medium's entries. */
    suspend fun catalogLibraryGenreOptions(medium: String): List<String> =
        graph.catalogRowResolver.libraryGenreOptions(medium)

    // --- History row plumbing ---

    private fun carrierMedium(carrier: BrowseCarrier): String = when (carrier) {
        BrowseCarrier.SHOWS -> CatalogRowSpec.MEDIUM_SHOWS
        BrowseCarrier.MANGA -> CatalogRowSpec.MEDIUM_MANGA
        BrowseCarrier.BOOKS -> CatalogRowSpec.MEDIUM_BOOKS
    }

    private fun carrierForMedium(medium: String): BrowseCarrier = when (medium) {
        CatalogRowSpec.MEDIUM_SHOWS -> BrowseCarrier.SHOWS
        CatalogRowSpec.MEDIUM_MANGA -> BrowseCarrier.MANGA
        else -> BrowseCarrier.BOOKS
    }

    private fun historyRowItems(medium: String, state: State): List<RowItem> =
        state.historyByMedium[medium].orEmpty()
            .take(ROW_ITEM_LIMIT)
            .map { it.toRowItem(medium) }

    private fun RecentItem.toRowItem(medium: String) = RowItem(
        title = entryTitle,
        coverUrl = coverUrl,
        subtitle = itemName,
        target = RowTarget.HistoryEntry(entryId, itemId, medium),
        key = "his:$medium:$entryId:$itemId",
    )

    private fun recentsFor(carrier: BrowseCarrier) = when (carrier) {
        BrowseCarrier.SHOWS -> graph.getAnimeHistory.subscribe("").map { list ->
            list.map {
                RecentItem(
                    entryId = it.animeId,
                    itemId = it.episodeId,
                    entryTitle = it.title,
                    itemName = "Ep. ${formatNumber(it.episodeNumber)}",
                    coverUrl = it.coverData.url,
                    readAt = it.seenAt?.toEpochMilliseconds(),
                )
            }
        }
        BrowseCarrier.MANGA -> graph.getMangaHistory.subscribe("").map { list ->
            list.map {
                RecentItem(
                    entryId = it.mangaId,
                    itemId = it.chapterId,
                    entryTitle = it.title,
                    itemName = "Ch. ${formatNumber(it.chapterNumber)}",
                    coverUrl = it.coverData.url,
                    readAt = it.readAt?.toEpochMilliseconds(),
                )
            }
        }
        BrowseCarrier.BOOKS -> graph.getNovelHistory.subscribe("").map { list ->
            list.map {
                RecentItem(
                    entryId = it.novelId,
                    itemId = it.chapterId,
                    entryTitle = it.title,
                    itemName = it.chapterName
                        ?: "Ch. ${formatNumber(it.chapterNumber)}",
                    coverUrl = it.coverData.url,
                    readAt = it.readAt?.toEpochMilliseconds(),
                )
            }
        }
    }.map { rows ->
        rows.distinctBy { it.entryId }.take(RECENT_LIMIT)
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    private companion object {
        const val RECENT_LIMIT = 12

        /** Android's `LibraryCatalogFeed.ROW_ITEM_LIMIT`. */
        const val ROW_ITEM_LIMIT = 20

        /** Bound on the builder waiting for the medium's source
         *  manager (Android's `EXTENSION_ENUM_TIMEOUT_MS`). */
        private const val EXTENSION_ENUM_TIMEOUT_MS = 20_000L
        val MEDIUM_CARRIERS = listOf(
            CatalogRowSpec.MEDIUM_SHOWS to BrowseCarrier.SHOWS,
            CatalogRowSpec.MEDIUM_MANGA to BrowseCarrier.MANGA,
            CatalogRowSpec.MEDIUM_BOOKS to BrowseCarrier.BOOKS,
        )
    }
}
