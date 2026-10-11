package miko.ui.carrier

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import miko.ui.model.toDomainNovel
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
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
) : CarrierBinding {

    /** chapterId → open timestamp (ms) — feeds sessionReadDuration on close. */
    private val openSessions = mutableMapOf<Long, Long>()

    override val isInitialized: StateFlow<Boolean> = sourceManager.isInitialized

    override val catalogueSources: Flow<List<CarrierSource>> =
        sourceManager.catalogueSources.map { sources ->
            sources.map { NovelCarrierSource(it) }
        }

    override fun source(sourceId: Long): CarrierSource? =
        (sourceManager.get(sourceId) as? NovelCatalogueSource)?.let(::NovelCarrierSource)

    override fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?> =
        getNovel.subscribe(url, sourceId).map { novel ->
            novel?.let { LocalEntry(id = it.id, title = it.title, favorite = it.favorite) }
        }

    override suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry {
        val novel = networkToLocalNovel.await(details.toSNovel().toDomainNovel(sourceId))
        return LocalEntry(id = novel.id, title = novel.title, favorite = novel.favorite)
    }

    override suspend fun setFavorite(localId: Long, favorite: Boolean) {
        novelRepository.updateNovelFavorite(localId, favorite)
    }

    override fun localItems(localId: Long): Flow<List<LocalItem>> =
        chapterRepository.getNovelChaptersByNovelIdAsFlow(localId).map { chapters ->
            chapters.map { LocalItem(id = it.id, url = it.url, name = it.name, done = it.read) }
        }

    override suspend fun getLocalItems(localId: Long): List<LocalItem> =
        chapterRepository.getNovelChaptersByNovelId(localId).map {
            LocalItem(id = it.id, url = it.url, name = it.name, done = it.read)
        }

    override suspend fun addMissingItems(localId: Long, remote: List<RemoteItem>) {
        val existing = getLocalItems(localId).mapTo(HashSet()) { it.url }
        val missing = remote.mapIndexedNotNull { index, item ->
            if (item.url in existing) {
                null
            } else {
                NovelChapter.create().copy(
                    novelId = localId,
                    url = item.url,
                    name = item.name,
                    dateUpload = item.dateUpload,
                    chapterNumber = item.number,
                    scanlator = item.scanlator,
                    sourceOrder = index.toLong(),
                )
            }
        }
        if (missing.isNotEmpty()) chapterRepository.addAllNovelChapters(missing)
    }

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        chapterRepository.updateNovelChapter(NovelChapterUpdate(id = itemId, read = done))
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

    override suspend fun popular(page: Int) = source.getPopularNovels(page).toFeedPage()
    override suspend fun latest(page: Int) = source.getLatestUpdates(page).toFeedPage()

    override suspend fun search(page: Int, query: String): FeedPage =
        source.getSearchNovels(page, query, source.getFilterList()).toFeedPage()

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
