package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCollectionsRestorer(
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val getMangaCollections: GetMangaCollections = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
) {

    suspend operator fun invoke(backupCollections: List<BackupCollection>) {
        if (backupCollections.isNotEmpty()) {
            val dbCollections = getMangaCollections.await()
            val dbCollectionsByName = dbCollections.associateBy { it.name }
            var nextOrder = dbCollections.maxOfOrNull { it.order }?.plus(1) ?: 0

            val collections = backupCollections
                .sortedBy { it.order }
                .map {
                    val dbCollection = dbCollectionsByName[it.name]
                    if (dbCollection != null) return@map dbCollection
                    val order = nextOrder++
                    mangaHandler.awaitOneExecutable {
                        categoriesQueries.insert(it.name, order, it.flags)
                        categoriesQueries.selectLastInsertedRowId()
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
