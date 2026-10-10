package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.SAnime

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 *
 * KMP: expect interfaces cannot declare members with bodies, so this
 * declares only the members that are abstract on every platform. The
 * rest of the contract — suspend `get*` calls, `lang`, the RxJava
 * `fetch*` methods and their bridge defaults (extensions-lib ABI on
 * Android) — live as extra members on each platform actual.
 */
expect interface AnimeSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    /**
     * Get all the available seasons for an anime
     *
     * @since extensions-lib 16
     * @param anime the anime to fetch seasons for.
     * @return the anime list for the anime.
     */
    suspend fun getSeasonList(anime: SAnime): List<SAnime>
}
