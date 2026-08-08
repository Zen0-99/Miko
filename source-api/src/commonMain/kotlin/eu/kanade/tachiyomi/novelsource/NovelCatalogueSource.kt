package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface NovelCatalogueSource : NovelSource {

    val supportsRelatedNovels: Boolean get() = false

    suspend fun getRelatedNovels(novel: SNovel): List<SNovel> {
        return emptyList()
    }

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    override val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Whether the source supports incremental search results.
     *
     * When true, the search UI will use [searchFlow] instead of [getSearchNovels],
     * allowing results to appear incrementally as they're parsed from the response.
     * Sources that override this should also override [searchFlow].
     */
    val supportsIncrementalSearch: Boolean get() = false

    /**
     * Get a page with a list of novels.
     *
     * @param page the page number to retrieve.
     */
    suspend fun getPopularNovels(page: Int): NovelsPage

    /**
     * Get a page with a list of novels while applying source filters.
     *
     * By default sources that don't support filtered popular/latest can ignore filters.
     */
    suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage {
        return getPopularNovels(page)
    }

    /**
     * Get a page with a list of novels.
     *
     * @param page the page number to retrieve.
     * @param query the search query.
     * @param filters the list of filters to apply.
     */
    suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage

    /**
     * Incremental search: emits partial result lists as they're parsed.
     *
     * Each emission contains the **cumulative** list of results found so far
     * for this page. The UI replaces its list with each emission, so results
     * appear incrementally without duplicates.
     *
     * Default implementation calls [getSearchNovels] and emits the full list
     * at once. Sources that want incremental display should override this to
     * emit results in batches as they're extracted from the response.
     *
     * Only used when [supportsIncrementalSearch] is true.
     *
     * @param query the search query.
     * @param page the page number to retrieve.
     * @return Flow emitting cumulative lists of search results.
     */
    fun searchFlow(query: String, page: Int): Flow<List<SNovel>> {
        return flow {
            val result = getSearchNovels(page, query, NovelFilterList())
            emit(result.novels)
        }
    }

    /**
     * Get a page with a list of latest novel updates.
     *
     * @param page the page number to retrieve.
     */
    suspend fun getLatestUpdates(page: Int): NovelsPage

    /**
     * Get a page with a list of latest novel updates while applying source filters.
     *
     * By default sources that don't support filtered popular/latest can ignore filters.
     */
    suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage {
        return getLatestUpdates(page)
    }

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): NovelFilterList
}
