package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage

actual interface AnimeCatalogueSource : AnimeSource {

    actual override val lang: String

    actual val supportsLatest: Boolean

    suspend fun getPopularAnime(page: Int): AnimesPage

    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage

    suspend fun getLatestUpdates(page: Int): AnimesPage

    actual fun getFilterList(): AnimeFilterList
}
