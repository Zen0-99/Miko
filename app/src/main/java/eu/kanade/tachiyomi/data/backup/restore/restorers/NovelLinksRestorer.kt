package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupNovelLink
import tachiyomi.domain.entries.novel.interactor.LinkNovels
import tachiyomi.domain.entries.novel.interactor.SetNovelSourcePriority
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
    private val setNovelSourcePriority: SetNovelSourcePriority = Injekt.get(),
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
            val resolvedLinks = mutableListOf<ResolvedLink>()
            var primaryNovelId: Long? = null
            var primarySourceId: Long? = null
            var primaryExtensionType = "apk"

            for (backupLink in clusterLinks) {
                val effectiveSourceId = sourceIdMap[backupLink.sourceId] ?: backupLink.sourceId
                val novel = runCatching {
                    novelRepository.getNovelByUrlAndSourceId(backupLink.novelUrl, effectiveSourceId)
                }.getOrNull() ?: continue

                resolvedLinks.add(
                    ResolvedLink(
                        novelId = novel.id,
                        sourceId = effectiveSourceId,
                        extensionType = backupLink.extensionType,
                        priority = backupLink.priority,
                    ),
                )

                if (backupLink.isPrimary) {
                    primaryNovelId = novel.id
                    primarySourceId = effectiveSourceId
                    primaryExtensionType = backupLink.extensionType
                }
            }

            if (resolvedLinks.size < 2) continue // Need at least 2 to link

            // If no primary was found, use the first
            if (primaryNovelId == null) {
                primaryNovelId = resolvedLinks.first().novelId
                primarySourceId = resolvedLinks.first().sourceId
                primaryExtensionType = resolvedLinks.first().extensionType
            }

            // Link all non-primary novels to the primary
            for (resolved in resolvedLinks) {
                if (resolved.novelId == primaryNovelId) continue
                linkNovels.await(
                    primaryNovelId = primaryNovelId,
                    primarySourceId = primarySourceId!!,
                    primaryExtensionType = primaryExtensionType,
                    linkedNovelId = resolved.novelId,
                    linkedSourceId = resolved.sourceId,
                    linkedExtensionType = resolved.extensionType,
                )
                // Restore quality-based priority
                if (resolved.priority != 0L) {
                    setNovelSourcePriority.await(resolved.novelId, resolved.priority)
                }
            }
        }
    }

    private data class ResolvedLink(
        val novelId: Long,
        val sourceId: Long,
        val extensionType: String,
        val priority: Long,
    )
}
