package eu.kanade.tachiyomi.metadata.stream

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.metadata.miko.dto.StremioStreamDto
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioAddonConfig
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioStreamAddonClient
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioStreamResolver
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

/**
 * Resolves streaming sources for a Cinemeta anime entry.
 *
 * Two resolution paths:
 * 1. **Stremio addons** (Torrentio, etc.) — calls the Stremio stream API directly
 *    with IMDB ID + season/episode. Returns torrent streams (infoHash + fileIdx).
 *    This is the primary path for all Cinemeta entries.
 * 2. **Aniyomi extensions** — searches installed anime extensions by title.
 *    Only used when the entry is detected as anime (country=Japan + genre=Animation).
 *
 * If a cached mapping exists in [SourceMappingCache], it is returned directly
 * without searching — the user already picked a source for this entry.
 */
class StreamResolver(
    private val sourceManager: AnimeSourceManager,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val sourceMappingCache: SourceMappingCache,
    private val titleMatcher: TitleMatcher,
    private val stremioStreamAddonClient: StremioStreamAddonClient,
    private val stremioStreamResolver: StremioStreamResolver,
    private val stremioAddonConfig: StremioAddonConfig,
) {
    /**
     * A stream candidate from either a Stremio addon or an Aniyomi extension.
     */
    sealed class StreamCandidate {
        abstract val matchScore: Double
        abstract val cached: Boolean
        abstract val cinemetaType: String

        /**
         * A Stremio addon stream (Torrentio, etc.).
         * Contains the raw Stremio stream DTO with infoHash + fileIdx.
         * The [StremioStreamResolver] converts this to a magnet link,
         * which [MagnetResolver] then resolves to a TorrServer HTTP URL.
         */
        data class Stremio(
            val stream: StremioStreamDto,
            val addonName: String,
            override val matchScore: Double = 1.0,
            override val cached: Boolean = false,
            override val cinemetaType: String = "movie",
        ) : StreamCandidate()

        /**
         * An Aniyomi extension search result.
         * Contains the source + anime to fetch episodes from.
         */
        data class Extension(
            val source: AnimeCatalogueSource,
            val anime: Anime,
            override val matchScore: Double,
            override val cached: Boolean = false,
            override val cinemetaType: String = "series",
        ) : StreamCandidate()
    }

    data class ResolveResult(
        val candidates: List<StreamCandidate>,
        val timedOut: Boolean,
        val failedSources: List<String>,
    )

    companion object {
        private const val PER_SOURCE_TIMEOUT_MS = 10_000L
        private const val TOTAL_TIMEOUT_MS = 15_000L
        private const val MIN_MATCH_SCORE = 0.5
        private const val STREMIO_SOURCE_ID = -1000L
    }

    /**
     * Resolve streaming sources for [cinemetaAnime].
     *
     * Returns candidates sorted by match score descending, plus metadata
     * about timeouts and failed sources.
     *
     * If a cached mapping exists, returns a single-element list with
     * [cached] = true — no search is performed.
     */
    suspend fun resolve(
        cinemetaAnime: Anime,
        season: Int? = null,
        episode: Int? = null,
    ): ResolveResult = withIOContext {
        // Extract type from Cinemeta URL: "cinemeta:series:tt123" or "cinemeta:movie:tt123"
        val parts = cinemetaAnime.url.split(":")
        val cinemetaType = parts.getOrNull(1) ?: "series"
        val imdbId = parts.getOrNull(2)
        val isSeries = cinemetaType == "series"

        logcat(LogPriority.DEBUG) {
            "StreamResolver.resolve: anime='${cinemetaAnime.title}', url='${cinemetaAnime.url}', type=$cinemetaType, imdbId=$imdbId, isSeries=$isSeries, season=$season, episode=$episode"
        }

        // Check cache first
        val cached = sourceMappingCache.get(cinemetaAnime.url)
        if (cached != null) {
            // Check if it's a Stremio cached mapping
            if (cached.sourceId == STREMIO_SOURCE_ID) {
                // Re-fetch Stremio streams for this entry
                val stremioCandidates = fetchStremioStreams(cinemetaType, imdbId, season, episode)
                if (stremioCandidates.isNotEmpty()) {
                    return@withIOContext ResolveResult(
                        stremioCandidates.map { it.copy(cached = true) },
                        timedOut = false,
                        failedSources = emptyList(),
                    )
                }
            } else {
                // Extension cached mapping
                val source = sourceManager.get(cached.sourceId) as? AnimeCatalogueSource
                if (source != null) {
                    val cachedAnime = networkToLocalAnime.await(
                        Anime.create().copy(
                            url = cached.sourceAnimeUrl,
                            title = cinemetaAnime.title,
                            source = cached.sourceId,
                        ),
                    )
                    return@withIOContext ResolveResult(
                        listOf(
                            StreamCandidate.Extension(
                                source = source,
                                anime = cachedAnime,
                                matchScore = 1.0,
                                cached = true,
                                cinemetaType = cinemetaType,
                            ),
                        ),
                        timedOut = false,
                        failedSources = emptyList(),
                    )
                }
            }
            // Cached source no longer available — clear and search
            sourceMappingCache.remove(cinemetaAnime.url)
        }

        val results = mutableListOf<StreamCandidate>()
        val failedSources = mutableListOf<String>()
        var timedOut = false

        coroutineScope {
            val deferred = mutableListOf<Deferred<Unit>>()

            // 1. Always fetch Stremio addon streams (direct URL construction)
            if (imdbId != null) {
                deferred.add(
                    async {
                        try {
                            val stremioCandidates = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                                fetchStremioStreams(cinemetaType, imdbId, season, episode)
                            }
                            if (stremioCandidates != null) {
                                synchronized(results) { results.addAll(stremioCandidates) }
                            } else {
                                timedOut = true
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Stremio stream fetch failed" }
                            synchronized(failedSources) { failedSources.add("Stremio addons") }
                        }
                        Unit
                    },
                )
            }

            // 2. Only search Aniyomi extensions if the entry is anime
            val isAnime = AnimeDetection.isAnime(cinemetaAnime.genre, cinemetaAnime.country)
            if (isAnime) {
                logcat(LogPriority.DEBUG) { "StreamResolver: entry is anime — searching Aniyomi extensions too" }
                val sources = sourceManager.getCatalogueSources()
                for (source in sources) {
                    deferred.add(
                        async {
                            try {
                                val searchResult = withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                                    val page = if (imdbId != null) {
                                        try {
                                            val imdbPage = source.getSearchAnime(
                                                page = 1,
                                                query = imdbId,
                                                filters = AnimeFilterList(),
                                            )
                                            if (imdbPage.animes.isNotEmpty()) {
                                                imdbPage
                                            } else {
                                                source.getSearchAnime(
                                                    page = 1,
                                                    query = cinemetaAnime.title,
                                                    filters = AnimeFilterList(),
                                                )
                                            }
                                        } catch (e: Exception) {
                                            source.getSearchAnime(
                                                page = 1,
                                                query = cinemetaAnime.title,
                                                filters = AnimeFilterList(),
                                            )
                                        }
                                    } else {
                                        source.getSearchAnime(
                                            page = 1,
                                            query = cinemetaAnime.title,
                                            filters = AnimeFilterList(),
                                        )
                                    }
                                    page.animes.map { sAnime ->
                                        val domainAnime = networkToLocalAnime.await(
                                            sAnime.toDomainAnime(source.id),
                                        )
                                        val score = titleMatcher.match(cinemetaAnime.title, sAnime.title)
                                        StreamCandidate.Extension(
                                            source = source,
                                            anime = domainAnime,
                                            matchScore = score,
                                            cinemetaType = cinemetaType,
                                        )
                                    }
                                }
                                if (searchResult != null) {
                                    synchronized(results) { results.addAll(searchResult) }
                                } else {
                                    synchronized(failedSources) { failedSources.add(source.name) }
                                }
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Source ${source.name} search failed" }
                                synchronized(failedSources) { failedSources.add(source.name) }
                            }
                            Unit
                        },
                    )
                }
            } else {
                logcat(LogPriority.DEBUG) { "StreamResolver: entry is NOT anime — skipping Aniyomi extensions" }
            }

            // Wait with total timeout — collect partial results on timeout
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                deferred.awaitAll()
            } ?: run {
                timedOut = true
            }
        }

        // Filter and sort candidates
        val filtered = results
            .filter { candidate ->
                when (candidate) {
                    is StreamCandidate.Stremio -> true // Stremio streams are always relevant
                    is StreamCandidate.Extension -> candidate.matchScore >= MIN_MATCH_SCORE
                }
            }
            .sortedByDescending { it.matchScore }

        logcat(LogPriority.DEBUG) {
            "StreamResolver.resolve: ${filtered.size} candidates after filter: ${filtered.take(5).map { c ->
                when (c) {
                    is StreamCandidate.Stremio -> "Stremio(${c.addonName}): ${c.stream.title}"
                    is StreamCandidate.Extension -> "${c.source.name}: ${c.anime.title} (${c.matchScore})"
                }
            }}"
        }

        ResolveResult(
            candidates = filtered,
            timedOut = timedOut,
            failedSources = failedSources.toList(),
        )
    }

    /**
     * Fetch streams from all configured Stremio addons.
     */
    private suspend fun fetchStremioStreams(
        cinemetaType: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
    ): List<StreamCandidate.Stremio> {
        if (imdbId == null) return emptyList()

        val addonUrls = stremioAddonConfig.getAddonUrls()
        val allStreams = mutableListOf<StreamCandidate.Stremio>()

        for (addonUrl in addonUrls) {
            val configuredUrl = stremioAddonConfig.buildAddonUrl(addonUrl)
            val addonName = addonUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")

            val streams = if (cinemetaType == "series" && season != null && episode != null) {
                stremioStreamAddonClient.getSeriesStreams(configuredUrl, imdbId, season, episode)
            } else {
                stremioStreamAddonClient.getMovieStreams(configuredUrl, imdbId)
            }

            for (stream in streams) {
                if (stream.infoHash != null) {
                    allStreams.add(
                        StreamCandidate.Stremio(
                            stream = stream,
                            addonName = addonName,
                            cinemetaType = cinemetaType,
                        ),
                    )
                }
            }
        }

        return allStreams
    }

    /**
     * Cache the user's source choice for a Cinemeta entry.
     */
    fun selectCandidate(cinemetaAnime: Anime, candidate: StreamCandidate) {
        val mapping = when (candidate) {
            is StreamCandidate.Stremio -> SourceMappingCache.SourceMapping(
                sourceId = STREMIO_SOURCE_ID,
                sourceAnimeUrl = "stremio:${cinemetaAnime.url}",
                sourceAnimeId = -1L,
                sourceName = candidate.addonName,
            )
            is StreamCandidate.Extension -> SourceMappingCache.SourceMapping(
                sourceId = candidate.source.id,
                sourceAnimeUrl = candidate.anime.url,
                sourceAnimeId = candidate.anime.id,
                sourceName = candidate.source.name,
            )
        }
        sourceMappingCache.put(cinemetaAnime.url, mapping)
    }

    /**
     * Clear the cached mapping for a Cinemeta entry.
     * Called when the user wants to change source.
     */
    fun clearMapping(cinemetaAnime: Anime) {
        sourceMappingCache.remove(cinemetaAnime.url)
    }
}
