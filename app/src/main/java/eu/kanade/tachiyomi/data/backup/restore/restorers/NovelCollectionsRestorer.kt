package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCollectionsRestorer(
    private val novelHandler: NovelDatabaseHandler = Injekt.get(),
    private val getNovelCollections: GetNovelCollections = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCollections: List<BackupCollection>) {
        if (backupCollections.isNotEmpty()) {
            val dbCollections = getNovelCollections.await()
            val dbCollectionsByName = dbCollections.associateBy { it.name }
            var nextOrder = dbCollections.maxOfOrNull { it.order }?.plus(1) ?: 0

            val collections = backupCollections
                .sortedBy { it.order }
                .map {
                    val dbCollection = dbCollectionsByName[it.name]
                    if (dbCollection != null) return@map dbCollection
                    val order = nextOrder++
                    novelHandler.awaitOneExecutable {
                        novelcategoriesQueries.insert(it.name, order, it.flags)
                        novelcategoriesQueries.selectLastInsertedRowId()
                    }
                        .let { id -> it.toCollection(id).copy(order = order) }
                }

            libraryPreferences.perCollectionDisplaySettings().set(
                (dbCollections + collections)
                    .distinctBy { it.flags }
                    .size > 1,
            )
        }
    }
}
