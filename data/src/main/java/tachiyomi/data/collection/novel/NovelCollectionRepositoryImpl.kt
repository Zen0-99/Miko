package tachiyomi.data.collection.novel

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.collection.model.CollectionUpdate
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository

class NovelCollectionRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelCollectionRepository {

    override fun subscribeAll(): Flow<List<Collection>> {
        return handler.subscribeToList {
            novelcategoriesQueries.getCategories(::mapCollection)
        }
    }

    override fun subscribeAllVisible(): Flow<List<Collection>> {
        return handler.subscribeToList {
            novelcategoriesQueries.getVisibleCategories(::mapCollection)
        }
    }

    override suspend fun getAllNovelCollections(): List<Collection> {
        return handler.awaitList { novelcategoriesQueries.getCategories(::mapCollection) }
    }

    override suspend fun getNovelCollection(id: Long): Collection? {
        return handler.awaitOneOrNull { novelcategoriesQueries.getCategory(id, ::mapCollection) }
    }

    override suspend fun getCollectionsByNovelId(novelId: Long): List<Collection> {
        return handler.awaitList {
            novelcategoriesQueries.getCategoriesByNovelId(novelId, ::mapCollection)
        }
    }

    override suspend fun getVisibleCollectionsByNovelId(novelId: Long): List<Collection> {
        return handler.awaitList {
            novelcategoriesQueries.getVisibleCategoriesByNovelId(novelId, ::mapCollection)
        }
    }

    override suspend fun insert(name: String, order: Long, flags: Long): Long? {
        return handler.await(inTransaction = true) {
            novelcategoriesQueries.insert(name, order, flags)
            novelcategoriesQueries.selectLastInsertedRowId().executeAsOneOrNull()
        }
    }

    override suspend fun update(
        collectionId: Long,
        name: String?,
        order: Long?,
        flags: Long?,
        hidden: Boolean?,
    ) {
        handler.await {
            novelcategoriesQueries.update(
                name = name,
                `order` = order,
                flags = flags,
                hidden = hidden?.let { if (it) 1L else 0L },
                categoryId = collectionId,
            )
        }
    }

    override suspend fun delete(collectionId: Long) {
        handler.await {
            novelcategoriesQueries.delete(collectionId)
        }
    }

    override suspend fun updatePartialNovelCollection(update: CollectionUpdate) {
        handler.await {
            novelcategoriesQueries.update(
                name = update.name,
                `order` = update.order,
                flags = update.flags,
                hidden = update.hidden?.let { if (it) 1L else 0L },
                categoryId = update.id,
            )
        }
    }

    override suspend fun updateAllNovelCollectionFlags(flags: Long?) {
        handler.await {
            novelcategoriesQueries.updateAllFlags(flags)
        }
    }

    private fun mapCollection(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        hidden: Long,
    ): Collection = Collection(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden == 1L,
    )
}