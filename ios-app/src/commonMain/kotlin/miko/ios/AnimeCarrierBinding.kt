package miko.ios

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import miko.domain.shows.SeasonAnime
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.FeedEntry
import miko.ui.carrier.FeedFilter
import miko.ui.carrier.FeedPage
import miko.ui.carrier.HosterEntry
import miko.ui.carrier.ItemSwipeAction
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.LocalItem
import miko.ui.carrier.LocalSeason
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.SeasonFlagOp
import miko.ui.carrier.VideoEntry
import miko.ui.library.passesUpdateRestrictions
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.collection.shows.repository.AnimeCollectionRepository
import tachiyomi.domain.entries.shows.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.shows.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.shows.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.shows.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.model.AnimeUpdate
import tachiyomi.domain.entries.shows.repository.AnimeRepository
import tachiyomi.domain.history.shows.model.AnimeHistoryUpdate
import tachiyomi.domain.history.shows.repository.AnimeHistoryRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.items.season.service.SeasonRecognition
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import kotlin.time.Clock

/** Shows carrier — wraps the JS anime source manager + anime repositories. */
class AnimeCarrierBinding(
    private val sourceManager: AnimeSourceManager,
    private val getAnime: GetAnime,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val historyRepository: AnimeHistoryRepository,
    private val collectionRepository: AnimeCollectionRepository,
    private val preferenceStore: PreferenceStore,
    private val trackRepository: AnimeTrackRepository,
    private val aniSkip: AniSkipApi,
    private val achievementEventBus: AchievementEventBus? = null,
) : CarrierBinding {

    override val isInitialized: StateFlow<Boolean> = sourceManager.isInitialized

    private val fetchInterval = AnimeFetchInterval(GetEpisodesByAnimeId(episodeRepository))
    private val libraryPreferences = LibraryPreferences(preferenceStore)
    private val setDefaultFlags = SetAnimeDefaultEpisodeFlags(
        libraryPreferences,
        SetAnimeEpisodeFlags(animeRepository),
        GetAnimeFavorites(animeRepository),
    )
    private val setSeasonFlags = SetAnimeSeasonFlags(animeRepository)
    private val setSeasonDefaults = SetAnimeDefaultSeasonFlags(
        libraryPreferences,
        setSeasonFlags,
        GetAnimeFavorites(animeRepository),
    )

    override val catalogueSources: Flow<List<CarrierSource>> =
        sourceManager.catalogueSources.map { sources ->
            sources.map { AnimeCarrierSource(it) }
        }

    override fun source(sourceId: Long): CarrierSource? =
        (sourceManager.get(sourceId) as? AnimeCatalogueSource)?.let(::AnimeCarrierSource)

    private fun Anime.toLocalEntry() = LocalEntry(
        id = id,
        title = title,
        favorite = favorite,
        url = url,
        sourceId = source,
        sortDescending = sortDescending(),
        sortMode = when (sorting) {
            Anime.EPISODE_SORTING_SOURCE -> ChapterSortMode.SOURCE
            Anime.EPISODE_SORTING_UPLOAD_DATE -> ChapterSortMode.UPLOAD_DATE
            Anime.EPISODE_SORTING_ALPHABET -> ChapterSortMode.ALPHABET
            else -> ChapterSortMode.NUMBER
        },
        bookmarkedFilter = bookmarkedFilter,
        displayByNumber = displayMode == Anime.EPISODE_DISPLAY_NUMBER,
        unreadFilter = unseenFilter,
        fillermarkedFilter = fillermarkedFilter,
        showPreviews = showPreviews(),
        showSummaries = showSummaries(),
        seasonDownloadedFilter = seasonDownloadedFilter,
        seasonUnseenFilter = seasonUnseenFilter,
        seasonStartedFilter = seasonStartedFilter,
        seasonCompletedFilter = seasonCompletedFilter,
        seasonBookmarkedFilter = seasonBookmarkedFilter,
        seasonFillermarkedFilter = seasonFillermarkedFilter,
        seasonSorting = seasonSorting,
        seasonSortDescending = seasonSortDescending(),
        seasonDisplayGridMode = seasonDisplayGridMode,
        seasonDisplayGridSize = seasonDisplayGridSize,
        seasonDownloadedOverlay = seasonDownloadedOverlay,
        seasonUnseenOverlay = seasonUnseenOverlay,
        seasonLocalOverlay = seasonLocalOverlay,
        seasonLangOverlay = seasonLangOverlay,
        seasonContinueOverlay = seasonContinueOverlay,
        seasonDisplayMode = seasonDisplayMode,
        skipIntroLength = skipIntroLength,
        skipIntroDisable = skipIntroDisable,
    )

    override fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?> =
        getAnime.subscribe(url, sourceId).map { anime -> anime?.toLocalEntry() }

    override suspend fun localEntryById(id: Long): LocalEntry? =
        runCatching { animeRepository.getAnimeById(id) }.getOrNull()?.toLocalEntry()

    override fun favoritedUrls(sourceId: Long): Flow<Set<String>> =
        GetAnimeFavorites(animeRepository).subscribe(sourceId).map { list ->
            list.mapTo(HashSet()) { it.url }
        }

    override suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry {
        val anime = networkToLocalAnime.await(details.toDomainAnime(sourceId))
        return anime.toLocalEntry()
    }

    override suspend fun setFavorite(localId: Long, favorite: Boolean) {
        animeRepository.updateAnime(AnimeUpdate(id = localId, favorite = favorite))
    }

    override suspend fun setChapterSortDescending(localId: Long, descending: Boolean) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val direction = if (descending) Anime.EPISODE_SORT_DESC else Anime.EPISODE_SORT_ASC
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_SORT_DIR_MASK.inv())
                    .or(direction),
            ),
        )
    }

    override suspend fun setChapterUnreadFilter(localId: Long, state: TriState) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val flag = when (state) {
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
            TriState.DISABLED -> 0L
        }
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_UNSEEN_MASK.inv())
                    .or(flag),
            ),
        )
    }

    override suspend fun setChapterSortMode(localId: Long, mode: ChapterSortMode) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val bits = when (mode) {
            ChapterSortMode.SOURCE -> Anime.EPISODE_SORTING_SOURCE
            ChapterSortMode.UPLOAD_DATE -> Anime.EPISODE_SORTING_UPLOAD_DATE
            ChapterSortMode.ALPHABET -> Anime.EPISODE_SORTING_ALPHABET
            ChapterSortMode.NUMBER -> Anime.EPISODE_SORTING_NUMBER
        }
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_SORTING_MASK.inv())
                    .or(bits),
            ),
        )
    }

    override suspend fun setChapterBookmarkedFilter(localId: Long, state: TriState) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val flag = when (state) {
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
            TriState.DISABLED -> 0L
        }
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_BOOKMARKED_MASK.inv())
                    .or(flag),
            ),
        )
    }

    override suspend fun setChapterDisplayByNumber(localId: Long, byNumber: Boolean) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val flag = if (byNumber) Anime.EPISODE_DISPLAY_NUMBER else 0L
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_DISPLAY_MASK.inv())
                    .or(flag),
            ),
        )
    }

    /** Android's showPreviews bit — inverted flag (SHOW = 0, NOT = bit). */
    override suspend fun setChapterShowPreviews(localId: Long, show: Boolean) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val flag = if (show) Anime.EPISODE_SHOW_PREVIEWS else Anime.EPISODE_SHOW_NOT_PREVIEWS
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_PREVIEWS_MASK.inv())
                    .or(flag),
            ),
        )
    }

    /** Android's showSummaries bit — same inverted-flag shape. */
    override suspend fun setChapterShowSummaries(localId: Long, show: Boolean) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val flag = if (show) Anime.EPISODE_SHOW_SUMMARIES else Anime.EPISODE_SHOW_NOT_SUMMARIES
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_SUMMARIES_MASK.inv())
                    .or(flag),
            ),
        )
    }

    /** Android's fillermarkedFilter — the episodeFlags filler bits. */
    override suspend fun setChapterFillermarkedFilter(localId: Long, state: TriState) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }.getOrNull() ?: return
        val flag = when (state) {
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_FILLERMARKED
            TriState.DISABLED -> 0L
        }
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                episodeFlags = anime.episodeFlags
                    .and(Anime.EPISODE_FILLERMARKED_MASK.inv())
                    .or(flag),
            ),
        )
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun localItems(localId: Long): Flow<List<LocalItem>> =
        // Android's season model — episodes live on the parent row's
        // child season rows; merge each season's episodes tagged with
        // its seasonNumber (parent's own rows stay flat, -1).
        animeRepository.getAnimeSeasonsByIdAsFlow(localId).flatMapLatest { seasons ->
            val own = episodeRepository.getEpisodeByAnimeIdAsFlow(localId)
                .map { eps -> eps.map { it.toLocalItem() } }
            if (seasons.isEmpty()) {
                own
            } else {
                val perSeason = seasons.map { season ->
                    episodeRepository.getEpisodeByAnimeIdAsFlow(season.anime.id)
                        .map { eps -> eps.map { it.toLocalItem(season.anime.seasonNumber) } }
                }
                combine(own, *perSeason.toTypedArray()) { lists ->
                    lists.flatMap { it }
                }
            }
        }

    override suspend fun getLocalItems(localId: Long): List<LocalItem> {
        val own = episodeRepository.getEpisodeByAnimeId(localId)
            .map { it.toLocalItem() }
        val seasonal = animeRepository.getAnimeSeasonsById(localId).flatMap { season ->
            episodeRepository.getEpisodeByAnimeId(season.anime.id)
                .map { it.toLocalItem(season.anime.seasonNumber) }
        }
        return own + seasonal
    }

    /**
     * Android's SyncSeasonsWithSource — upsert one child Anime row per
     * source season url, stamping parentId/seasonNumber/sourceOrder.
     * Returns season url → row so episodes attach to their season.
     */
    private suspend fun syncSeasonRows(
        parent: Anime,
        remote: List<RemoteItem>,
    ): Map<String, Anime> {
        val keyed = remote.mapNotNull { item ->
            item.seasonUrl?.let { Triple(it, item.seasonTitle, item.seasonNumber) }
        }.distinctBy { it.first }
        if (keyed.isEmpty()) return emptyMap()
        val localId = parent.id
        val rows = keyed.mapIndexed { index, (url, title, number) ->
            val seasonNumber = SeasonRecognition.parseSeasonNumber(
                parent.title,
                title.orEmpty(),
                number.takeIf { it >= 0.0 },
            )
            val row = networkToLocalAnime.await(
                Anime.create().copy(
                    url = url,
                    title = title ?: url,
                    source = parent.source,
                    fetchType = FetchType.Episodes,
                ),
            )
            if (row.parentId != localId ||
                row.seasonNumber != seasonNumber ||
                row.seasonSourceOrder != index.toLong() ||
                row.title != (title ?: url)
            ) {
                animeRepository.updateAnime(
                    AnimeUpdate(
                        id = row.id,
                        title = title ?: url,
                        parentId = localId,
                        seasonNumber = seasonNumber,
                        seasonSourceOrder = index.toLong(),
                        fetchType = FetchType.Episodes,
                    ),
                )
            }
            url to row
        }.toMap()
        // Android detaches seasons the source no longer returns
        // (removeParentIdByIds — keep the row, drop the linkage).
        val stale = animeRepository.getAnimeSeasonsById(localId)
            .map { it.anime }
            .filter { it.url !in rows.keys }
            .map { it.id }
        if (stale.isNotEmpty()) animeRepository.removeParentIdByIds(stale)
        return rows
    }

    override suspend fun addMissingItems(localId: Long, remote: List<RemoteItem>) {
        val local = runCatching { animeRepository.getAnimeById(localId) }
            .getOrNull() ?: return
        // Season rows are FetchType.Episodes — they never grow children,
        // and a season page's remote list may span sibling seasons when
        // the source re-echoes the parent's season list, so scope it.
        val isSeasonRow = local.parentId != null
        val seasonRows = if (isSeasonRow) {
            emptyMap()
        } else {
            syncSeasonRows(local, remote)
        }
        val scoped = if (isSeasonRow) {
            remote.filter { it.seasonUrl == null || it.seasonUrl == local.url }
        } else {
            remote
        }
        // Pre-season-model installs stored every episode on the parent
        // row — move those to their season rows so progress survives.
        if (seasonRows.isNotEmpty()) {
            val ownByUrl = episodeRepository.getEpisodeByAnimeId(localId)
                .associateBy { it.url }
            if (ownByUrl.isNotEmpty()) {
                val reparent = scoped.mapNotNull { item ->
                    val seasonId = item.seasonUrl?.let { seasonRows[it]?.id }
                        ?: return@mapNotNull null
                    ownByUrl[item.url]?.let {
                        EpisodeUpdate(id = it.id, animeId = seasonId)
                    }
                }
                if (reparent.isNotEmpty()) episodeRepository.updateAllEpisodes(reparent)
            }
        }
        val existing = getLocalItems(localId).mapTo(HashSet()) { it.url }
        val now = Clock.System.now().toEpochMilliseconds()
        val missing = scoped.mapIndexedNotNull { index, item ->
            if (item.url in existing) {
                null
            } else {
                Episode.create().copy(
                    animeId = seasonRows[item.seasonUrl]?.id ?: localId,
                    url = item.url,
                    name = item.name,
                    dateUpload = item.dateUpload,
                    dateFetch = now,
                    episodeNumber = item.number,
                    scanlator = item.scanlator,
                    summary = item.summary,
                    previewUrl = item.previewUrl,
                    sourceOrder = index.toLong(),
                )
            }
        }
        if (missing.isNotEmpty()) episodeRepository.addAllEpisodes(missing)
    }

    override suspend fun refreshFavorites(): Int {
        var fetched = 0
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        val restrictions = libraryPreferences.autoUpdateItemRestrictions().get()
        val (_, upperBound) = fetchInterval.getWindow(now, timeZone)
        for (libraryAnime in animeRepository.getLibraryAnime()) {
            val anime = libraryAnime.anime
            if (!passesUpdateRestrictions(
                    restrictions = restrictions,
                    alwaysUpdate = anime.updateStrategy == AnimeUpdateStrategy.ALWAYS_UPDATE,
                    completed = anime.status.toInt() == SAnime.COMPLETED,
                    unviewedCount = libraryAnime.unseenCount,
                    totalItems = libraryAnime.totalCount,
                    hasStarted = libraryAnime.hasStarted,
                    nextUpdate = anime.nextUpdate,
                    fetchWindowUpperBound = upperBound,
                )
            ) {
                continue
            }
            val remote = source(anime.source)?.items(anime.url) ?: continue
            addMissingItems(anime.id, remote)
            animeRepository.updateAnime(
                fetchInterval
                    .toAnimeUpdate(anime, now, timeZone, fetchInterval.getWindow(now, timeZone))
                    .copy(lastUpdate = now.toEpochMilliseconds()),
            )
            fetched++
        }
        return fetched
    }

    /**
     * Android's episode swipe prefs — start edge =
     * pref_episode_swipe_start_action (ToggleBookmark), end edge =
     * pref_episode_swipe_end_action (ToggleSeen).
     */
    override suspend fun swipeActions(): Pair<ItemSwipeAction, ItemSwipeAction> =
        libraryPreferences.swipeEpisodeEndAction().get().toItemSwipe() to
            libraryPreferences.swipeEpisodeStartAction().get().toItemSwipe()

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        episodeRepository.updateEpisode(EpisodeUpdate(id = itemId, seen = done))
        // Android's SetSeenStatus — watch events feed the achievement bus.
        if (done && achievementEventBus != null) {
            episodeRepository.getEpisodeById(itemId)?.let { episode ->
                achievementEventBus.tryEmit(
                    AchievementEvent.EpisodeWatched(
                        animeId = episode.animeId,
                        episodeNumber = episode.episodeNumber.toInt(),
                    ),
                )
                val allEpisodes = episodeRepository.getEpisodeByAnimeId(episode.animeId)
                if (allEpisodes.isNotEmpty() && allEpisodes.all { it.seen }) {
                    achievementEventBus.tryEmit(
                        AchievementEvent.AnimeCompleted(animeId = episode.animeId),
                    )
                }
            }
        }
    }

    override suspend fun setItemBookmark(itemId: Long, bookmarked: Boolean) {
        episodeRepository.updateEpisode(EpisodeUpdate(id = itemId, bookmark = bookmarked))
    }

    /** Playback position write — Android's lastSecondSeen + totalSeconds. */
    override suspend fun setItemPlayback(
        itemId: Long,
        positionSeconds: Long,
        totalSeconds: Long,
    ) {
        episodeRepository.updateEpisode(
            EpisodeUpdate(
                id = itemId,
                lastSecondSeen = positionSeconds,
                totalSeconds = totalSeconds,
            ),
        )
    }

    /** Android's multi-fillermark — the episode's own flag (canonical unaffected). */
    override suspend fun setItemFillermark(itemId: Long, fillermarked: Boolean) {
        episodeRepository.updateEpisode(EpisodeUpdate(id = itemId, fillermark = fillermarked))
    }

    /** Android's setCurrentSettingsAsDefault — entry flags → default prefs. */
    override suspend fun setEntryFlagsAsDefault(localId: Long) {
        getAnime.await(localId)?.let {
            libraryPreferences.setEpisodeSettingsDefault(it)
        }
    }

    /** Android's resetToDefaultSettings — default prefs → entry flags. */
    override suspend fun resetEntryFlagsToDefault(localId: Long) {
        getAnime.await(localId)?.let { setDefaultFlags.await(it) }
    }

    /** SetAsDefaultDialog's applyToExisting — defaults → every favorite. */
    override suspend fun applyDefaultFlagsToAll() {
        setDefaultFlags.awaitAll()
    }

    private fun SeasonAnime.toLocalSeason() = LocalSeason(
        id = anime.id,
        url = anime.url,
        title = anime.title,
        seasonNumber = anime.seasonNumber,
        sourceOrder = anime.seasonSourceOrder,
        thumbnailUrl = anime.thumbnailUrl,
        status = anime.status.toInt(),
        seen = seen,
        unseenCount = unseenCount,
        hasStarted = hasStarted,
        hasBookmarks = hasBookmarks,
        hasFillermarks = hasFillermarks,
        latestUpload = latestUpload,
        fetchedAt = fetchedAt,
        lastSeen = lastSeen,
        sourceLanguage = sourceManager.getOrStub(anime.source).lang,
    )

    override fun localSeasons(localId: Long): Flow<List<LocalSeason>> =
        animeRepository.getAnimeSeasonsByIdAsFlow(localId).map { seasons ->
            seasons.map { it.toLocalSeason() }
        }

    /** Android's `seasonFlags == 0L` load-time guard — defaults once. */
    override suspend fun ensureSeasonDefaults(localId: Long) {
        val anime = getAnime.await(localId) ?: return
        if (anime.seasonFlags == 0L) setSeasonDefaults.await(anime)
    }

    override suspend fun applySeasonFlag(localId: Long, op: SeasonFlagOp) {
        val anime = getAnime.await(localId) ?: return
        when (op) {
            is SeasonFlagOp.DownloadedFilter ->
                setSeasonFlags.awaitSetDownloadedFilter(anime, op.flag)
            is SeasonFlagOp.UnseenFilter ->
                setSeasonFlags.awaitSetUnseenFilter(anime, op.flag)
            is SeasonFlagOp.StartedFilter ->
                setSeasonFlags.awaitSetStartedFilter(anime, op.flag)
            is SeasonFlagOp.CompletedFilter ->
                setSeasonFlags.awaitSetCompletedFilter(anime, op.flag)
            is SeasonFlagOp.BookmarkedFilter ->
                setSeasonFlags.awaitSetBookmarkedFilter(anime, op.flag)
            is SeasonFlagOp.FillermarkedFilter ->
                setSeasonFlags.awaitSetFillermarkedFilter(anime, op.flag)
            is SeasonFlagOp.SortModeOrFlip ->
                setSeasonFlags.awaitSetSortingModeOrFlipOrder(anime, op.flag)
            is SeasonFlagOp.GridMode ->
                setSeasonFlags.awaitSetGridMode(anime, op.mode)
            is SeasonFlagOp.GridSize ->
                setSeasonFlags.awaitSetGridSize(anime, op.size)
            is SeasonFlagOp.DownloadedOverlay ->
                setSeasonFlags.awaitSetDownloadedOverlay(anime, op.on)
            is SeasonFlagOp.UnseenOverlay ->
                setSeasonFlags.awaitSetUnseenOverlay(anime, op.on)
            is SeasonFlagOp.LocalOverlay ->
                setSeasonFlags.awaitSetLocalOverlay(anime, op.on)
            is SeasonFlagOp.LangOverlay ->
                setSeasonFlags.awaitSetLangOverlay(anime, op.on)
            is SeasonFlagOp.ContinueOverlay ->
                setSeasonFlags.awaitSetContinueOverlay(anime, op.on)
            is SeasonFlagOp.DisplayMode ->
                setSeasonFlags.awaitSetDisplayMode(anime, op.flag)
        }
    }

    /** Android's setSeasonCurrentSettingsAsDefault — flags → season prefs. */
    override suspend fun setSeasonSettingsAsDefault(localId: Long, applyToExisting: Boolean) {
        val anime = getAnime.await(localId) ?: return
        libraryPreferences.setSeasonSettingsDefault(anime)
        if (applyToExisting) setSeasonDefaults.awaitAll()
    }

    override fun collections() = collectionRepository.getAllVisibleAnimeCollectionsAsFlow()

    override fun entryCollections(localId: Long) =
        collectionRepository.getVisibleCollectionsByAnimeIdAsFlow(localId)

    override suspend fun filterEntriesByCollection(
        collectionId: Long,
        entryIds: List<Long>,
    ): Set<Long> = entryIds.filterTo(HashSet()) { id ->
        collectionRepository.getCollectionsByAnimeId(id).any { it.id == collectionId }
    }

    override suspend fun setEntryCollections(localId: Long, collectionIds: List<Long>) {
        animeRepository.setAnimeCollections(localId, collectionIds)
    }

    override suspend fun createCollection(name: String): Long {
        val order = (
            collectionRepository.getAllAnimeCollections()
                .maxOfOrNull { it.order } ?: -1L
            ) + 1
        collectionRepository.insertAnimeCollection(
            Collection(id = 0, name = name, order = order, flags = 0L, hidden = false),
        )
        return collectionRepository.getAllAnimeCollections()
            .firstOrNull { it.name == name && it.order == order }?.id ?: -1L
    }

    override suspend fun renameCollection(collectionId: Long, name: String) {
        collectionRepository.updatePartialAnimeCollection(
            CollectionUpdate(id = collectionId, name = name),
        )
    }

    override suspend fun deleteCollection(collectionId: Long) {
        collectionRepository.deleteAnimeCollection(collectionId)
    }

    override suspend fun setAllItemsDone(localId: Long, done: Boolean) {
        val updates = getLocalItems(localId)
            .map { EpisodeUpdate(id = it.id, seen = done) }
        if (updates.isNotEmpty()) episodeRepository.updateAllEpisodes(updates)
    }

    override suspend fun recordItemOpen(itemId: Long) {
        // Android's incognito mode — history writes pause while enabled.
        if (incognitoMode()) return
        historyRepository.upsertAnimeHistory(
            AnimeHistoryUpdate(itemId, Clock.System.now()),
        )
    }

    /** Android's BasePreferences.incognitoMode — global history pause. */
    private fun incognitoMode(): Boolean =
        preferenceStore.getBoolean("__APP_STATE_incognito_mode", false).get()

    // Anime history has no duration column — seenAt on open is the row.

    override suspend fun resetHistoryRow(historyId: Long) =
        historyRepository.resetAnimeHistory(historyId)

    override suspend fun resetEntryHistory(entryId: Long) =
        historyRepository.resetHistoryByAnimeId(entryId)

    override suspend fun clearHistory(): Boolean =
        historyRepository.deleteAllAnimeHistory()

    /** Episode stream hosts — resolved lazily the way Android's player does. */
    override suspend fun hosters(sourceId: Long, episodeUrl: String): List<HosterEntry> {
        val source = sourceManager.get(sourceId)
            ?: return emptyList()
        val episode = SEpisode.create().apply { url = episodeUrl }
        return source.getHosterList(episode).map { hoster ->
            HosterEntry(
                url = hoster.hosterUrl,
                name = hoster.hosterName,
                internalData = hoster.internalData,
                lazy = hoster.lazy,
                videos = hoster.videoList?.map {
                    VideoEntry(url = it.videoUrl, title = it.videoTitle, timestamps = it.timestamps)
                },
            )
        }
    }

    /** Resolve a hoster's (or NO_HOSTER episode's) playable streams. */
    override suspend fun videos(sourceId: Long, target: String): List<VideoEntry> {
        val source = sourceManager.get(sourceId)
            ?: return emptyList()
        return source.getVideoList(eu.kanade.tachiyomi.animesource.model.Hoster(internalData = target))
            .map {
                VideoEntry(url = it.videoUrl, title = it.videoTitle, timestamps = it.timestamps)
            }
    }

    /**
     * Android's `aniSkipResponse` — the first track record's MAL id
     * (AniList resolved via GraphQL; TrackerManager ids MAL=1/AniList=2)
     * feeds the skip-times lookup. No tracks → empty like Android's
     * "No tracks found" early return.
     */
    override suspend fun skipTimes(
        localId: Long,
        episodeNumber: Int,
        durationSec: Double,
    ): List<eu.kanade.tachiyomi.animesource.model.TimeStamp> {
        val track = runCatching { trackRepository.getTracksByAnimeId(localId) }
            .getOrNull()?.firstOrNull() ?: return emptyList()
        val malId = when (track.trackerId) {
            TRACKER_MAL -> track.remoteId
            TRACKER_ANILIST -> aniSkip.getMalIdFromAL(track.remoteId)
            else -> return emptyList()
        }
        if (malId <= 0) return emptyList()
        return aniSkip.getResult(malId, episodeNumber, durationSec.toLong())
            .orEmpty()
    }

    private companion object {
        const val TRACKER_MAL = 1L
        const val TRACKER_ANILIST = 2L
    }

    /**
     * Android's `SetAnimeViewerFlags.awaitSetSkipIntroLength` — length
     * goes into ANIME_INTRO_MASK; 0 also sets ANIME_INTRO_DISABLE_MASK
     * (which is how Android expresses "intro disabled for this entry").
     */
    override suspend fun setSkipIntroLength(localId: Long, seconds: Int) {
        val anime = runCatching { animeRepository.getAnimeById(localId) }
            .getOrNull() ?: return
        val flag = seconds.toLong()
        animeRepository.updateAnime(
            AnimeUpdate(
                id = localId,
                viewerFlags = anime.viewerFlags
                    .and(Anime.ANIME_INTRO_MASK.inv())
                    .or(flag and Anime.ANIME_INTRO_MASK)
                    .let { flags ->
                        // (flag == 0).toLong().addHexZeros(14)
                        if (flag == 0L) {
                            flags or Anime.ANIME_INTRO_DISABLE_MASK
                        } else {
                            flags and Anime.ANIME_INTRO_DISABLE_MASK.inv()
                        }
                    },
            ),
        )
    }

    private fun RemoteDetails.toDomainAnime(sourceId: Long) =
        tachiyomi.domain.entries.shows.model.Anime.create().copy(
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status.toLong(),
            thumbnailUrl = thumbnailUrl,
            backgroundUrl = backgroundUrl,
            initialized = true,
            source = sourceId,
            fetchType = fetchType ?: FetchType.Episodes,
        )

    private fun Episode.toLocalItem(seasonNumber: Double = -1.0) = LocalItem(
        id = id,
        url = url,
        name = name,
        number = episodeNumber,
        dateUpload = dateUpload,
        done = seen,
        bookmarked = bookmark,
        lastSecondSeen = lastSecondSeen,
        totalSeconds = totalSeconds,
        fillermarked = fillermark || canonicalFiller == true,
        seasonNumber = seasonNumber,
    )
}

private class AnimeCarrierSource(
    private val source: AnimeCatalogueSource,
) : CarrierSource {

    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val supportsLatest: Boolean = source.supportsLatest

    private var activeFilters: AnimeFilterList? = null
    private var feedFilters: List<FeedFilter>? = null

    private fun activeFilters(): AnimeFilterList =
        activeFilters ?: source.getFilterList().also { activeFilters = it }

    override fun filters(): List<FeedFilter> =
        feedFilters ?: FeedFilter.wrapAll(activeFilters().list).also { feedFilters = it }

    override fun resetFilters(): List<FeedFilter> {
        activeFilters = null
        feedFilters = null
        return filters()
    }

    override suspend fun popular(page: Int) = source.getPopularAnime(page).toFeedPage()
    override suspend fun latest(page: Int) = source.getLatestUpdates(page).toFeedPage()

    override suspend fun search(page: Int, query: String): FeedPage =
        source.getSearchAnime(page, query, activeFilters()).toFeedPage()

    override suspend fun details(entryUrl: String, stubTitle: String): RemoteDetails? {
        val stub = SAnime.create().apply {
            url = entryUrl
            title = stubTitle
        }
        val anime = source.getAnimeDetails(stub)
        return RemoteDetails(
            url = anime.url,
            title = anime.title,
            author = anime.author,
            artist = anime.artist,
            description = anime.description,
            genre = anime.getGenres(),
            status = anime.status,
            thumbnailUrl = anime.thumbnail_url,
            backgroundUrl = anime.background_url,
            fetchType = anime.fetch_type,
        )
    }

    override suspend fun items(entryUrl: String): List<RemoteItem> {
        val stub = SAnime.create().apply {
            url = entryUrl
            title = entryUrl
        }
        val seasons = source.getSeasonList(stub)
        // Flat plugins echo the anime back as the only "season" — then the
        // episode list comes straight off the entry. Season-structured
        // plugins get one getEpisodeList per season, merged in order.
        val flat = seasons.size <= 1 && seasons.firstOrNull()?.url == entryUrl
        if (flat) {
            return source.getEpisodeList(stub).map { it.toRemoteItem() }
        }
        return seasons.flatMap { season ->
            runCatching { source.getEpisodeList(season) }
                .getOrDefault(emptyList())
                .map {
                    it.toRemoteItem(
                        seasonUrl = season.url,
                        seasonNumber = season.season_number,
                        seasonTitle = season.title,
                    )
                }
        }
    }

    private fun AnimesPage.toFeedPage() =
        FeedPage(
            entries = animes.map { FeedEntry(it.url, it.title, it.thumbnail_url, it.author, it.genre) },
            hasNextPage = hasNextPage,
        )

    private fun SEpisode.toRemoteItem(
        seasonUrl: String? = null,
        seasonNumber: Double = -1.0,
        seasonTitle: String? = null,
    ) = RemoteItem(
        url = url,
        name = name,
        number = episode_number.toDouble(),
        dateUpload = date_upload,
        scanlator = scanlator,
        summary = summary,
        previewUrl = preview_url,
        seasonUrl = seasonUrl,
        seasonNumber = seasonNumber,
        seasonTitle = seasonTitle,
    )
}

private fun LibraryPreferences.EpisodeSwipeAction.toItemSwipe() = when (this) {
    LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> ItemSwipeAction.ToggleDone
    LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> ItemSwipeAction.ToggleBookmark
    LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> ItemSwipeAction.ToggleFillermark
    LibraryPreferences.EpisodeSwipeAction.Download -> ItemSwipeAction.Download
    LibraryPreferences.EpisodeSwipeAction.Disabled -> ItemSwipeAction.Disabled
}
