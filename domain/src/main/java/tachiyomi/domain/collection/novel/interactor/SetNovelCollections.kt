package tachiyomi.domain.collection.novel.interactor

import tachiyomi.domain.entries.novel.repository.NovelRepository

class SetNovelCollections(
    private val novelRepository: NovelRepository,
) {

    suspend fun await(novelId: Long, collectionIds: List<Long>) {
        novelRepository.setNovelCollections(novelId, collectionIds)
    }
}
