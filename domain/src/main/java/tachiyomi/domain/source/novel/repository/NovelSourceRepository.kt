package tachiyomi.domain.source.novel.repository

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.novel.model.NovelSource

typealias NovelSourcePagingSourceType = PagingSource<Long, SNovel>

interface NovelSourceRepository {

    fun getNovelSources(): Flow<List<NovelSource>>

    fun getOnlineNovelSources(): Flow<List<NovelSource>>

    fun getNovelSourcesWithFavoriteCount(): Flow<List<Pair<NovelSource, Long>>>

    fun searchNovels(sourceId: Long, query: String, filterList: NovelFilterList): NovelSourcePagingSourceType

    fun getPopularNovels(sourceId: Long): NovelSourcePagingSourceType

    fun getLatestNovels(sourceId: Long): NovelSourcePagingSourceType

    /**
     * Incremental search: returns a Flow that emits cumulative result lists
     * as they're parsed by the source. Only sources with
     * [NovelCatalogueSource.supportsIncrementalSearch] = true use this path.
     */
    fun searchNovelsFlow(sourceId: Long, query: String): Flow<List<SNovel>>
}
