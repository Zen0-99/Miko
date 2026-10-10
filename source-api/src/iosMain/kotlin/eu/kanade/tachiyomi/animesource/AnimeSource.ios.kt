package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

/**
 * iOS contract — suspend-first, no RxJava. Members beyond the expect
 * contract are extra declarations; the JS-source adapter implements them.
 */
actual interface AnimeSource {

    actual val id: Long

    actual val name: String

    actual suspend fun getSeasonList(anime: SAnime): List<SAnime>

    val lang: String
        get() = ""

    suspend fun getAnimeDetails(anime: SAnime): SAnime

    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>

    suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw IllegalStateException("Not used")

    suspend fun getVideoList(hoster: Hoster): List<Video> = throw IllegalStateException("Not used")

    suspend fun getVideoList(episode: SEpisode): List<Video>
}
