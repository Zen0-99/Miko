package eu.kanade.tachiyomi.metadata.stream

import eu.kanade.domain.entries.shows.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.data.mikodb.MikoDbManager
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.metadata.miko.dto.StremioStreamDto
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioAddonConfig
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioStreamAddonClient
import eu.kanade.tachiyomi.metadata.stream.stremio.StremioStreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    private val mikoDbManager: MikoDbManager,
    private val animeExtensionManager: AnimeExtensionManager,
    private val getAnime: GetAnime,
    private val getEpisode: GetEpisode,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
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
            val addonLogoUrl: String? = null,
            override val matchScore: Double = 1.0,
            override val cached: Boolean = false,
            override val cinemetaType: String = "movie",
        ) : StreamCandidate()

        /**
         * An Aniyomi extension search result.
         * Carries only the source anime's identity ([animeUrl]/[animeTitle]) —
         * a real DB [Anime] row is materialized via [materializeExtension]
         * only when the user actually picks this candidate. Materializing
         * every search result would spam the anime table with one row per
         * extension per entry (that's what created the duplicate entries).
         * [episodeName] carries the Cinemeta episode name so the picker can
         * show episode context even though extension episode lists aren't
         * fetched yet at candidate time.
         */
        data class Extension(
            val source: AnimeCatalogueSource,
            val animeUrl: String,
            val animeTitle: String,
            override val matchScore: Double,
            override val cached: Boolean = false,
            override val cinemetaType: String = "series",
            val episodeName: String? = null,
        ) : StreamCandidate()
    }

    /**
     * Per-provider search status, used by the picker to grey out provider
     * pills until that provider has returned (or failed).
     */
    enum class ProviderStatus { LOADING, DONE, FAILED }

    /**
     * A live snapshot of an in-flight (or finished) source search. Emitted
     * incrementally — fast providers like Torrentio appear immediately while
     * slower extensions are still searching.
     */
    data class ResolveProgress(
        val candidates: List<StreamCandidate> = emptyList(),
        val providerStatuses: Map<String, ProviderStatus> = emptyMap(),
        val timedOut: Boolean = false,
        val finished: Boolean = false,
    ) {
        val failedSources: List<String>
            get() = providerStatuses.filterValues { it == ProviderStatus.FAILED }.keys.toList()
    }

    private data class CachedResolve(
        val timestamp: Long,
        val progress: ResolveProgress,
    )

    private data class AddonStreams(
        val fallbackName: String,
        val displayName: String,
        val streams: List<StreamCandidate.Stremio>,
    )

    companion object {
        private const val PER_SOURCE_TIMEOUT_MS = 25_000L
        private const val IMDB_PROBE_TIMEOUT_MS = 6_000L
        private const val TOTAL_TIMEOUT_MS = 32_000L
        private const val MIN_MATCH_SCORE = 0.5
        private const val STREMIO_SOURCE_ID = -1000L
        private const val RESULT_TTL_MS = 30 * 60 * 1000L
    }

    private val searchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeSearches = ConcurrentHashMap<String, MutableStateFlow<ResolveProgress>>()
    private val finishedResults = ConcurrentHashMap<String, CachedResolve>()

    private fun searchKey(animeUrl: String, season: Int?, episode: Int?): String =
        "$animeUrl|${season ?: -1}|${episode ?: -1}"

    /**
     * Resolve streaming sources for [cinemetaAnime], returning a live
     * [StateFlow] of [ResolveProgress] that emits as each provider finishes.
     * Callers collect and render incrementally — no waiting for the slowest
     * source.
     *
     * Results are cached per (anime, season, episode): reopening the picker
     * for the same episode within [RESULT_TTL_MS] replays the finished
     * result instantly instead of re-searching. An in-flight search for the
     * same key is shared — callers attach to the same flow.
     */
    fun search(
        cinemetaAnime: Anime,
        season: Int? = null,
        episode: Int? = null,
        episodeName: String? = null,
    ): StateFlow<ResolveProgress> {
        val key = searchKey(cinemetaAnime.url, season, episode)
        finishedResults[key]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestamp < RESULT_TTL_MS) {
                logcat(LogPriority.DEBUG) { "StreamResolver: cache hit for $key" }
                return MutableStateFlow(cached.progress)
            }
            finishedResults.remove(key)
        }
        activeSearches[key]?.let { return it }

        val flow = MutableStateFlow(ResolveProgress())
        activeSearches[key] = flow
        searchScope.launch {
            try {
                runSearch(cinemetaAnime, season, episode, episodeName, flow)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Stream resolution failed" }
                flow.value = flow.value.copy(finished = true)
            } finally {
                activeSearches.remove(key)
                val final = flow.value
                if (final.finished) {
                    finishedResults[key] = CachedResolve(System.currentTimeMillis(), final)
                }
            }
        }
        return flow
    }

    /**
     * Drop the finished-result cache for a given (anime, season, episode).
     * Called when the user explicitly wants a fresh search (change source).
     */
    fun invalidateSearch(animeUrl: String, season: Int?, episode: Int?) {
        finishedResults.remove(searchKey(animeUrl, season, episode))
    }

    private suspend fun runSearch(
        cinemetaAnime: Anime,
        season: Int?,
        episode: Int?,
        episodeName: String?,
        flow: MutableStateFlow<ResolveProgress>,
    ) {
        // Extract type from Cinemeta URL: "cinemeta:series:tt123" or "cinemeta:movie:tt123"
        val parts = cinemetaAnime.url.split(":")
        val cinemetaType = parts.getOrNull(1) ?: "series"
        val imdbId = parts.getOrNull(2)

        logcat(LogPriority.DEBUG) {
            "StreamResolver.search: anime='${cinemetaAnime.title}', url='${cinemetaAnime.url}', type=$cinemetaType, imdbId=$imdbId, season=$season, episode=$episode"
        }

        // Check mapping cache first — a cached mapping pins the choice at the
        // top ("Cached" badge) but the full search still runs so the picker
        // always shows every available source.
        val cachedMapping = sourceMappingCache.get(cinemetaAnime.url)
        var cachedAddonName: String? = null
        var pinnedExtension: StreamCandidate.Extension? = null
        if (cachedMapping != null) {
            if (cachedMapping.sourceId == STREMIO_SOURCE_ID) {
                cachedAddonName = cachedMapping.sourceName
            } else {
                val source = sourceManager.get(cachedMapping.sourceId) as? AnimeCatalogueSource
                if (source != null) {
                    pinnedExtension = StreamCandidate.Extension(
                        source = source,
                        animeUrl = cachedMapping.sourceAnimeUrl,
                        animeTitle = cinemetaAnime.title,
                        matchScore = 1.0,
                        cached = true,
                        cinemetaType = cinemetaType,
                        episodeName = episodeName,
                    )
                } else {
                    // Cached source no longer available — clear and search
                    sourceMappingCache.remove(cinemetaAnime.url)
                }
            }
        }

        val results = CopyOnWriteArrayList<StreamCandidate>()
        val providerStatuses = ConcurrentHashMap<String, ProviderStatus>()
        val providerOrder = CopyOnWriteArrayList<String>()
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)

        fun registerProvider(name: String) {
            if (name !in providerOrder) providerOrder.add(name)
            providerStatuses[name] = ProviderStatus.LOADING
        }

        fun renameProvider(old: String, new: String) {
            if (old == new) return
            val idx = providerOrder.indexOf(old)
            if (idx >= 0) providerOrder[idx] = new else providerOrder.add(new)
            providerStatuses.remove(old)?.let { providerStatuses[new] = it }
        }

        fun publish(finished: Boolean = false) {
            val snapshot = providerOrder
                .map { it to (providerStatuses[it] ?: ProviderStatus.LOADING) }
                .toMap()
            flow.value = ResolveProgress(
                candidates = buildCandidateList(results.toList(), pinnedExtension),
                providerStatuses = snapshot,
                timedOut = timedOut.get(),
                finished = finished,
            )
        }

        coroutineScope {
            val jobs = mutableListOf<Job>()

            // MikoDB identity is the stronger anime signal: a local row with
            // MAL/AniList ids IS anime, even when the stored Cinemeta
            // country/genre fields are missing (stale or catalog-trimmed).
            val bridge = mikoDbManager.bridge()
            val mikoDbMatch = imdbId != null && bridge?.mainEntry(imdbId) != null
            val isAnime = AnimeDetection.isAnime(cinemetaAnime.genre, cinemetaAnime.country) || mikoDbMatch

            // Canonical identity + episode binding for this Cinemeta S/E.
            // canonicalTitle prefers the bound split-cour entry title (e.g.
            // "Attack on Titan Season 3 Part 2") over the generic series title.
            val episodeBinding = if (isAnime && imdbId != null && season != null && episode != null) {
                bridge?.bindEpisode(imdbId, season, episode)
            } else {
                null
            }
            val canonicalTitle = episodeBinding?.row?.title
                ?: imdbId?.takeIf { isAnime }?.let { bridge?.mainEntry(it)?.title }

            // Stremio addon streams — each addon is its own provider/job so
            // fast addons (Torrentio) publish before slower ones finish.
            val addonUrls = if (imdbId != null) stremioAddonConfig.getAddonUrls() else emptyList()
            for (addonUrl in addonUrls) {
                registerProvider(addonFallbackName(addonUrl))
            }

            // Aniyomi extensions — only when the entry is anime. Nyaa is
            // excluded (see isNyaaSource). The local source sorts last so its
            // provider pill sits at the end.
            val sources = if (isAnime) {
                sourceManager.getCatalogueSources()
                    .filterNot { isNyaaSource(it) }
                    .sortedBy { it.id == 0L }
            } else {
                emptyList()
            }
            if (isAnime) {
                logcat(LogPriority.DEBUG) {
                    "StreamResolver: entry is anime (mikoDbMatch=$mikoDbMatch) — searching ${sources.size} extensions"
                }
            } else {
                logcat(LogPriority.DEBUG) { "StreamResolver: entry is NOT anime — skipping Aniyomi extensions" }
            }
            for (source in sources) {
                registerProvider(source.name)
            }
            publish()

            for (addonUrl in addonUrls) {
                jobs += async {
                    val fallbackName = addonFallbackName(addonUrl)
                    try {
                        val addon = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                            fetchAddonStreams(
                                addonUrl,
                                cinemetaType,
                                imdbId!!,
                                season,
                                episode,
                                cachedAddonName,
                            )
                        }
                        if (addon != null) {
                            renameProvider(fallbackName, addon.displayName)
                            results.addAll(addon.streams)
                            providerStatuses[addon.displayName] = ProviderStatus.DONE
                        } else {
                            providerStatuses[fallbackName] = ProviderStatus.FAILED
                            timedOut.set(true)
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Stremio addon $addonUrl fetch failed" }
                        providerStatuses[fallbackName] = ProviderStatus.FAILED
                    }
                    publish()
                }
            }

            for (source in sources) {
                jobs += async {
                    try {
                        val searchResult = withTimeoutOrNull(PER_SOURCE_TIMEOUT_MS) {
                            searchExtensionSource(
                                source = source,
                                imdbId = imdbId,
                                cinemetaTitle = cinemetaAnime.title,
                                canonicalTitle = canonicalTitle,
                                cinemetaType = cinemetaType,
                                episodeName = episodeName,
                                episodeBindingAnilistId = episodeBinding?.row?.anilistId,
                                episodeBindingMalId = episodeBinding?.row?.malId,
                            )
                        }
                        if (searchResult != null) {
                            results.addAll(searchResult)
                            providerStatuses[source.name] = ProviderStatus.DONE
                        } else {
                            logcat(LogPriority.WARN) {
                                "Source ${source.name} search timed out after ${PER_SOURCE_TIMEOUT_MS}ms"
                            }
                            providerStatuses[source.name] = ProviderStatus.FAILED
                        }
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Source ${source.name} search failed" }
                        providerStatuses[source.name] = ProviderStatus.FAILED
                    }
                    publish()
                }
            }

            // Wait with total timeout — providers still loading are marked failed
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
                jobs.joinAll()
            } ?: run {
                timedOut.set(true)
                for (name in providerOrder) {
                    if (providerStatuses[name] == ProviderStatus.LOADING) {
                        providerStatuses[name] = ProviderStatus.FAILED
                    }
                }
            }
        }

        publish(finished = true)

        logcat(LogPriority.DEBUG) {
            "StreamResolver.search finished: ${flow.value.candidates.size} candidates, " +
                "providers=${flow.value.providerStatuses}"
        }
    }

    /**
     * Filter and sort raw results — pinned cached choice first, then by score.
     */
    private fun buildCandidateList(
        results: List<StreamCandidate>,
        pinnedExtension: StreamCandidate.Extension?,
    ): List<StreamCandidate> {
        return results
            .filter { candidate ->
                when (candidate) {
                    is StreamCandidate.Stremio -> true // Stremio streams are always relevant
                    is StreamCandidate.Extension -> candidate.matchScore >= MIN_MATCH_SCORE
                }
            }
            .filter { candidate ->
                // Drop the searched duplicate of the pinned cached extension entry
                pinnedExtension == null ||
                    candidate !is StreamCandidate.Extension ||
                    candidate.source.id != pinnedExtension.source.id ||
                    candidate.animeUrl != pinnedExtension.animeUrl
            }
            .sortedByDescending { it.matchScore }
            .let { list ->
                if (pinnedExtension != null) listOf(pinnedExtension) + list else list
            }
    }

    /**
     * Search a single Aniyomi extension for the entry. Tries the IMDB id
     * first (some sources index it), then the canonical/Cinemeta title.
     * Scores each result; an exact MikoDB id bind (tier-A sources keying on
     * AniList/MAL ids) pins the score to 1.0.
     */
    private suspend fun searchExtensionSource(
        source: AnimeCatalogueSource,
        imdbId: String?,
        cinemetaTitle: String,
        canonicalTitle: String?,
        cinemetaType: String,
        episodeName: String?,
        episodeBindingAnilistId: Long?,
        episodeBindingMalId: Long?,
    ): List<StreamCandidate.Extension> {
        val sourceMeta = animeExtensionManager.getExtensionPackage(source.id)
            ?.let { mikoDbManager.sourceMeta(it) }
        val titleQuery = canonicalTitle ?: cinemetaTitle
        val page = if (imdbId != null) {
            // Probe imdb-id and title concurrently: a slow or hanging site must
            // not serialize both requests inside the per-source budget (e.g.
            // AnimeGG answers in ~16s+ under load; two serial probes would
            // always exceed the budget and yield nothing).
            coroutineScope {
                suspend fun probe(query: String, timeoutMs: Long?): AnimesPage? =
                    try {
                        if (timeoutMs != null) {
                            withTimeoutOrNull(timeoutMs) {
                                source.getSearchAnime(page = 1, query = query, filters = AnimeFilterList())
                            }
                        } else {
                            source.getSearchAnime(page = 1, query = query, filters = AnimeFilterList())
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }

                val imdbProbe = async { probe(imdbId, IMDB_PROBE_TIMEOUT_MS) }
                val titleProbe = async { probe(titleQuery, null) }
                val imdbPage = imdbProbe.await()
                val titlePage = titleProbe.await()
                if (imdbPage != null && imdbPage.animes.isNotEmpty()) {
                    imdbPage
                } else {
                    titlePage ?: error("search failed")
                }
            }
        } else {
            source.getSearchAnime(page = 1, query = titleQuery, filters = AnimeFilterList())
        }
        return page.animes.map { sAnime ->
            val score = maxOf(
                titleMatcher.match(cinemetaTitle, sAnime.title),
                canonicalTitle?.let { titleMatcher.match(it, sAnime.title) } ?: 0.0,
            )
            // Exact bind: source URLs embedding the canonical id
            // (tier-A sites key entries on AniList/MAL ids).
            val boundId = when (sourceMeta?.idType) {
                "anilist" -> episodeBindingAnilistId
                "mal" -> episodeBindingMalId
                else -> null
            }
            val finalScore = if (boundId != null && sAnime.url.contains(boundId.toString())) {
                1.0
            } else {
                score
            }
            StreamCandidate.Extension(
                source = source,
                animeUrl = sAnime.url,
                animeTitle = sAnime.title,
                matchScore = finalScore,
                cinemetaType = cinemetaType,
                episodeName = episodeName,
            )
        }
    }

    private fun addonFallbackName(addonUrl: String): String =
        addonUrl.removePrefix("https://").removePrefix("http://").substringBefore(".")

    /**
     * Fetch streams from a single Stremio addon (manifest for name/logo +
     * series/movie streams).
     */
    private suspend fun fetchAddonStreams(
        addonUrl: String,
        cinemetaType: String,
        imdbId: String,
        season: Int?,
        episode: Int?,
        cachedAddonName: String?,
    ): AddonStreams {
        val fallbackName = addonFallbackName(addonUrl)
        val configuredUrl = stremioAddonConfig.buildAddonUrl(addonUrl)
        val manifest = stremioStreamAddonClient.getManifest(addonUrl)
        val addonName = manifest?.name ?: fallbackName

        val streams = if (cinemetaType == "series" && season != null && episode != null) {
            stremioStreamAddonClient.getSeriesStreams(configuredUrl, imdbId, season, episode)
        } else {
            stremioStreamAddonClient.getMovieStreams(configuredUrl, imdbId)
        }

        val candidates = streams.mapNotNull { stream ->
            stream.infoHash?.let {
                StreamCandidate.Stremio(
                    stream = stream,
                    addonName = addonName,
                    addonLogoUrl = manifest?.logo,
                    cached = cachedAddonName != null &&
                        (
                            addonName.equals(cachedAddonName, ignoreCase = true) ||
                                addonUrl.contains(cachedAddonName, ignoreCase = true)
                            ),
                    cinemetaType = cinemetaType,
                )
            }
        }
        return AddonStreams(fallbackName = fallbackName, displayName = addonName, streams = candidates)
    }

    /**
     * Nyaa was removed from stream resolution — it proved extremely
     * unreliable (dead results, frequent timeouts, misleading matches).
     * If anyone wants to tackle making it work properly, be my guest:
     * remove this filter and test thoroughly before shipping it back.
     */
    private fun isNyaaSource(source: AnimeCatalogueSource): Boolean {
        if (source.name.contains("nyaa", ignoreCase = true)) return true
        return try {
            animeExtensionManager.getExtensionPackage(source.id)
                ?.contains("nyaa", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Ordered candidate episode numbers on [sourceId] for a Cinemeta
     * (season, episode), most-likely first. Uses the MikoDB binding when
     * available; falls back to the bare episode number without it.
     *
     * Numbering-aware: sources marked `absolute` in miko.json get the
     * whole-series absolute number first; entry-split sources get the
     * bound entry's local number first.
     */
    fun episodeTargets(sourceId: Long, imdbId: String?, season: Int?, episode: Int?): List<Int> {
        if (episode == null) return emptyList()
        if (imdbId == null || season == null) return listOf(episode)
        val binding = mikoDbManager.bridge()?.bindEpisode(imdbId, season, episode)
            ?: return listOf(episode)
        val numbering = animeExtensionManager.getExtensionPackage(sourceId)
            ?.let { mikoDbManager.sourceMeta(it)?.numbering }
            .orEmpty()
        return buildList {
            if (numbering.contains("absolute")) add(binding.absoluteEpisode)
            add(binding.entryEpisode)
            add(binding.seasonEpisode)
            add(binding.absoluteEpisode)
            add(episode)
        }.distinct()
    }

    /**
     * Materialize the DB [Anime] row for a picked extension candidate.
     * Search results are never inserted — a row only exists once the user
     * has actually chosen this source, so it's safe to call repeatedly
     * (networkToLocalAnime dedupes by url+source).
     */
    suspend fun materializeExtension(candidate: StreamCandidate.Extension): Anime {
        return networkToLocalAnime.await(
            Anime.create().copy(
                url = candidate.animeUrl,
                title = candidate.animeTitle,
                source = candidate.source.id,
            ),
        )
    }

    /**
     * Cache the user's source choice for a Cinemeta entry.
     */
    suspend fun selectCandidate(cinemetaAnime: Anime, candidate: StreamCandidate) {
        val mapping = when (candidate) {
            is StreamCandidate.Stremio -> SourceMappingCache.SourceMapping(
                sourceId = STREMIO_SOURCE_ID,
                sourceAnimeUrl = "stremio:${cinemetaAnime.url}",
                sourceAnimeId = -1L,
                sourceName = candidate.addonName,
            )
            is StreamCandidate.Extension -> SourceMappingCache.SourceMapping(
                sourceId = candidate.source.id,
                sourceAnimeUrl = candidate.animeUrl,
                sourceAnimeId = materializeExtension(candidate).id,
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

    /**
     * Resolve a Cinemeta [episode] to its playable source episode using the
     * cached source mapping — the fast path for play entry points that can't
     * show the picker (home hero, continue-watching, history, updates).
     *
     * Returns null when no mapping exists, the mapped source is gone, or the
     * episode can't be matched — callers should fall back to the picker flow
     * or open the detail screen.
     *
     * Stremio-mapped entries return null: resolving a stream requires a
     * search, which belongs behind the picker.
     */
    /**
     * Resolve (animeId, episodeId) to the pair that can actually be played.
     * Non-Cinemeta entries pass through unchanged; Cinemeta entries resolve
     * through the cached source mapping. Null when the entry is Cinemeta
     * without a usable mapping — the caller must fall back to the picker
     * or the detail screen instead of launching the player.
     */
    suspend fun resolvePlayableByIds(animeId: Long, episodeId: Long): Pair<Anime, Episode>? {
        val anime = getAnime.await(animeId) ?: return null
        val episode = getEpisode.await(episodeId) ?: return null
        val isCinemeta = anime.source == 0L && anime.url.startsWith("cinemeta:")
        if (!isCinemeta) return anime to episode
        return resolveCachedEpisode(anime, episode)?.let { sourceEpisode ->
            getAnime.await(sourceEpisode.animeId)?.let { it to sourceEpisode }
        }
    }

    suspend fun resolveCachedEpisode(cinemetaAnime: Anime, episode: Episode): Episode? {
        val mapping = sourceMappingCache.get(cinemetaAnime.url) ?: return null
        if (mapping.sourceId == STREMIO_SOURCE_ID) return null
        val source = sourceManager.get(mapping.sourceId) as? AnimeCatalogueSource
            ?: run {
                sourceMappingCache.remove(cinemetaAnime.url)
                return null
            }
        val sourceAnime = getAnime.await(mapping.sourceAnimeId)
        if (sourceAnime == null || sourceAnime.source != mapping.sourceId) {
            sourceMappingCache.remove(cinemetaAnime.url)
            return null
        }

        var episodes = getEpisodesByAnimeId.await(sourceAnime.id)
        if (episodes.isEmpty()) {
            val sEpisodes = try {
                source.getEpisodeList(sourceAnime.toSAnime())
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) {
                    "resolveCachedEpisode: episode fetch failed for ${source.name}"
                }
                return null
            }
            if (sEpisodes.isNotEmpty()) {
                syncEpisodesWithSource.await(sEpisodes, sourceAnime, source, manualFetch = false)
                episodes = getEpisodesByAnimeId.await(sourceAnime.id)
            }
        }
        if (episodes.isEmpty()) return null

        return matchCachedEpisode(episodes, source.id, cinemetaAnime.url, episode)
    }

    /**
     * Match a Cinemeta episode against a source's episode list.
     * Same strategy as the picker's resolution: exact name first, then
     * MikoDB-aware episode-number targets.
     */
    private fun matchCachedEpisode(
        episodes: List<Episode>,
        sourceId: Long,
        cinemetaUrl: String,
        cinemetaEpisode: Episode,
    ): Episode? {
        val cleanName = cinemetaEpisode.name.trim()
        if (cleanName.isNotEmpty()) {
            episodes.find { it.name.trim().equals(cleanName, ignoreCase = true) }
                ?.let { return it }
        }

        val imdbId = cinemetaUrl.split(":").getOrNull(2)
        val (season, epFromUrl) = parseSeasonEpisode(cinemetaEpisode.url)
        val targets = episodeTargets(
            sourceId,
            imdbId,
            season,
            epFromUrl ?: cinemetaEpisode.episodeNumber.toInt(),
        )
        return targets.firstNotNullOfOrNull { target ->
            episodes.find { it.episodeNumber.toInt() == target }
        }
    }

    /** "cinemeta:episode:tt0903747:1:1" → (season, episode) */
    private fun parseSeasonEpisode(episodeUrl: String?): Pair<Int?, Int?> {
        if (episodeUrl == null || !episodeUrl.startsWith("cinemeta:episode:")) return null to null
        val parts = episodeUrl.split(":")
        return parts.getOrNull(3)?.toIntOrNull() to parts.getOrNull(4)?.toIntOrNull()
    }
}
