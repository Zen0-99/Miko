package eu.kanade.tachiyomi.metadata.stream

import eu.kanade.tachiyomi.data.mikodb.MikoDbManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.shows.model.asAnimeCover
import tachiyomi.domain.entries.shows.repository.AnimeRepository
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * Unifies extension-source anime entries with their canonical Cinemeta
 * entry. Extensions materialize their own anime rows when browsing or
 * searching — so "Naruto" can exist once per extension. When the user
 * opens one of those duplicates and MikoDB can map it to an identity that
 * already has a Cinemeta entry, the detail screen redirects to the
 * Cinemeta entry instead: one entry per real anime.
 *
 * Identity resolution order:
 * 1. Numeric id embedded in the source URL, matched through the source's
 *    MikoDB `idType` (tier-A sources key entries on AniList/MAL/Kitsu).
 * 2. Title match against MikoDB's canonical titles.
 */
class CinemetaEntryUnifier(
    private val mikoDbManager: MikoDbManager,
    private val animeExtensionManager: AnimeExtensionManager,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
) {

    // animeId -> canonical Cinemeta anime, or NoCanonical for "checked, no
    // mapping". ConcurrentHashMap can't hold nulls, so the sentinel is
    // required — getOrPut with a null result crashes with NPE.
    private val canonicalCache = java.util.concurrent.ConcurrentHashMap<Long, Any>()

    // canonicalAnimeId -> non-special episodes in air order (for label lookup)
    private val canonicalEpisodesCache = ConcurrentHashMap<Long, List<Episode>>()

    /**
     * Returns the canonical Cinemeta [Anime] for [anime], or null if the
     * entry is already Cinemeta/local or no canonical entry exists.
     */
    suspend fun resolve(anime: Anime): Anime? {
        if (anime.source == 0L || anime.url.startsWith("cinemeta:")) return null
        val bridge = mikoDbManager.bridge() ?: return null

        val pkg = try {
            animeExtensionManager.getExtensionPackage(anime.source)
        } catch (e: Exception) {
            null
        }
        val idType = pkg?.let { mikoDbManager.sourceMeta(it)?.idType }
        val urlId = URL_ID_REGEX.findAll(anime.url).lastOrNull()?.value?.toLongOrNull()

        val mapping = when (idType) {
            "anilist" -> urlId?.let { bridge.byAnilist(it) }
            "mal" -> urlId?.let { bridge.byMal(it) }
            "kitsu" -> urlId?.let { bridge.byKitsu(it) }
            else -> null
        } ?: bridge.byTitle(anime.title)

        val imdbId = mapping?.imdbId ?: return null
        val type = if (mapping.animeMediaType == "MOVIE") "movie" else "series"

        return animeRepository.getAnimeByUrlAndSourceId("cinemeta:$type:$imdbId", 0L)
            ?: animeRepository.getAnimeByUrlAndSourceId("cinemeta:series:$imdbId", 0L)
            ?: animeRepository.getAnimeByUrlAndSourceId("cinemeta:movie:$imdbId", 0L)
    }

    /**
     * Substitute a history item's display fields with its canonical Cinemeta
     * entry's — extension-source history rows (e.g. an AniKage "Naruto") would
     * otherwise show the extension's static cover and lose the season label.
     * The row's ids stay untouched so delete/navigation semantics are
     * preserved; only title, cover, url, runtime and the episode's season
     * scanlator are replaced.
     */
    suspend fun unify(item: AnimeHistoryWithRelations): AnimeHistoryWithRelations {
        if (item.coverData.sourceId == 0L || item.animeUrl.startsWith("cinemeta:")) {
            return item
        }

        val canonical = if (canonicalCache.containsKey(item.animeId)) {
            canonicalCache[item.animeId] as? Anime
        } else {
            val resolved = try {
                resolve(animeRepository.getAnimeById(item.animeId))
            } catch (e: Exception) {
                null
            }
            canonicalCache[item.animeId] = resolved ?: NoCanonical
            resolved
        } ?: return item

        // Map the extension's absolute episode number onto the canonical
        // entry's same-position episode to recover the "Season N" scanlator.
        val episodeScanlator = canonicalEpisodesCache.getOrPut(canonical.id) {
            try {
                episodeRepository.getEpisodeByAnimeId(canonical.id)
                    .filter { it.scanlator?.startsWith("Season 0") != true }
                    .sortedBy { it.sourceOrder }
            } catch (e: Exception) {
                emptyList()
            }
        }.getOrNull(item.episodeNumber.toInt() - 1)?.scanlator

        return item.copy(
            title = canonical.title,
            animeUrl = canonical.url,
            runtime = canonical.runtime,
            episodeScanlator = episodeScanlator,
            coverData = canonical.asAnimeCover(),
            canonicalAnimeId = canonical.id,
        )
    }

    private object NoCanonical

    private companion object {
        private val URL_ID_REGEX = Regex("\\d+")
    }
}
