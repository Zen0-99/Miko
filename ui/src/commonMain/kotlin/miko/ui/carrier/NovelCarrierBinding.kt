package miko.ui.carrier

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.TimeZone
import miko.ui.model.toDomainNovel
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.interactor.NovelFetchInterval
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.history.novel.model.NovelHistoryUpdate
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import kotlin.time.Clock

/** Books carrier — wraps the JS novel source manager + novel repositories. */
class NovelCarrierBinding(
    private val sourceManager: NovelSourceManager,
    private val getNovel: GetNovel,
    private val networkToLocalNovel: NetworkToLocalNovel,
    private val novelRepository: NovelRepository,
    private val chapterRepository: NovelChapterRepository,
    private val historyRepository: NovelHistoryRepository,
    private val collectionRepository: NovelCollectionRepository,
) : CarrierBinding {

    /** chapterId → open timestamp (ms) — feeds sessionReadDuration on close. */
    private val fetchInterval = NovelFetchInterval()
    private val openSessions = mutableMapOf<Long, Long>()

    override val isInitialized: StateFlow<Boolean> = sourceManager.isInitialized

    override val catalogueSources: Flow<List<CarrierSource>> =
        sourceManager.catalogueSources.map { sources ->
            sources.map { NovelCarrierSource(it) }
        }

    override fun source(sourceId: Long): CarrierSource? =
        (sourceManager.get(sourceId) as? NovelCatalogueSource)?.let(::NovelCarrierSource)

    private fun Novel.toLocalEntry() = LocalEntry(
        id = id,
        title = title,
        favorite = favorite,
        url = url,
        sourceId = source,
        sortDescending = sortDescending(),
        unreadOnly = unreadFilterRaw == Novel.CHAPTER_SHOW_UNREAD,
    )

    override fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?> =
        getNovel.subscribe(url, sourceId).map { novel -> novel?.toLocalEntry() }

    override suspend fun localEntryById(id: Long): LocalEntry? =
        runCatching { novelRepository.getNovelById(id) }.getOrNull()?.toLocalEntry()

    override suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry {
        val novel = networkToLocalNovel.await(details.toSNovel().toDomainNovel(sourceId))
        return novel.toLocalEntry()
    }

    override suspend fun setFavorite(localId: Long, favorite: Boolean) {
        novelRepository.updateNovelFavorite(localId, favorite)
    }

    override suspend fun setChapterSortDescending(localId: Long, descending: Boolean) {
        val novel = runCatching { novelRepository.getNovelById(localId) }.getOrNull() ?: return
        val direction = if (descending) Novel.CHAPTER_SORT_DESC else Novel.CHAPTER_SORT_ASC
        novelRepository.updateNovel(
            NovelUpdate(
                id = localId,
                chapterFlags = novel.chapterFlags
                    .and(Novel.CHAPTER_SORT_DIR_MASK.inv())
                    .or(direction),
            ),
        )
    }

    override suspend fun setChapterUnreadOnly(localId: Long, onlyUnread: Boolean) {
        val novel = runCatching { novelRepository.getNovelById(localId) }.getOrNull() ?: return
        val flag = if (onlyUnread) Novel.CHAPTER_SHOW_UNREAD else 0L
        novelRepository.updateNovel(
            NovelUpdate(
                id = localId,
                chapterFlags = novel.chapterFlags
                    .and(Novel.CHAPTER_UNREAD_MASK.inv())
                    .or(flag),
            ),
        )
    }

    private fun toLocalItem(chapter: tachiyomi.domain.items.chapter.model.NovelChapter) =
        LocalItem(
            id = chapter.id,
            url = chapter.url,
            name = chapter.name,
            done = chapter.read,
            bookmarked = chapter.bookmark,
        )

    override fun localItems(localId: Long): Flow<List<LocalItem>> =
        chapterRepository.getNovelChaptersByNovelIdAsFlow(localId).map { chapters ->
            chapters.map(::toLocalItem)
        }

    override suspend fun getLocalItems(localId: Long): List<LocalItem> =
        chapterRepository.getNovelChaptersByNovelId(localId).map(::toLocalItem)

    override suspend fun addMissingItems(localId: Long, remote: List<RemoteItem>) {
        val existing = getLocalItems(localId).mapTo(HashSet()) { it.url }
        val now = Clock.System.now().toEpochMilliseconds()
        val missing = remote.mapIndexedNotNull { index, item ->
            if (item.url in existing) {
                null
            } else {
                NovelChapter.create().copy(
                    novelId = localId,
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
        if (missing.isNotEmpty()) chapterRepository.addAllNovelChapters(missing)
    }

    override suspend fun refreshFavorites(): Int {
        var fetched = 0
        val now = Clock.System.now()
        val timeZone = TimeZone.currentSystemDefault()
        for (libraryNovel in novelRepository.getLibraryNovels()) {
            val novel = libraryNovel.novel
            val remote = source(novel.source)?.items(novel.url) ?: continue
            addMissingItems(novel.id, remote)
            // Android's update job: stamp lastUpdate + expected next_update
            // so the Upcoming feed has data.
            novelRepository.updateNovel(
                fetchInterval
                    .toNovelUpdate(
                        novel,
                        chapterRepository.getNovelChaptersByNovelId(novel.id),
                        now,
                        timeZone,
                        fetchInterval.getWindow(now, timeZone),
                    )
                    .copy(lastUpdate = now.toEpochMilliseconds()),
            )
            fetched++
        }
        return fetched
    }

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        chapterRepository.updateNovelChapter(NovelChapterUpdate(id = itemId, read = done))
    }

    override suspend fun setItemBookmark(itemId: Long, bookmarked: Boolean) {
        chapterRepository.updateNovelChapter(NovelChapterUpdate(id = itemId, bookmark = bookmarked))
    }

    override fun collections() = collectionRepository.subscribeAllVisible()

    override fun entryCollections(localId: Long) = flow {
        emit(collectionRepository.getVisibleCollectionsByNovelId(localId))
    }

    override suspend fun filterEntriesByCollection(
        collectionId: Long,
        entryIds: List<Long>,
    ): Set<Long> = entryIds.filterTo(HashSet()) { id ->
        collectionRepository.getCollectionsByNovelId(id).any { it.id == collectionId }
    }

    override suspend fun setEntryCollections(localId: Long, collectionIds: List<Long>) {
        novelRepository.setNovelCollections(localId, collectionIds)
    }

    override suspend fun createCollection(name: String): Long =
        collectionRepository.insert(
            name = name,
            order = (
                collectionRepository.getAllNovelCollections()
                    .maxOfOrNull { it.order } ?: -1L
                ) + 1,
            flags = 0L,
        ) ?: -1L

    override suspend fun renameCollection(collectionId: Long, name: String) {
        collectionRepository.update(collectionId, name, null, null, null)
    }

    override suspend fun deleteCollection(collectionId: Long) {
        collectionRepository.delete(collectionId)
    }

    override suspend fun setAllItemsDone(localId: Long, done: Boolean) {
        val updates = getLocalItems(localId)
            .map { NovelChapterUpdate(id = it.id, read = done) }
        if (updates.isNotEmpty()) chapterRepository.updateAllNovelChapters(updates)
    }

    override suspend fun recordItemOpen(itemId: Long) {
        openSessions[itemId] = Clock.System.now().toEpochMilliseconds()
        historyRepository.upsertNovelHistory(
            NovelHistoryUpdate(itemId, Clock.System.now(), 0),
        )
    }

    override suspend fun recordItemClose(itemId: Long) {
        val started = openSessions.remove(itemId) ?: return
        val duration = Clock.System.now().toEpochMilliseconds() - started
        historyRepository.upsertNovelHistory(
            NovelHistoryUpdate(itemId, Clock.System.now(), duration),
        )
    }

    /** Chapter body text — the books reader's content source. */
    override suspend fun itemText(sourceId: Long, itemUrl: String): String {
        val catalogue = sourceManager.get(sourceId) as? NovelCatalogueSource
            ?: return ""
        val chapter = SNovelChapter.create().apply { url = itemUrl }
        return catalogue.getChapterText(chapter)
    }

    private fun RemoteDetails.toSNovel(): SNovel = SNovel.create().also {
        it.url = url
        it.title = title
        it.artist = artist
        it.author = author
        it.description = description
        it.genre = genre?.joinToString(", ")
        it.status = status
        it.thumbnail_url = thumbnailUrl
        it.initialized = true
    }
}

private class NovelCarrierSource(
    private val source: NovelCatalogueSource,
) : CarrierSource {

    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val supportsLatest: Boolean = source.supportsLatest

    // The single mutable filter list for this source session — FeedFilter
    // edits write through to these objects and listing calls serialize
    // them back. Re-fetching per call would silently discard edits.
    private var activeFilters: NovelFilterList? = null
    private var feedFilters: List<FeedFilter>? = null

    private fun activeFilters(): NovelFilterList =
        activeFilters ?: source.getFilterList().also { activeFilters = it }

    override fun filters(): List<FeedFilter> =
        feedFilters ?: FeedFilter.wrapAll(activeFilters().list).also { feedFilters = it }

    override fun resetFilters(): List<FeedFilter> {
        activeFilters = null
        feedFilters = null
        return filters()
    }

    override suspend fun popular(page: Int) =
        source.getPopularNovels(page, activeFilters()).toFeedPage()

    override suspend fun latest(page: Int) =
        source.getLatestUpdates(page, activeFilters()).toFeedPage()

    override suspend fun search(page: Int, query: String): FeedPage =
        source.getSearchNovels(page, query, activeFilters()).toFeedPage()

    override suspend fun details(entryUrl: String, stubTitle: String): RemoteDetails? {
        val stub = SNovel.create().apply {
            url = entryUrl
            title = stubTitle
        }
        val novel = source.getNovelDetails(stub)
        return RemoteDetails(
            url = novel.url,
            title = novel.title,
            author = novel.author,
            artist = novel.artist,
            description = novel.description,
            genre = novel.getGenres(),
            status = novel.status,
            thumbnailUrl = novel.thumbnail_url,
        )
    }

    override suspend fun items(entryUrl: String): List<RemoteItem> {
        val stub = SNovel.create().apply {
            url = entryUrl
            title = entryUrl
        }
        return source.getChapterList(stub).map { it.toRemoteItem() }
    }

    private fun eu.kanade.tachiyomi.novelsource.model.NovelsPage.toFeedPage() =
        FeedPage(
            entries = novels.map { FeedEntry(it.url, it.title, it.thumbnail_url, it.author) },
            hasNextPage = hasNextPage,
        )

    private fun SNovelChapter.toRemoteItem() = RemoteItem(
        url = url,
        name = name,
        number = chapter_number.toDouble(),
        dateUpload = date_upload,
        scanlator = scanlator,
    )
}
