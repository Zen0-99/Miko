package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupNovelLink
import tachiyomi.domain.entries.novel.interactor.GetAllNovelLinks
import tachiyomi.domain.entries.novel.interactor.GetNovel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelLinksBackupCreator(
    private val getAllNovelLinks: GetAllNovelLinks = Injekt.get(),
    private val getNovel: GetNovel = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupNovelLink> {
        val links = getAllNovelLinks.await()
        return links.map { link ->
            val novel = runCatching { getNovel.await(link.novelId) }.getOrNull()
            BackupNovelLink(
                linkedId = link.linkedId,
                novelId = link.novelId,
                sourceId = link.sourceId,
                isPrimary = link.isPrimary,
                extensionType = link.extensionType,
                novelUrl = novel?.url ?: "",
                novelTitle = novel?.title ?: "",
                priority = link.priority,
            )
        }
    }
}
