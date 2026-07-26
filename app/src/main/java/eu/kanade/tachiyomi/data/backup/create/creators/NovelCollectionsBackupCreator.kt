package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.backupCollectionMapper
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.model.Collection
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelCollectionsBackupCreator(
    private val getNovelCollections: GetNovelCollections = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCollection> {
        return getNovelCollections.await()
            .filterNot(Collection::isSystemCollection)
            .map(backupCollectionMapper)
    }
}
