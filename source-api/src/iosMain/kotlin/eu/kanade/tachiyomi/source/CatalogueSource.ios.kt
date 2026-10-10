package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga

actual interface CatalogueSource : MangaSource {

    actual override val lang: String

    actual val supportsLatest: Boolean

    val supportsRelatedMangas: Boolean
        get() = false

    val disableRelatedMangasBySearch: Boolean
        get() = false

    val disableRelatedMangas: Boolean
        get() = false

    suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> =
        throw UnsupportedOperationException("Unsupported!")

    suspend fun getPopularManga(page: Int): MangasPage

    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage

    suspend fun getLatestUpdates(page: Int): MangasPage

    actual fun getFilterList(): FilterList
}
