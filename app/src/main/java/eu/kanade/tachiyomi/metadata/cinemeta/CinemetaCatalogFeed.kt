package eu.kanade.tachiyomi.metadata.cinemeta

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.mikodb.MikoDbManager
import eu.kanade.tachiyomi.metadata.MetadataSourceManager
import eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogResult
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import tachiyomi.domain.entries.shows.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.shows.model.Anime

/**
 * Shared Cinemeta catalog use-case (Phase 18) — fetch + canonical
 * dedupe + the meta→entry materialization that `CinemetaBrowseScreenModel`,
 * the discovery surface, and user-built home catalog rows all funnel
 * through.
 *
 * `fetchCatalog`/`searchCatalog` return the raw [MikoCatalogResult] —
 * dedupe is a separate step (`dedupeMetas`) because the browse surface's
 * paging counts raw results (`skip` advances on page size) while rows
 * only want the collapsed list.
 */
class CinemetaCatalogFeed(
    metadataSourceManager: MetadataSourceManager,
    private val mikoDbManager: MikoDbManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
) {
    private val source = metadataSourceManager.cinemeta

    suspend fun fetchCatalog(type: String, catalogId: String, skip: Int = 0): MikoCatalogResult =
        source.getCatalog(type, catalogId, skip)

    suspend fun searchCatalog(type: String, query: String, skip: Int = 0): MikoCatalogResult =
        source.search(type, query, skip)

    /**
     * Collapse catalog/search results that resolve to the same canonical
     * anime. The Stremio catalog can list one show under several IMDb ids
     * (e.g. specials/OVAs that MikoDB folds into the parent series); clicking
     * any of them lands on the same canonical entry, so the duplicates only
     * clutter the grid. Keyed by the MikoDB main-entry identity (MAL id),
     * falling back to the meta id itself when the bridge is unavailable —
     * entries that don't map to a canonical row are never dropped.
     */
    fun dedupeMetas(metas: List<MikoMetaShort>): List<MikoMetaShort> {
        val bridge = try {
            mikoDbManager.bridge()
        } catch (e: Exception) {
            null
        } ?: return metas
        val seen = mutableSetOf<String>()
        return metas.filter { meta ->
            val key = try {
                bridge.mainEntry(meta.id)?.malId?.let { "mal:$it" } ?: "imdb:${meta.id}"
            } catch (e: Exception) {
                "imdb:${meta.id}"
            }
            seen.add(key)
        }
    }

    /**
     * Converts a Cinemeta catalog item into a domain [Anime] record,
     * inserts it into the database (or returns the existing record), and
     * returns the database ID so the caller can navigate to ShowsScreen.
     */
    suspend fun getOrCreateAnimeId(meta: MikoMetaShort): Long {
        val isOngoing = meta.releaseInfo?.endsWith("-") == true
        val artistValue = listOfNotNull(
            meta.imdbRating?.let { "★ $it" },
            meta.releaseInfo,
        ).joinToString("||")
        val anime = Anime.create().copy(
            url = "cinemeta:${meta.type}:${meta.id}",
            title = meta.name,
            description = meta.description,
            genre = meta.genres,
            country = meta.country,
            thumbnailUrl = meta.poster,
            backgroundUrl = meta.background,
            source = CINEMETA_SOURCE_ID,
            initialized = true,
            author = null,
            artist = artistValue.ifEmpty { null },
            status = if (isOngoing) SAnime.ONGOING.toLong() else SAnime.COMPLETED.toLong(),
        )
        return networkToLocalAnime.await(anime).id
    }

    companion object {
        const val CINEMETA_SOURCE_ID = 0L
    }
}
