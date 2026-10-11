package miko.ios

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import miko.ui.carrier.CarrierBinding
import miko.ui.carrier.CarrierSource
import miko.ui.carrier.FeedEntry
import miko.ui.carrier.FeedPage
import miko.ui.carrier.HosterEntry
import miko.ui.carrier.LocalEntry
import miko.ui.carrier.LocalItem
import miko.ui.carrier.RemoteDetails
import miko.ui.carrier.RemoteItem
import miko.ui.carrier.VideoEntry
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.shows.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.shows.model.AnimeUpdate
import tachiyomi.domain.entries.shows.repository.AnimeRepository
import tachiyomi.domain.history.shows.model.AnimeHistoryUpdate
import tachiyomi.domain.history.shows.repository.AnimeHistoryRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import kotlin.time.Clock

/** Shows carrier — wraps the JS anime source manager + anime repositories. */
class AnimeCarrierBinding(
    private val sourceManager: AnimeSourceManager,
    private val getAnime: GetAnime,
    private val networkToLocalAnime: NetworkToLocalAnime,
    private val animeRepository: AnimeRepository,
    private val episodeRepository: EpisodeRepository,
    private val historyRepository: AnimeHistoryRepository,
) : CarrierBinding {

    override val isInitialized: StateFlow<Boolean> = sourceManager.isInitialized

    override val catalogueSources: Flow<List<CarrierSource>> =
        sourceManager.catalogueSources.map { sources ->
            sources.map { AnimeCarrierSource(it) }
        }

    override fun source(sourceId: Long): CarrierSource? =
        (sourceManager.get(sourceId) as? AnimeCatalogueSource)?.let(::AnimeCarrierSource)

    override fun localEntry(url: String, sourceId: Long): Flow<LocalEntry?> =
        getAnime.subscribe(url, sourceId).map { anime ->
            anime?.let {
                LocalEntry(id = it.id, title = it.title, favorite = it.favorite, url = it.url, sourceId = it.source)
            }
        }

    override suspend fun localEntryById(id: Long): LocalEntry? =
        runCatching { animeRepository.getAnimeById(id) }.getOrNull()?.let {
            LocalEntry(id = it.id, title = it.title, favorite = it.favorite, url = it.url, sourceId = it.source)
        }

    override suspend fun materialize(details: RemoteDetails, sourceId: Long): LocalEntry {
        val anime = networkToLocalAnime.await(details.toDomainAnime(sourceId))
        return LocalEntry(
            id = anime.id,
            title = anime.title,
            favorite = anime.favorite,
            url = anime.url,
            sourceId = anime.source,
        )
    }

    override suspend fun setFavorite(localId: Long, favorite: Boolean) {
        animeRepository.updateAnime(AnimeUpdate(id = localId, favorite = favorite))
    }

    override fun localItems(localId: Long): Flow<List<LocalItem>> = flow {
        // getEpisodeByAnimeIdAsFlow is suspend (unlike the novel repo's).
        emitAll(
            episodeRepository.getEpisodeByAnimeIdAsFlow(localId).map { episodes ->
                episodes.map {
                    LocalItem(id = it.id, url = it.url, name = it.name, done = it.seen)
                }
            },
        )
    }

    override suspend fun getLocalItems(localId: Long): List<LocalItem> =
        episodeRepository.getEpisodeByAnimeId(localId).map {
            LocalItem(id = it.id, url = it.url, name = it.name, done = it.seen)
        }

    override suspend fun addMissingItems(localId: Long, remote: List<RemoteItem>) {
        val existing = getLocalItems(localId).mapTo(HashSet()) { it.url }
        val now = Clock.System.now().toEpochMilliseconds()
        val missing = remote.mapIndexedNotNull { index, item ->
            if (item.url in existing) {
                null
            } else {
                Episode.create().copy(
                    animeId = localId,
                    url = item.url,
                    name = item.name,
                    dateUpload = item.dateUpload,
                    dateFetch = now,
                    episodeNumber = item.number,
                    scanlator = item.scanlator,
                    summary = item.summary,
                    previewUrl = item.previewUrl,
                    sourceOrder = index.toLong(),
                )
            }
        }
        if (missing.isNotEmpty()) episodeRepository.addAllEpisodes(missing)
    }

    override suspend fun refreshFavorites(): Int {
        var fetched = 0
        for (libraryAnime in animeRepository.getLibraryAnime()) {
            val anime = libraryAnime.anime
            val remote = source(anime.source)?.items(anime.url) ?: continue
            addMissingItems(anime.id, remote)
            fetched++
        }
        return fetched
    }

    override suspend fun setItemDone(itemId: Long, done: Boolean) {
        episodeRepository.updateEpisode(EpisodeUpdate(id = itemId, seen = done))
    }

    override suspend fun setAllItemsDone(localId: Long, done: Boolean) {
        val updates = getLocalItems(localId)
            .map { EpisodeUpdate(id = it.id, seen = done) }
        if (updates.isNotEmpty()) episodeRepository.updateAllEpisodes(updates)
    }

    override suspend fun recordItemOpen(itemId: Long) {
        historyRepository.upsertAnimeHistory(
            AnimeHistoryUpdate(itemId, Clock.System.now()),
        )
    }

    // Anime history has no duration column — seenAt on open is the row.

    /** Episode stream hosts — resolved lazily the way Android's player does. */
    override suspend fun hosters(sourceId: Long, episodeUrl: String): List<HosterEntry> {
        val source = sourceManager.get(sourceId)
            ?: return emptyList()
        val episode = SEpisode.create().apply { url = episodeUrl }
        return source.getHosterList(episode).map { hoster ->
            HosterEntry(
                url = hoster.hosterUrl,
                name = hoster.hosterName,
                internalData = hoster.internalData,
                lazy = hoster.lazy,
                videos = hoster.videoList?.map {
                    VideoEntry(url = it.videoUrl, title = it.videoTitle)
                },
            )
        }
    }

    /** Resolve a hoster's (or NO_HOSTER episode's) playable streams. */
    override suspend fun videos(sourceId: Long, target: String): List<VideoEntry> {
        val source = sourceManager.get(sourceId)
            ?: return emptyList()
        return source.getVideoList(eu.kanade.tachiyomi.animesource.model.Hoster(internalData = target))
            .map { VideoEntry(url = it.videoUrl, title = it.videoTitle) }
    }

    private fun RemoteDetails.toDomainAnime(sourceId: Long) =
        tachiyomi.domain.entries.shows.model.Anime.create().copy(
            url = url,
            title = title,
            artist = artist,
            author = author,
            description = description,
            genre = genre,
            status = status.toLong(),
            thumbnailUrl = thumbnailUrl,
            backgroundUrl = backgroundUrl,
            initialized = true,
            source = sourceId,
        )
}

private class AnimeCarrierSource(
    private val source: AnimeCatalogueSource,
) : CarrierSource {

    override val id: Long = source.id
    override val name: String = source.name
    override val lang: String = source.lang
    override val supportsLatest: Boolean = source.supportsLatest

    override suspend fun popular(page: Int) = source.getPopularAnime(page).toFeedPage()
    override suspend fun latest(page: Int) = source.getLatestUpdates(page).toFeedPage()

    override suspend fun search(page: Int, query: String): FeedPage =
        source.getSearchAnime(page, query, source.getFilterList()).toFeedPage()

    override suspend fun details(entryUrl: String, stubTitle: String): RemoteDetails? {
        val stub = SAnime.create().apply {
            url = entryUrl
            title = stubTitle
        }
        val anime = source.getAnimeDetails(stub)
        return RemoteDetails(
            url = anime.url,
            title = anime.title,
            author = anime.author,
            artist = anime.artist,
            description = anime.description,
            genre = anime.getGenres(),
            status = anime.status,
            thumbnailUrl = anime.thumbnail_url,
            backgroundUrl = anime.background_url,
        )
    }

    override suspend fun items(entryUrl: String): List<RemoteItem> {
        val stub = SAnime.create().apply {
            url = entryUrl
            title = entryUrl
        }
        val seasons = source.getSeasonList(stub)
        // Flat plugins echo the anime back as the only "season" — then the
        // episode list comes straight off the entry. Season-structured
        // plugins get one getEpisodeList per season, merged in order.
        val flat = seasons.size <= 1 && seasons.firstOrNull()?.url == entryUrl
        if (flat) {
            return source.getEpisodeList(stub).map { it.toRemoteItem() }
        }
        return seasons.flatMap { season ->
            runCatching { source.getEpisodeList(season) }
                .getOrDefault(emptyList())
                .map { it.toRemoteItem() }
        }
    }

    private fun AnimesPage.toFeedPage() =
        FeedPage(
            entries = animes.map { FeedEntry(it.url, it.title, it.thumbnail_url, it.author) },
            hasNextPage = hasNextPage,
        )

    private fun SEpisode.toRemoteItem() = RemoteItem(
        url = url,
        name = name,
        number = episode_number.toDouble(),
        dateUpload = date_upload,
        scanlator = scanlator,
        summary = summary,
        previewUrl = preview_url,
    )
}
