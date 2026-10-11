package miko.ios

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.FeedEntry
import miko.ui.carrier.FeedPage
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.LocalItem
import miko.ui.carrier.PageEntry
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.history.manga.model.MangaHistoryUpdate
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
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
) : CarrierBinding {

    /** chapterId → open timestamp (ms) — feeds sessionReadDuration on close. */
    private val openSessions = mutableMapOf<Long, Long>()

    override val isInitialized: StateFlow<Boolean> = sourceManager.isInitialized

    override val catalogueSources: Flow<List<CarrierSource>> =
        sourceManager.catalogueSources.map { sources ->
            sources.map { MangaCarrierSource(it) }
        }

    override fun source(sourceId: Long): CarrierSource? =
        (sourceManager.get(sourceId) as? CatalogueSource)?.let(::MangaCarrierSource)

    override fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?> =
        getManga.subscribe(url, sourceId).map { manga ->
            manga?.let {
                LocalEntry(id = it.id, title = it.title, favorite = it.favorite, url = it.url, sourceId = it.source)
            }
        }

    override suspend fun localEntryById(id: Long): LocalEntry? =
        runCatching { mangaRepository.getMangaById(id) }.getOrNull()?.let {
            LocalEntry(id = it.id, title = it.title, favorite = it.favorite, url = it.url, sourceId = it.source)
        }

    override suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry {
        val manga = networkToLocalManga.await(details.toDomainManga(sourceId))
        return LocalEntry(
            id = manga.id,
            title = manga.title,
            favorite = manga.favorite,
            url = manga.url,
            sourceId = manga.source,
        )
    }

    override suspend fun setFavorite(localId: Long, favorite: Boolean) {
        mangaRepository.updateManga(MangaUpdate(id = localId, favorite = favorite))
    }

    override fun localItems(localId: Long): Flow<List<LocalItem>> = flow {
        // getChapterByMangaIdAsFlow is suspend (unlike the novel repo's).
        emitAll(
            chapterRepository.getChapterByMangaIdAsFlow(localId).map { chapters ->
                chapters.map {
                    LocalItem(id = it.id, url = it.url, name = it.name, done = it.read)
                }
            },
        )
    }

    override suspend fun getLocalItems(localId: Long): List<LocalItem> =
        chapterRepository.getChapterByMangaId(localId).map {
            LocalItem(id = it.id, url = it.url, name = it.name, done = it.read)
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
        for (libraryManga in mangaRepository.getLibraryManga()) {
            val manga = libraryManga.manga
            val remote = source(manga.source)?.items(manga.url) ?: continue
            addMissingItems(manga.id, remote)
            fetched++
        }
        return fetched
    }

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        chapterRepository.updateChapter(ChapterUpdate(id = itemId, read = done))
    }

    override suspend fun setAllItemsDone(localId: Long, done: Boolean) {
        val updates = getLocalItems(localId)
            .map { ChapterUpdate(id = it.id, read = done) }
        if (updates.isNotEmpty()) chapterRepository.updateAllChapters(updates)
    }

    override suspend fun recordItemOpen(itemId: Long) {
        openSessions[itemId] = Clock.System.now().toEpochMilliseconds()
        historyRepository.upsertMangaHistory(
            MangaHistoryUpdate(itemId, Clock.System.now(), 0),
        )
    }

    override suspend fun recordItemClose(itemId: Long) {
        val started = openSessions.remove(itemId) ?: return
        val duration = Clock.System.now().toEpochMilliseconds() - started
        historyRepository.upsertMangaHistory(
            MangaHistoryUpdate(itemId, Clock.System.now(), duration),
        )
    }

    /** Chapter page images — the manga reader's page source. */
    override suspend fun pageList(sourceId: Long, chapterUrl: String): List<PageEntry> {
        val catalogue = sourceManager.get(sourceId) as? CatalogueSource
            ?: return emptyList()
        val chapter = SChapter.create().apply { url = chapterUrl }
        return catalogue.getPageList(chapter).map {
            PageEntry(index = it.index, url = it.url, imageUrl = it.imageUrl)
        }
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

    override suspend fun popular(page: Int) = source.getPopularManga(page).toFeedPage()
    override suspend fun latest(page: Int) = source.getLatestUpdates(page).toFeedPage()

    override suspend fun search(page: Int, query: String): FeedPage =
        source.getSearchManga(page, query, source.getFilterList()).toFeedPage()

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
            entries = mangas.map { FeedEntry(it.url, it.title, it.thumbnail_url, it.author) },
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
