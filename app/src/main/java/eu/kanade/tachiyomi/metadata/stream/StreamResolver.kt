package eu.kanade.tachiyomi.metadata.stream

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Resolves streaming sources for a Cinemeta anime entry by searching all
 * installed anime extensions via [getSearchAnime] and ranking results with
 * [TitleMatcher].
 *
 * If a cached mapping exists in [SourceMappingCache], it is returned directly
 * without searching — the user already picked a source for this entry.
 */
class StreamResolver(
    private val sourceManager: AnimeSourceManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val sourceMappingCache: SourceMappingCache,
    private val titleMatcher: TitleMatcher,
) {
    data class StreamCandidate(
        val source: AnimeCatalogueSource,
        val anime: Anime,
        val matchScore: Double,
        val cached: Boolean = false,
    )

    companion object {
        private const val PER_SOURCE_TIMEOUT_MS = 10_000L
        private const val TOTAL_TIMEOUT_MS = 15_000L
        private const val MIN_MATCH_SCORE = 0.5
    }

    /**
     * Search all installed catalogue sources for [cinemetaAnime].
     * Returns candidates sorted by match score descending.
     *
     * If a cached mapping exists, returns a single-element list with
     * [cached] = true — no search is performed.
     */
    suspend fun resolve(cinemetaAnime: Anime): List<StreamCandidate> = withIOContext {
        // Check cache first
        val cached = sourceMappingCache.get(cinemetaAnime.url)
        if (cached != null) {
            val source = sourceManager.get(cached.sourceId) as? AnimeCatalogueSource
            if (source != null) {
                val cachedAnime = networkToLocalAnime.await(
                    Anime.create().copy(
                        url = cached.sourceAnimeUrl,
                        title = cinemetaAnime.title,
                        source = cached.sourceId,
                    ),
                )
                return@withIOContext listOf(
                    StreamCandidate(source, cachedAnime, 1.0, cached = true),
                )
            }
            // Cached source no longer installed — clear and search
            sourceMappingCache.remove(cinemetaAnime.url)
        }

        val sources = sourceManager.getCatalogueSources()
        if (sources.isEmpty()) return@withIOContext emptyList()

        withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            coroutineScope {
                val deferred = sources.map { source ->
                    async {
                        try {
                            withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                                val page = source.getSearchAnime(
                                    page = 1,
                                    query = cinemetaAnime.title,
                                    filters = AnimeFilterList(),
                                )
                                page.animes.map { sAnime ->
                                    val domainAnime = networkToLocalAnime.await(
                                        sAnime.toDomainAnime(source.id),
                                    )
                                    val score = titleMatcher.match(cinemetaAnime.title, sAnime.title)
                                    StreamCandidate(source, domainAnime, score)
                                }
                            } ?: emptyList()
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Source ${source.name} search failed" }
                            emptyList()
                        }
                    }
                }
                deferred.flatMap { it.await() }
            }
        }
            ?.filter { it.matchScore >= MIN_MATCH_SCORE }
            ?.sortedByDescending { it.matchScore }
            ?: emptyList()
    }

    /**
     * Cache the user's source choice for a Cinemeta entry.
     */
    fun selectCandidate(cinemetaAnime: Anime, candidate: StreamCandidate) {
        sourceMappingCache.put(
            cinemetaAnime.url,
            SourceMappingCache.SourceMapping(
                sourceId = candidate.source.id,
                sourceAnimeUrl = candidate.anime.url,
                sourceAnimeId = candidate.anime.id,
                sourceName = candidate.source.name,
            ),
        )
    }

    /**
     * Clear the cached mapping for a Cinemeta entry.
     * Called when the user wants to change source.
     */
    fun clearMapping(cinemetaAnime: Anime) {
        sourceMappingCache.remove(cinemetaAnime.url)
    }
}
