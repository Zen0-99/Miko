package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.backupCollectionMapper
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.model.Collection
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaCollectionsBackupCreator(
    private val getMangaCollections: GetMangaCollections = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCollection> {
        return getMangaCollections.await()
            .filterNot(Collection::isSystemCollection)
            .map(backupCollectionMapper)
    }
}
