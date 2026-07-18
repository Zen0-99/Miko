package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.models.BackupNovelSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSourcesBackupCreator(
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
) {

    operator fun invoke(novels: List<BackupNovel>): List<BackupNovelSource> {
        return novels
            .asSequence()
            .map(BackupNovel::source)
            .distinct()
            .map(novelSourceManager::getOrStub)
            .map { it.toBackupSource() }
            .toList()
    }
}

private fun NovelSource.toBackupSource() =
    BackupNovelSource(
        name = this.name,
        sourceId = this.id,
        baseUrl = (this as? NovelHttpSource)?.baseUrl ?: "",
    )
