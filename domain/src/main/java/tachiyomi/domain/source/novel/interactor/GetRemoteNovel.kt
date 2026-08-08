package tachiyomi.domain.source.novel.interactor

import android.util.Log
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.novel.repository.NovelSourcePagingSourceType
import tachiyomi.domain.source.novel.repository.NovelSourceRepository

class GetRemoteNovel(
    private val repository: NovelSourceRepository,
) {

    fun subscribe(sourceId: Long, query: String, filterList: NovelFilterList): NovelSourcePagingSourceType {
        Log.d("NovelSearch", "[GetRemoteNovel] subscribe - sourceId=$sourceId, query='$query', filters=${filterList.size}")
        return when (query) {
            QUERY_POPULAR -> {
                Log.d("NovelSearch", "[GetRemoteNovel] subscribe - fetching POPULAR novels for sourceId=$sourceId")
                repository.getPopularNovels(sourceId)
            }
            QUERY_LATEST -> {
                Log.d("NovelSearch", "[GetRemoteNovel] subscribe - fetching LATEST novels for sourceId=$sourceId")
                repository.getLatestNovels(sourceId)
            }
            else -> {
                Log.d("NovelSearch", "[GetRemoteNovel] subscribe - SEARCHING novels for sourceId=$sourceId, query='$query'")
                repository.searchNovels(sourceId, query, filterList)
            }
        }
    }

    /**
     * Incremental search: returns a Flow that emits cumulative result lists.
     * Used by sources that set [NovelCatalogueSource.supportsIncrementalSearch].
     */
    fun subscribeFlow(sourceId: Long, query: String): Flow<List<SNovel>> {
        Log.d("NovelSearch", "[GetRemoteNovel] subscribeFlow - sourceId=$sourceId, query='$query'")
        return repository.searchNovelsFlow(sourceId, query)
    }

    fun popular(sourceId: Long): NovelSourcePagingSourceType {
        return repository.getPopularNovels(sourceId)
    }

    fun latest(sourceId: Long): NovelSourcePagingSourceType {
        return repository.getLatestNovels(sourceId)
    }

    companion object {
        const val QUERY_POPULAR = "eu.kanade.domain.source.novel.interactor.POPULAR"
        const val QUERY_LATEST = "eu.kanade.domain.source.novel.interactor.LATEST"
    }
}
