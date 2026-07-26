package tachiyomi.domain.collection.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate

interface AnimeCollectionRepository {

    suspend fun getAnimeCollection(id: Long): Collection?

    suspend fun getAllAnimeCollections(): List<Collection>

    suspend fun getAllVisibleAnimeCollections(): List<Collection>

    fun getAllAnimeCollectionsAsFlow(): Flow<List<Collection>>

    fun getAllVisibleAnimeCollectionsAsFlow(): Flow<List<Collection>>

    suspend fun getCollectionsByAnimeId(animeId: Long): List<Collection>

    suspend fun getVisibleCollectionsByAnimeId(animeId: Long): List<Collection>

    fun getCollectionsByAnimeIdAsFlow(animeId: Long): Flow<List<Collection>>

    fun getVisibleCollectionsByAnimeIdAsFlow(animeId: Long): Flow<List<Collection>>

    suspend fun insertAnimeCollection(collection: Collection)

    suspend fun updatePartialAnimeCollection(update: CollectionUpdate)

    suspend fun updatePartialAnimeCollections(updates: List<CollectionUpdate>)

    suspend fun updateAllAnimeCollectionFlags(flags: Long?)

    suspend fun deleteAnimeCollection(collectionId: Long)

    suspend fun getAnimeCustomOrder(collectionId: Long): List<Long>

    suspend fun setAnimeCustomOrder(collectionId: Long, animeIds: List<Long>)
}
