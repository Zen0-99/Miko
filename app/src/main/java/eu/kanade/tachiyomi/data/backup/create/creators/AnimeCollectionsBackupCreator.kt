package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCollection
import eu.kanade.tachiyomi.data.backup.models.backupCollectionMapper
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.model.Collection
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeCollectionsBackupCreator(
    private val getAnimeCollections: GetAnimeCollections = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCollection> {
        return getAnimeCollections.await()
            .filterNot(Collection::isSystemCollection)
            .map(backupCollectionMapper)
    }
}
