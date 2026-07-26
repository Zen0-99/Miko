package tachiyomi.domain.collection.novel.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate

interface NovelCollectionRepository {

    fun subscribeAll(): Flow<List<Collection>>

    fun subscribeAllVisible(): Flow<List<Collection>>

    suspend fun getAllNovelCollections(): List<Collection>

    suspend fun getNovelCollection(id: Long): Collection?

    suspend fun getCollectionsByNovelId(novelId: Long): List<Collection>

    suspend fun getVisibleCollectionsByNovelId(novelId: Long): List<Collection>

    suspend fun insert(name: String, order: Long, flags: Long): Long?

    suspend fun update(collectionId: Long, name: String?, order: Long?, flags: Long?, hidden: Boolean?)

    suspend fun updatePartialNovelCollection(update: CollectionUpdate)

    suspend fun updateAllNovelCollectionFlags(flags: Long?)

    suspend fun delete(collectionId: Long)

    suspend fun getNovelCustomOrder(collectionId: Long): List<Long>

    suspend fun setNovelCustomOrder(collectionId: Long, novelIds: List<Long>)
}
