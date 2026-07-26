package tachiyomi.data.collection.anime

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.mi.data.AnimeDatabase

class AnimeCollectionRepositoryImpl(
    private val handler: AnimeDatabaseHandler,
) : AnimeCollectionRepository {

    override suspend fun getAnimeCollection(id: Long): Collection? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, ::mapCollection) }
    }

    override suspend fun getAllAnimeCollections(): List<Collection> {
        return handler.awaitList { categoriesQueries.getCategories(::mapCollection) }
    }

    override suspend fun getAllVisibleAnimeCollections(): List<Collection> {
        return handler.awaitList { categoriesQueries.getVisibleCategories(::mapCollection) }
    }

    override fun getAllAnimeCollectionsAsFlow(): Flow<List<Collection>> {
        return handler.subscribeToList { categoriesQueries.getCategories(::mapCollection) }
    }

    override fun getAllVisibleAnimeCollectionsAsFlow(): Flow<List<Collection>> {
        return handler.subscribeToList { categoriesQueries.getVisibleCategories(::mapCollection) }
    }

    override suspend fun getCollectionsByAnimeId(animeId: Long): List<Collection> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByAnimeId(animeId, ::mapCollection)
        }
    }

    override suspend fun getVisibleCollectionsByAnimeId(animeId: Long): List<Collection> {
        return handler.awaitList {
            categoriesQueries.getVisibleCategoriesByAnimeId(animeId, ::mapCollection)
        }
    }

    override fun getCollectionsByAnimeIdAsFlow(animeId: Long): Flow<List<Collection>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByAnimeId(animeId, ::mapCollection)
        }
    }

    override fun getVisibleCollectionsByAnimeIdAsFlow(animeId: Long): Flow<List<Collection>> {
        return handler.subscribeToList {
            categoriesQueries.getVisibleCategoriesByAnimeId(animeId, ::mapCollection)
        }
    }

    override suspend fun insertAnimeCollection(collection: Collection) {
        handler.await {
            categoriesQueries.insert(
                name = collection.name,
                order = collection.order,
                flags = collection.flags,
            )
        }
    }

    override suspend fun updatePartialAnimeCollection(update: CollectionUpdate) {
        handler.await {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartialAnimeCollections(updates: List<CollectionUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun AnimeDatabase.updatePartialBlocking(update: CollectionUpdate) {
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            hidden = update.hidden?.let { if (it) 1L else 0L },
            categoryId = update.id,
        )
    }

    override suspend fun updateAllAnimeCollectionFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun deleteAnimeCollection(collectionId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = collectionId,
            )
        }
    }

    override suspend fun getAnimeCustomOrder(collectionId: Long): List<Long> {
        return handler.awaitList {
            anime_collection_orderQueries.getPositionsByCollectionId(collectionId) { id, _ -> id }
        }
    }

    override suspend fun setAnimeCustomOrder(collectionId: Long, animeIds: List<Long>) {
        handler.await(inTransaction = true) {
            anime_collection_orderQueries.deletePositionsByCollectionId(collectionId)
            animeIds.forEachIndexed { index, animeId ->
                anime_collection_orderQueries.upsertPosition(collectionId, animeId, index.toLong())
            }
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