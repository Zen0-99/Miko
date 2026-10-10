package mihon.domain.extensionrepo.novel.interactor

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository

class GetNovelExtensionRepoCount(
    private val repository: NovelExtensionRepoRepository,
) {
    fun subscribe(): Flow<Int> = repository.getCount()
}
