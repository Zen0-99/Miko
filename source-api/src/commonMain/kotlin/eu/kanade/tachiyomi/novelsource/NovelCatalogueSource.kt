package eu.kanade.tachiyomi.novelsource

import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel

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
