package tachiyomi.data.entries.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.NovelUpdateStrategyColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import java.time.LocalDate
import java.time.ZoneId
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.library.novel.LibraryNovel

class NovelRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelRepository {

    override suspend fun getNovelById(id: Long): Novel {
        return handler.awaitOne { novelsQueries.getNovelById(id, NovelMapper::mapNovel) }
    }

    override suspend fun getNovelByUrlAndSourceId(url: String, sourceId: Long): Novel? {
        return handler.awaitOneOrNull {
            novelsQueries.getNovelByUrlAndSource(url, sourceId, NovelMapper::mapNovel)
        }
    }

    override fun getNovelByIdAsFlow(id: Long): kotlinx.coroutines.flow.Flow<Novel> {
        return handler.subscribeToOne { novelsQueries.getNovelById(id, NovelMapper::mapNovel) }
    }

    override fun getNovelByUrlAndSourceIdAsFlow(url: String, sourceId: Long): kotlinx.coroutines.flow.Flow<Novel?> {
        return handler.subscribeToOneOrNull {
            novelsQueries.getNovelByUrlAndSource(url, sourceId, NovelMapper::mapNovel)
        }
    }

    override suspend fun getNovelFavorites(): List<Novel> {
        return handler.awaitList { novelsQueries.getFavorites(NovelMapper::mapNovel) }
    }

    override fun getNovelFavoritesBySourceId(sourceId: Long): Flow<List<Novel>> {
        return handler.subscribeToList { novelsQueries.getFavoriteBySourceId(sourceId, NovelMapper::mapNovel) }
    }

    override suspend fun getLibraryNovels(): List<LibraryNovel> {
        return handler.awaitList { novelLibraryViewQueries.library(NovelMapper::mapLibraryNovel) }
    }

    override fun getLibraryNovelsAsFlow(): kotlinx.coroutines.flow.Flow<List<LibraryNovel>> {
        return handler.subscribeToList { novelLibraryViewQueries.library(NovelMapper::mapLibraryNovel) }
    }

    override suspend fun insertNovel(novel: Novel): Long? {
        return handler.await(inTransaction = true) {
            novelsQueries.insert(
                novel.source,
                novel.url,
                novel.artist,
                novel.author,
                novel.description,
                novel.genre,
                novel.title,
                novel.status,
                novel.thumbnailUrl,
                novel.favorite,
                novel.lastUpdate,
                novel.nextUpdate,
                novel.initialized,
                novel.viewerFlags,
                novel.chapterFlags,
                novel.coverLastModified,
                novel.dateAdded,
                novel.updateStrategy,
                novel.fetchInterval.toLong(),
                novel.version,
            )
            novelsQueries.selectLastInsertedRowId().executeAsOneOrNull()
        }
    }

    override suspend fun updateNovelFavorite(novelId: Long, favorite: Boolean): Boolean {
        return try {
            handler.await {
                novelsQueries.update(
                    source = null,
                    url = null,
                    artist = null,
                    author = null,
                    description = null,
                    genre = null,
                    title = null,
                    status = null,
                    thumbnailUrl = null,
                    favorite = favorite,
                    lastUpdate = null,
                    nextUpdate = null,
                    initialized = null,
                    viewer = null,
                    chapterFlags = null,
                    coverLastModified = null,
                    dateAdded = null,
                    updateStrategy = null,
                    calculateInterval = null,
                    version = null,
                    isSyncing = null,
                    novelId = novelId,
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun updateNovel(update: NovelUpdate): Boolean {
        return try {
            handler.await {
                novelsQueries.update(
                    source = update.source,
                    url = update.url,
                    artist = update.artist,
                    author = update.author,
                    description = update.description,
                    genre = update.genre?.let(StringListColumnAdapter::encode),
                    title = update.title,
                    status = update.status,
                    thumbnailUrl = update.thumbnailUrl,
                    favorite = update.favorite,
                    lastUpdate = update.lastUpdate,
                    nextUpdate = update.nextUpdate,
                    initialized = update.initialized,
                    viewer = update.viewerFlags,
                    chapterFlags = update.chapterFlags,
                    coverLastModified = update.coverLastModified,
                    dateAdded = update.dateAdded,
                    updateStrategy = update.updateStrategy?.let(NovelUpdateStrategyColumnAdapter::encode),
                    calculateInterval = update.fetchInterval?.toLong(),
                    version = update.version,
                    isSyncing = null,
                    novelId = update.id,
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun updateAllNovel(updates: List<NovelUpdate>): Boolean {
        return try {
            handler.await(inTransaction = true) {
                updates.forEach { update ->
                    novelsQueries.update(
                        source = update.source,
                        url = update.url,
                        artist = update.artist,
                        author = update.author,
                        description = update.description,
                        genre = update.genre?.let(StringListColumnAdapter::encode),
                        title = update.title,
                        status = update.status,
                        thumbnailUrl = update.thumbnailUrl,
                        favorite = update.favorite,
                        lastUpdate = update.lastUpdate,
                        nextUpdate = update.nextUpdate,
                        initialized = update.initialized,
                        viewer = update.viewerFlags,
                        chapterFlags = update.chapterFlags,
                        coverLastModified = update.coverLastModified,
                        dateAdded = update.dateAdded,
                        updateStrategy = update.updateStrategy?.let(NovelUpdateStrategyColumnAdapter::encode),
                        calculateInterval = update.fetchInterval?.toLong(),
                        version = update.version,
                        isSyncing = null,
                        novelId = update.id,
                    )
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getDuplicateLibraryNovel(id: Long, title: String): List<Novel> {
        return handler.awaitList {
            novelsQueries.getDuplicateLibraryNovel(title, id, NovelMapper::mapNovel)
        }
    }

    override suspend fun setNovelCategories(novelId: Long, categoryIds: List<Long>) {
        handler.await(inTransaction = true) {
            novels_categoriesQueries.deleteNovelCategoryByNovelId(novelId)
            categoryIds.forEach { categoryId ->
                novels_categoriesQueries.insert(novelId, categoryId)
            }
        }
    }

    override suspend fun getUpcomingNovel(statuses: Set<Long>): Flow<List<Novel>> {
        val epochMillis = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond() * 1000
        return handler.subscribeToList {
            novelsQueries.getUpcomingNovel(epochMillis, statuses, NovelMapper::mapNovel)
        }
    }
}
