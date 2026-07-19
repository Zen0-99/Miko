package tachiyomi.data.source.novel

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.source.novel.model.NovelSource as DomainNovelSource
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.domain.source.novel.repository.NovelSourcePagingSourceType
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelSourceRepositoryImpl(
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val handler: NovelDatabaseHandler = Injekt.get(),
) : NovelSourceRepository {

    override fun getNovelSources(): Flow<List<DomainNovelSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources.map {
                mapSourceToDomainSource(it).copy(
                    supportsLatest = it.supportsLatest,
                    supportsComments = it.supportsComments,
                )
            }
        }
    }

    override fun getOnlineNovelSources(): Flow<List<DomainNovelSource>> {
        return sourceManager.catalogueSources.map { sources ->
            sources
                .filterIsInstance<NovelHttpSource>()
                .map(::mapSourceToDomainSource)
        }
    }

    override fun getNovelSourcesWithFavoriteCount(): Flow<List<Pair<DomainNovelSource, Long>>> {
        return combine(
            handler.subscribeToList { novelsQueries.getSourceIdWithFavoriteCount() },
            sourceManager.catalogueSources,
        ) { sourceIdWithFavoriteCount, _ -> sourceIdWithFavoriteCount }
            .map {
                it.map { (sourceId, count) ->
                    val source = sourceManager.getOrStub(sourceId)
                    val domainSource = mapSourceToDomainSource(source).copy(
                        isStub = source is StubNovelSource,
                    )
                    domainSource to count
                }
            }
    }

    override fun searchNovels(
        sourceId: Long,
        query: String,
        filterList: NovelFilterList,
    ): NovelSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as NovelCatalogueSource
        return NovelSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopularNovels(sourceId: Long): NovelSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as NovelCatalogueSource
        return NovelSourcePopularPagingSource(source)
    }

    override fun getLatestNovels(sourceId: Long): NovelSourcePagingSourceType {
        val source = sourceManager.get(sourceId) as NovelCatalogueSource
        return NovelSourceLatestPagingSource(source)
    }

    private fun mapSourceToDomainSource(source: NovelSource): DomainNovelSource = DomainNovelSource(
        id = source.id,
        lang = source.lang,
        name = source.name,
        supportsLatest = false,
        isStub = source is StubNovelSource,
    )
}
