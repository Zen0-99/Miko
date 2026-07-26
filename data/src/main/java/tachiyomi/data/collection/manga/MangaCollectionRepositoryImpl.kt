package tachiyomi.data.collection.manga

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate

class MangaCollectionRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : MangaCollectionRepository {

    override suspend fun getMangaCollection(id: Long): Collection? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, ::mapCollection) }
    }

    override suspend fun getAllMangaCollections(): List<Collection> {
        return handler.awaitList { categoriesQueries.getCategories(::mapCollection) }
    }

    override suspend fun getAllVisibleMangaCollections(): List<Collection> {
        return handler.awaitList { categoriesQueries.getVisibleCategories(::mapCollection) }
    }

    override fun getAllMangaCollectionsAsFlow(): Flow<List<Collection>> {
        return handler.subscribeToList { categoriesQueries.getCategories(::mapCollection) }
    }

    override fun getAllVisibleMangaCollectionsAsFlow(): Flow<List<Collection>> {
        return handler.subscribeToList { categoriesQueries.getVisibleCategories(::mapCollection) }
    }

    override suspend fun getCollectionsByMangaId(mangaId: Long): List<Collection> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByMangaId(mangaId, ::mapCollection)
        }
    }

    override suspend fun getVisibleCollectionsByMangaId(mangaId: Long): List<Collection> {
        return handler.awaitList {
            categoriesQueries.getVisibleCategoriesByMangaId(mangaId, ::mapCollection)
        }
    }

    override fun getCollectionsByMangaIdAsFlow(mangaId: Long): Flow<List<Collection>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByMangaId(mangaId, ::mapCollection)
        }
    }

    override fun getVisibleCollectionsByMangaIdAsFlow(mangaId: Long): Flow<List<Collection>> {
        return handler.subscribeToList {
            categoriesQueries.getVisibleCategoriesByMangaId(mangaId, ::mapCollection)
        }
    }

    override suspend fun insertMangaCollection(collection: Collection) {
        handler.await {
            categoriesQueries.insert(
                name = collection.name,
                order = collection.order,
                flags = collection.flags,
            )
        }
    }

    override suspend fun updatePartialMangaCollection(update: CollectionUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialMangaCollections(updates: List<CollectionUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: CollectionUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllMangaCollectionFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteMangaCollection(collectionId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = collectionId,
            )
        }
    }

    override suspend fun getMangaCustomOrder(collectionId: Long): List<Long> {
        return handler.awaitList {
            manga_collection_orderQueries.getPositionsByCollectionId(collectionId) { id, _ -> id }
        }
    }

    override suspend fun setMangaCustomOrder(collectionId: Long, mangaIds: List<Long>) {
        handler.await(inTransaction = true) {
            manga_collection_orderQueries.deletePositionsByCollectionId(collectionId)
            mangaIds.forEachIndexed { index, mangaId ->
                manga_collection_orderQueries.upsertPosition(collectionId, mangaId, index.toLong())
            }
        }
    }

    override suspend fun getMangaIdsByCollection(collectionId: Long): List<Long> {
        return handler.awaitList {
            mangas_categoriesQueries.getMangaIdsByCategoryId(collectionId)
        }
    }

    override suspend fun addMangaToCollection(mangaId: Long, collectionId: Long) {
        handler.await {
            mangas_categoriesQueries.insertMangaCategory(mangaId, collectionId)
        }
    }

    override suspend fun removeMangaFromCollection(mangaId: Long, collectionId: Long) {
        handler.await {
            mangas_categoriesQueries.deleteMangaCategoryByMangaIdAndCategoryId(mangaId, collectionId)
        }
    }

    private fun mapCollection(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Collection {
        return Collection(
            id = id,
            name = name,
            order = order,
            flags = flags,
            hidden = hidden == 1L,
        )
    }
}