package tachiyomi.domain.collection.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate

interface MangaCollectionRepository {

    suspend fun getMangaCollection(id: Long): Collection?

    suspend fun getAllMangaCollections(): List<Collection>

    suspend fun getAllVisibleMangaCollections(): List<Collection>

    fun getAllMangaCollectionsAsFlow(): Flow<List<Collection>>

    fun getAllVisibleMangaCollectionsAsFlow(): Flow<List<Collection>>

    suspend fun getCollectionsByMangaId(mangaId: Long): List<Collection>

    suspend fun getVisibleCollectionsByMangaId(mangaId: Long): List<Collection>

    fun getCollectionsByMangaIdAsFlow(mangaId: Long): Flow<List<Collection>>

    fun getVisibleCollectionsByMangaIdAsFlow(mangaId: Long): Flow<List<Collection>>

    suspend fun insertMangaCollection(collection: Collection)

    suspend fun updatePartialMangaCollection(update: CollectionUpdate)

    suspend fun updatePartialMangaCollections(updates: List<CollectionUpdate>)

    suspend fun updateAllMangaCollectionFlags(flags: Long?)

    suspend fun deleteMangaCollection(collectionId: Long)

    suspend fun getMangaCustomOrder(collectionId: Long): List<Long>

    suspend fun setMangaCustomOrder(collectionId: Long, mangaIds: List<Long>)
}
