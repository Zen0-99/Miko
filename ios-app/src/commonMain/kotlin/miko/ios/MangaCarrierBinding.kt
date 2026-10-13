package miko.ios

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.ChapterSortMode
import miko.ui.carrier.FeedEntry
import miko.ui.carrier.FeedFilter
import miko.ui.carrier.FeedPage
import miko.ui.carrier.ItemSwipeAction
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.LocalItem
import miko.ui.carrier.PageEntry
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem
import miko.ui.download.ItemPayload
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.TriState
import tachiyomi.data.achievement.handler.AchievementEventBus
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.achievement.model.AchievementEvent
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.manga.model.MangaHistoryUpdate
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import kotlin.time.Clock

/** Manga carrier — wraps the JS manga source manager + manga repositories. */
class MangaCarrierBinding(
    private val sourceManager: MangaSourceManager,
    private val getManga: GetManga,
    private val networkToLocalManga: NetworkToLocalManga,
    private val mangaRepository: MangaRepository,
    private val chapterRepository: ChapterRepository,
    private val historyRepository: MangaHistoryRepository,
    private val collectionRepository: MangaCollectionRepository,
    private val databaseHandler: MangaDatabaseHandler,
    private val preferenceStore: PreferenceStore,
    private val achievementEventBus: AchievementEventBus? = null,
) : CarrierBinding {

    /** chapterId → open timestamp (ms) — feeds sessionReadDuration on close. */
    private val fetchInterval = MangaFetchInterval(GetChaptersByMangaId(chapterRepository))
    private val openSessions = mutableMapOf<Long, Long>()
    private val libraryPreferences = LibraryPreferences(preferenceStore)
    private val setDefaultFlags = SetMangaDefaultChapterFlags(
        libraryPreferences,
        SetMangaChapterFlags(mangaRepository),
        GetMangaFavorites(mangaRepository),
    )

    override val isInitialized: StateFlow<Boolean> = sourceManager.isInitialized

    override val catalogueSources: Flow<List<CarrierSource>> =
        sourceManager.catalogueSources.map { sources ->
            sources.map { MangaCarrierSource(it) }
        }

    override fun source(sourceId: Long): CarrierSource? =
        (sourceManager.get(sourceId) as? CatalogueSource)?.let(::MangaCarrierSource)

    private fun Manga.toLocalEntry() = LocalEntry(
        id = id,
        title = title,
        favorite = favorite,
        url = url,
        sourceId = source,
        sortDescending = sortDescending(),
        sortMode = when (sorting) {
            Manga.CHAPTER_SORTING_SOURCE -> ChapterSortMode.SOURCE
            Manga.CHAPTER_SORTING_UPLOAD_DATE -> ChapterSortMode.UPLOAD_DATE
            Manga.CHAPTER_SORTING_ALPHABET -> ChapterSortMode.ALPHABET
            else -> ChapterSortMode.NUMBER
        },
        bookmarkedFilter = bookmarkedFilter,
        displayByNumber = displayMode == Manga.CHAPTER_DISPLAY_NUMBER,
        unreadFilter = unreadFilter,
        viewer = (viewerFlags and MANGA_READING_MODE_MASK).toInt(),
        viewerOrientation = (viewerFlags and MANGA_ORIENTATION_MASK).toInt(),
    )

    override fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?> =
        getManga.subscribe(url, sourceId).map { manga -> manga?.toLocalEntry() }

    override suspend fun localEntryById(id: Long): LocalEntry? =
        runCatching { mangaRepository.getMangaById(id) }.getOrNull()?.toLocalEntry()

    override fun favoritedUrls(sourceId: Long): Flow<Set<String>> =
        GetMangaFavorites(mangaRepository).subscribe(sourceId).map { list ->
            list.mapTo(HashSet()) { it.url }
        }

    override suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry {
        val manga = networkToLocalManga.await(details.toDomainManga(sourceId))
        return manga.toLocalEntry()
    }

    override suspend fun setFavorite(localId: Long, favorite: Boolean) {
        mangaRepository.updateManga(MangaUpdate(id = localId, favorite = favorite))
    }

    override suspend fun setChapterSortDescending(localId: Long, descending: Boolean) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        val direction = if (descending) Manga.CHAPTER_SORT_DESC else Manga.CHAPTER_SORT_ASC
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                chapterFlags = manga.chapterFlags
                    .and(Manga.CHAPTER_SORT_DIR_MASK.inv())
                    .or(direction),
            ),
        )
    }

    override suspend fun setChapterUnreadFilter(localId: Long, state: TriState) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        val flag = when (state) {
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
            TriState.DISABLED -> 0L
        }
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                chapterFlags = manga.chapterFlags
                    .and(Manga.CHAPTER_UNREAD_MASK.inv())
                    .or(flag),
            ),
        )
    }

    override suspend fun setChapterSortMode(localId: Long, mode: ChapterSortMode) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        val bits = when (mode) {
            ChapterSortMode.SOURCE -> Manga.CHAPTER_SORTING_SOURCE
            ChapterSortMode.UPLOAD_DATE -> Manga.CHAPTER_SORTING_UPLOAD_DATE
            ChapterSortMode.ALPHABET -> Manga.CHAPTER_SORTING_ALPHABET
            ChapterSortMode.NUMBER -> Manga.CHAPTER_SORTING_NUMBER
        }
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                chapterFlags = manga.chapterFlags
                    .and(Manga.CHAPTER_SORTING_MASK.inv())
                    .or(bits),
            ),
        )
    }

    override suspend fun setChapterBookmarkedFilter(localId: Long, state: TriState) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        val flag = when (state) {
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
            TriState.DISABLED -> 0L
        }
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                chapterFlags = manga.chapterFlags
                    .and(Manga.CHAPTER_BOOKMARKED_MASK.inv())
                    .or(flag),
            ),
        )
    }

    override suspend fun setChapterDisplayByNumber(localId: Long, byNumber: Boolean) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        val flag = if (byNumber) Manga.CHAPTER_DISPLAY_NUMBER else 0L
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                chapterFlags = manga.chapterFlags
                    .and(Manga.CHAPTER_DISPLAY_MASK.inv())
                    .or(flag),
            ),
        )
    }

    /**
     * Android's swipe prefs — the accessor names are swapped against the
     * pref keys upstream (start reads _start_ after the double swap):
     * start edge = pref_chapter_swipe_start_action (ToggleBookmark),
     * end edge = pref_chapter_swipe_end_action (ToggleRead).
     */
    override suspend fun swipeActions(): Pair<ItemSwipeAction, ItemSwipeAction> =
        libraryPreferences.swipeChapterEndAction().get().toItemSwipe() to
            libraryPreferences.swipeChapterStartAction().get().toItemSwipe()

    override suspend fun setViewer(localId: Long, flag: Int) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                viewerFlags = manga.viewerFlags
                    .and(MANGA_READING_MODE_MASK.inv())
                    .or(flag.toLong()),
            ),
        )
    }

    override suspend fun setViewerOrientation(localId: Long, flag: Int) {
        val manga = runCatching { mangaRepository.getMangaById(localId) }.getOrNull() ?: return
        mangaRepository.updateManga(
            MangaUpdate(
                id = localId,
                viewerFlags = manga.viewerFlags
                    .and(MANGA_ORIENTATION_MASK.inv())
                    .or(flag.toLong()),
            ),
        )
    }

    override fun localItems(localId: Long): Flow<List<LocalItem>> = flow {
        // getChapterByMangaIdAsFlow is suspend (unlike the novel repo's).
        // applyScanlatorFilter mirrors Android's chapter feed — excluded
        // scanlators' rows never reach the UI's done/bookmark decoration.
        emitAll(
            chapterRepository.getChapterByMangaIdAsFlow(
                localId,
                applyScanlatorFilter = true,
            ).map { chapters ->
                chapters.map {
                    LocalItem(
                        id = it.id,
                        url = it.url,
                        name = it.name,
                        number = it.chapterNumber,
                        dateUpload = it.dateUpload,
                        done = it.read,
                        lastPageRead = it.lastPageRead.toInt(),
                        bookmarked = it.bookmark,
                    )
                }
            },
        )
    }

    override suspend fun getLocalItems(localId: Long): List<LocalItem> =
        chapterRepository.getChapterByMangaId(localId).map {
            LocalItem(
                id = it.id,
                url = it.url,
                name = it.name,
                number = it.chapterNumber,
                dateUpload = it.dateUpload,
                done = it.read,
                lastPageRead = it.lastPageRead.toInt(),
                bookmarked = it.bookmark,
            )
        }

    override suspend fun addMissingItems(localId: Long, remote: List<RemoteItem>) {
        val existing = getLocalItems(localId).mapTo(HashSet()) { it.url }
        val now = Clock.System.now().toEpochMilliseconds()
        val missing = remote.mapIndexedNotNull { index, item ->
            if (item.url in existing) {
                null
            } else {
                Chapter.create().copy(
                    mangaId = localId,
                    url = item.url,
                    name = item.name,
                    dateUpload = item.dateUpload,
                    dateFetch = now,
                    chapterNumber = item.number,
                    scanlator = item.scanlator,
                    sourceOrder = index.toLong(),
                )
            }
        }
        if (missing.isNotEmpty()) chapterRepository.addAllChapters(missing)
    }

    override suspend fun refreshFavorites(): Int {
        var fetched = 0
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        for (libraryManga in mangaRepository.getLibraryManga()) {
            val manga = libraryManga.manga
            val remote = source(manga.source)?.items(manga.url) ?: continue
            addMissingItems(manga.id, remote)
            mangaRepository.updateManga(
                fetchInterval
                    .toMangaUpdate(manga, now, timeZone, fetchInterval.getWindow(now, timeZone))
                    .copy(lastUpdate = now.toEpochMilliseconds()),
            )
            fetched++
        }
        return fetched
    }

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        chapterRepository.updateChapter(ChapterUpdate(id = itemId, read = done))
        // Android's SetReadStatus — read events feed the achievement bus.
        if (done && achievementEventBus != null) {
            chapterRepository.getChapterById(itemId)?.let { chapter ->
                achievementEventBus.tryEmit(
                    AchievementEvent.ChapterRead(
                        mangaId = chapter.mangaId,
                        chapterNumber = chapter.chapterNumber.toInt(),
                    ),
                )
                val allChapters = chapterRepository.getChapterByMangaId(chapter.mangaId)
                if (allChapters.isNotEmpty() && allChapters.all { it.read }) {
                    achievementEventBus.tryEmit(
                        AchievementEvent.MangaCompleted(mangaId = chapter.mangaId),
                    )
                }
            }
        }
    }

    override suspend fun setItemPageProgress(itemId: Long, page: Int) {
        chapterRepository.updateChapter(
            ChapterUpdate(id = itemId, lastPageRead = page.toLong()),
        )
    }

    override suspend fun setItemBookmark(itemId: Long, bookmarked: Boolean) {
        chapterRepository.updateChapter(ChapterUpdate(id = itemId, bookmark = bookmarked))
    }

    /** Android's ScanlatorFilterDialog — per-entry exclusion list. */
    override suspend fun getItemScanlators(localId: Long): Set<String> =
        chapterRepository.getScanlatorsByMangaId(localId).toSet()

    override fun excludedScanlators(localId: Long): Flow<Set<String>> =
        databaseHandler.subscribeToList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(localId)
        }.map { it.toSet() }

    /** Android's SetExcludedScanlators — diff insert/remove in one txn. */
    override suspend fun setExcludedScanlators(localId: Long, excluded: Set<String>) {
        databaseHandler.await(inTransaction = true) {
            val current = databaseHandler.awaitList {
                excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(localId)
            }.toSet()
            (excluded - current).forEach { scanlator ->
                excluded_scanlatorsQueries.insert(localId, scanlator)
            }
            val removed = current - excluded
            if (removed.isNotEmpty()) {
                excluded_scanlatorsQueries.remove(localId, removed)
            }
        }
    }

    /** Android's setCurrentSettingsAsDefault — entry flags → default prefs. */
    override suspend fun setEntryFlagsAsDefault(localId: Long) {
        getManga.await(localId)?.let { libraryPreferences.setChapterSettingsDefault(it) }
    }

    /** Android's resetToDefaultSettings — default prefs → entry flags. */
    override suspend fun resetEntryFlagsToDefault(localId: Long) {
        getManga.await(localId)?.let { setDefaultFlags.await(it) }
    }

    /** SetAsDefaultDialog's applyToExisting — defaults → every favorite. */
    override suspend fun applyDefaultFlagsToAll() {
        setDefaultFlags.awaitAll()
    }

    override fun collections() = collectionRepository.getAllVisibleMangaCollectionsAsFlow()

    override fun entryCollections(localId: Long) =
        collectionRepository.getVisibleCollectionsByMangaIdAsFlow(localId)

    override suspend fun filterEntriesByCollection(
        collectionId: Long,
        entryIds: List<Long>,
    ): Set<Long> = collectionRepository.getMangaIdsByCollection(collectionId)
        .toSet()
        .intersect(entryIds.toSet())

    override suspend fun setEntryCollections(localId: Long, collectionIds: List<Long>) {
        mangaRepository.setMangaCollections(localId, collectionIds)
    }

    override suspend fun createCollection(name: String): Long {
        val order = (
            collectionRepository.getAllMangaCollections()
                .maxOfOrNull { it.order } ?: -1L
            ) + 1
        collectionRepository.insertMangaCollection(
            Collection(id = 0, name = name, order = order, flags = 0L, hidden = false),
        )
        return collectionRepository.getAllMangaCollections()
            .firstOrNull { it.name == name && it.order == order }?.id ?: -1L
    }

    override suspend fun renameCollection(collectionId: Long, name: String) {
        collectionRepository.updatePartialMangaCollection(
            CollectionUpdate(id = collectionId, name = name),
        )
    }

    override suspend fun deleteCollection(collectionId: Long) {
        collectionRepository.deleteMangaCollection(collectionId)
    }

    override suspend fun setAllItemsDone(localId: Long, done: Boolean) {
        val updates = getLocalItems(localId)
            .map { ChapterUpdate(id = it.id, read = done) }
        if (updates.isNotEmpty()) chapterRepository.updateAllChapters(updates)
    }

    override suspend fun recordItemOpen(itemId: Long) {
        // Android's incognito mode — history writes pause while enabled.
        if (incognitoMode()) return
        openSessions[itemId] = Clock.System.now().toEpochMilliseconds()
        historyRepository.upsertMangaHistory(
            MangaHistoryUpdate(itemId, Clock.System.now(), 0),
        )
    }

    /** Android's BasePreferences.incognitoMode — global history pause. */
    private fun incognitoMode(): Boolean =
        preferenceStore.getBoolean("__APP_STATE_incognito_mode", false).get()

    override suspend fun recordItemClose(itemId: Long) {
        val started = openSessions.remove(itemId) ?: return
        val duration = Clock.System.now().toEpochMilliseconds() - started
        historyRepository.upsertMangaHistory(
            MangaHistoryUpdate(itemId, Clock.System.now(), duration),
        )
    }

    override suspend fun resetHistoryRow(historyId: Long) =
        historyRepository.resetMangaHistory(historyId)

    override suspend fun resetEntryHistory(entryId: Long) =
        historyRepository.resetHistoryByMangaId(entryId)

    override suspend fun clearHistory(): Boolean =
        historyRepository.deleteAllMangaHistory()

    /** Chapter page images — the manga reader's page source. */
    override suspend fun pageList(sourceId: Long, chapterUrl: String): List<PageEntry> {
        val catalogue = sourceManager.get(sourceId) as? CatalogueSource
            ?: return emptyList()
        val chapter = SChapter.create().apply { url = chapterUrl }
        return catalogue.getPageList(chapter).map {
            PageEntry(index = it.index, url = it.url, imageUrl = it.imageUrl)
        }
    }

    /** Chapter download payload — numbered image files per page. */
    override suspend fun itemPayload(
        sourceId: Long,
        itemUrl: String,
    ): ItemPayload? {
        val pages = pageList(sourceId, itemUrl)
        if (pages.isEmpty()) return null
        return ItemPayload(
            files = pages.map { page ->
                // String.format is JVM-only — padStart works on K/N too.
                page.index.toString().padStart(3, '0') + ".img" to (page.imageUrl ?: page.url)
            },
        )
    }

    private fun RemoteDetails.toDomainManga(sourceId: Long) =
        tachiyomi.domain.entries.manga.model.Manga.create().copy(
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status.toLong(),
            thumbnailUrl = thumbnailUrl,
            initialized = true,
            source = sourceId,
        )
}

private class MangaCarrierSource(
    private val source: CatalogueSource,
) : CarrierSource {

    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val supportsLatest: Boolean = source.supportsLatest

    private var activeFilters: FilterList? = null
    private var feedFilters: List<FeedFilter>? = null

    private fun activeFilters(): FilterList =
        activeFilters ?: source.getFilterList().also { activeFilters = it }

    override fun filters(): List<FeedFilter> =
        feedFilters ?: FeedFilter.wrapAll(activeFilters().list).also { feedFilters = it }

    override fun resetFilters(): List<FeedFilter> {
        activeFilters = null
        feedFilters = null
        return filters()
    }

    override suspend fun popular(page: Int) = source.getPopularManga(page).toFeedPage()
    override suspend fun latest(page: Int) = source.getLatestUpdates(page).toFeedPage()

    override suspend fun search(page: Int, query: String): FeedPage =
        source.getSearchManga(page, query, activeFilters()).toFeedPage()

    override suspend fun details(entryUrl: String, stubTitle: String): RemoteDetails? {
        val stub = SManga.create().apply {
            url = entryUrl
            title = stubTitle
        }
        val manga = source.getMangaDetails(stub)
        return RemoteDetails(
            url = manga.url,
            title = manga.title,
            author = manga.author,
            artist = manga.artist,
            description = manga.description,
            genre = manga.getGenres(),
            status = manga.status,
            thumbnailUrl = manga.thumbnail_url,
        )
    }

    override suspend fun items(entryUrl: String): List<RemoteItem> {
        val stub = SManga.create().apply {
            url = entryUrl
            title = entryUrl
        }
        return source.getChapterList(stub).map { it.toRemoteItem() }
    }

    private fun MangasPage.toFeedPage() =
        FeedPage(
            entries = mangas.map { FeedEntry(it.url, it.title, it.thumbnail_url, it.author, it.genre) },
            hasNextPage = hasNextPage,
        )

    private fun SChapter.toRemoteItem() = RemoteItem(
        url = url,
        name = name,
        number = chapter_number.toDouble(),
        dateUpload = date_upload,
        scanlator = scanlator,
    )
}

/** Android's `ReadingMode.MASK` — the viewerFlags' low three bits. */
private const val MANGA_READING_MODE_MASK = 0x00000007L

/** Android's `ReaderOrientation.MASK` — the viewerFlags' 0x38 bits. */
private const val MANGA_ORIENTATION_MASK = 0x00000038L

private fun LibraryPreferences.ChapterSwipeAction.toItemSwipe() = when (this) {
    LibraryPreferences.ChapterSwipeAction.ToggleRead -> ItemSwipeAction.ToggleDone
    LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> ItemSwipeAction.ToggleBookmark
    LibraryPreferences.ChapterSwipeAction.Download -> ItemSwipeAction.Download
    LibraryPreferences.ChapterSwipeAction.Disabled -> ItemSwipeAction.Disabled
}
