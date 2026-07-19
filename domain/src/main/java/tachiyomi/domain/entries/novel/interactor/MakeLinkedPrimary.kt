package tachiyomi.domain.entries.novel.interactor

import tachiyomi.domain.entries.novel.repository.NovelLinkRepository

class MakeLinkedPrimary(
    private val linkRepository: NovelLinkRepository,
) {
    suspend fun await(oldPrimaryNovelId: Long, newPrimaryNovelId: Long) {
        if (oldPrimaryNovelId == newPrimaryNovelId) return
        linkRepository.setPrimary(oldPrimaryNovelId, false)
        linkRepository.setPrimary(newPrimaryNovelId, true)
    }
}
