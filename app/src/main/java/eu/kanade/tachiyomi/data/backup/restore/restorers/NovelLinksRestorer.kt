package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupNovelLink
import tachiyomi.domain.entries.novel.interactor.LinkNovels
import tachiyomi.domain.entries.novel.repository.NovelLinkRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Restores novel source links from backup.
 *
 * Links are restored after novels have been inserted, so we can look up
 * the new novel IDs by source + URL. The backup stores original novel IDs,
 * but after restore the novel IDs may differ, so we match by (sourceId, url).
 *
 * Source ID remapping from [eu.kanade.tachiyomi.data.backup.restore.SourceIdMapper]
 * is applied to ensure links reference the correct installed source IDs.
 */
class NovelLinksRestorer(
    private val novelRepository: NovelRepository = Injekt.get(),
    private val novelLinkRepository: NovelLinkRepository = Injekt.get(),
    private val linkNovels: LinkNovels = Injekt.get(),
) {

    /**
     * Map of backup source IDs to installed source IDs, set by BackupRestorer.
     */
    var sourceIdMap: Map<Long, Long> = emptyMap()

    suspend fun restore(backupLinks: List<BackupNovelLink>) {
        if (backupLinks.isEmpty()) return

        // Group by linkedId to restore clusters together
        val clusters = backupLinks.groupBy { it.linkedId }

        for ((_, clusterLinks) in clusters) {
            val resolvedLinks = mutableListOf<Triple<Long, Long, String>>() // (novelId, sourceId, extensionType)
            var primaryNovelId: Long? = null
            var primarySourceId: Long? = null
            var primaryExtensionType = "apk"

            for (backupLink in clusterLinks) {
                val effectiveSourceId = sourceIdMap[backupLink.sourceId] ?: backupLink.sourceId
                val novel = runCatching {
                    novelRepository.getNovelByUrlAndSourceId(backupLink.novelUrl, effectiveSourceId)
                }.getOrNull() ?: continue

                resolvedLinks.add(Triple(novel.id, effectiveSourceId, backupLink.extensionType))

                if (backupLink.isPrimary) {
                    primaryNovelId = novel.id
                    primarySourceId = effectiveSourceId
                    primaryExtensionType = backupLink.extensionType
                }
            }

            if (resolvedLinks.size < 2) continue // Need at least 2 to link

            // If no primary was found, use the first
            if (primaryNovelId == null) {
                primaryNovelId = resolvedLinks.first().first
                primarySourceId = resolvedLinks.first().second
                primaryExtensionType = resolvedLinks.first().third
            }

            // Link all non-primary novels to the primary
            for ((novelId, sourceId, extType) in resolvedLinks) {
                if (novelId == primaryNovelId) continue
                linkNovels.await(
                    primaryNovelId = primaryNovelId,
                    primarySourceId = primarySourceId!!,
                    primaryExtensionType = primaryExtensionType,
                    linkedNovelId = novelId,
                    linkedSourceId = sourceId,
                    linkedExtensionType = extType,
                )
            }
        }
    }
}
