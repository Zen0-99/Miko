package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.model.NovelLink
import tachiyomi.domain.entries.novel.repository.NovelLinkRepository
import tachiyomi.domain.items.chapter.model.NovelChapter

/**
 * Interactors for managing linked novel sources.
 *
 * Linked sources allow novels from different sources (e.g. APK extension
 * and JS plugin for the same site) to be clustered together. Chapters from
 * all linked sources are merged with deduplication by chapter number.
 */
class GetLinkedNovels(
    private val linkRepository: NovelLinkRepository,
) {
    suspend fun await(linkedId: Long): List<NovelLink> {
        return linkRepository.getLinkedNovelsByLinkedId(linkedId)
    }
}

class GetNovelLinks(
    private val linkRepository: NovelLinkRepository,
) {
    suspend fun await(novelId: Long): List<NovelLink> {
        return linkRepository.getLinksByNovelId(novelId)
    }
}

class GetNovelLinkedId(
    private val linkRepository: NovelLinkRepository,
) {
    suspend fun await(novelId: Long): Long? {
        return linkRepository.getLinkedIdByNovelId(novelId)
    }
}

class LinkNovels(
    private val linkRepository: NovelLinkRepository,
) {
    /**
     * Link two novels from different sources. The first novel becomes the primary.
     * If either novel is already linked, they are merged into the same cluster.
     */
    suspend fun await(
        primaryNovelId: Long,
        primarySourceId: Long,
        primaryExtensionType: String,
        linkedNovelId: Long,
        linkedSourceId: Long,
        linkedExtensionType: String,
    ): Long {
        val existingPrimaryLink = linkRepository.getLinkedIdByNovelId(primaryNovelId)
        val existingLinkedLink = linkRepository.getLinkedIdByNovelId(linkedNovelId)

        val linkedId = when {
            existingPrimaryLink != null && existingLinkedLink != null -> {
                // Both already linked — merge clusters (move all from linked cluster to primary cluster)
                if (existingPrimaryLink != existingLinkedLink) {
                    val linksToMove = linkRepository.getLinkedNovelsByLinkedId(existingLinkedLink)
                    linksToMove.forEach { link ->
                        linkRepository.deleteLink(link.novelId)
                        linkRepository.insertLink(
                            linkedId = existingPrimaryLink,
                            novelId = link.novelId,
                            sourceId = link.sourceId,
                            isPrimary = link.isPrimary && link.novelId == primaryNovelId,
                            extensionType = link.extensionType,
                        )
                    }
                }
                existingPrimaryLink
            }
            existingPrimaryLink != null -> {
                // Primary already linked — add the new novel to the same cluster
                linkRepository.insertLink(
                    linkedId = existingPrimaryLink,
                    novelId = linkedNovelId,
                    sourceId = linkedSourceId,
                    isPrimary = false,
                    extensionType = linkedExtensionType,
                )
                existingPrimaryLink
            }
            existingLinkedLink != null -> {
                // Linked novel already in a cluster — add primary to that cluster
                linkRepository.insertLink(
                    linkedId = existingLinkedLink,
                    novelId = primaryNovelId,
                    sourceId = primarySourceId,
                    isPrimary = true,
                    extensionType = primaryExtensionType,
                )
                // Demote any existing primary in that cluster
                linkRepository.setPrimary(linkedNovelId, false)
                existingLinkedLink
            }
            else -> {
                // Neither linked — create a new cluster
                val newLinkedId = linkRepository.getNextLinkedId()
                linkRepository.insertLink(
                    linkedId = newLinkedId,
                    novelId = primaryNovelId,
                    sourceId = primarySourceId,
                    isPrimary = true,
                    extensionType = primaryExtensionType,
                )
                linkRepository.insertLink(
                    linkedId = newLinkedId,
                    novelId = linkedNovelId,
                    sourceId = linkedSourceId,
                    isPrimary = false,
                    extensionType = linkedExtensionType,
                )
                newLinkedId
            }
        }

        return linkedId
    }
}

class UnlinkNovel(
    private val linkRepository: NovelLinkRepository,
) {
    suspend fun await(novelId: Long) {
        linkRepository.deleteLink(novelId)
    }
}

class GetAllNovelLinks(
    private val linkRepository: NovelLinkRepository,
) {
    suspend fun await(): List<NovelLink> {
        return linkRepository.getAllLinks()
    }
}

class SetNovelSourcePriority(
    private val linkRepository: NovelLinkRepository,
) {
    /**
     * Set the quality-based priority for a linked source within its cluster.
     * Lower values = higher priority. When chapters from multiple linked
     * sources have the same chapter number, the source with the lowest
     * priority value wins during dedup.
     */
    suspend fun await(novelId: Long, priority: Long) {
        linkRepository.setPriority(novelId, priority)
    }
}

/**
 * Merges chapters from the primary source and all linked sources, deduplicating
 * by chapter number.
 *
 * The primary source always wins for a given chapter number. Among linked
 * sources, the one with the lowest [NovelLink.priority] wins; ties are broken
 * by the cluster ordering from the repository (stable).
 *
 * Chapters with unrecognized chapter numbers (negative) are kept without
 * dedup — they are appended after the recognized winners.
 *
 * @param primaryChapters chapters from the primary novel's source
 * @param linkedChapters pairs of (link, chapters) for each linked source,
 *   ordered by priority ascending
 * @return the deduplicated, merged chapter list
 */
class MergeLinkedNovelChapters {

    fun await(
        primaryChapters: List<NovelChapter>,
        linkedChapters: List<Pair<NovelLink, List<NovelChapter>>>,
    ): List<NovelChapter> {
        val winners = LinkedHashMap<Double, NovelChapter>()
        val unrecognized = mutableListOf<NovelChapter>()

        fun ingest(chapters: List<NovelChapter>, isPrimary: Boolean) {
            for (chapter in chapters) {
                if (chapter.isRecognizedNumber) {
                    val existing = winners[chapter.chapterNumber]
                    // Primary always wins; otherwise first writer wins (linked
                    // sources are pre-sorted by priority).
                    if (existing == null || (isPrimary && existing.novelId != chapter.novelId)) {
                        winners[chapter.chapterNumber] = chapter
                    }
                } else {
                    unrecognized.add(chapter)
                }
            }
        }

        ingest(primaryChapters, isPrimary = true)
        for ((_, chapters) in linkedChapters) {
            ingest(chapters, isPrimary = false)
        }

        // Resequence sourceOrder by chapter number descending so the existing
        // "source order" sort produces the right merged order across sources.
        val sortedWinners = winners.values.sortedByDescending { it.chapterNumber }
        val resequenced = sortedWinners.mapIndexed { i, c ->
            c.copy(sourceOrder = i.toLong())
        }
        val unrecognizedOffset = resequenced.size.toLong()
        val resequencedUnrecognized = unrecognized.mapIndexed { i, c ->
            c.copy(sourceOrder = unrecognizedOffset + i)
        }

        return resequenced + resequencedUnrecognized
    }
}
