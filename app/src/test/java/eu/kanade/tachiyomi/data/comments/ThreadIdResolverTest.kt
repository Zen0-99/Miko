package eu.kanade.tachiyomi.data.comments

import eu.kanade.tachiyomi.data.mikodb.AnimeIdBridge
import eu.kanade.tachiyomi.data.mikodb.MikoDbManager
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

class ThreadIdResolverTest {

    private val workIdResolver = mockk<WorkIdResolver>()
    private val mikoDbManager = mockk<MikoDbManager>()
    private val getAnimeTracks = mockk<GetAnimeTracks>()
    private val bridge = mockk<AnimeIdBridge>()

    private val resolver = ThreadIdResolver(workIdResolver, mikoDbManager, getAnimeTracks)

    private fun anime(id: Long, url: String = "src/1", title: String = "Show") =
        mockk<Anime> {
            every { this@mockk.id } returns id
            every { this@mockk.url } returns url
            every { this@mockk.title } returns title
        }

    private fun track(trackerId: Long, remoteId: Long) = AnimeTrack(
        id = 0, animeId = 0, trackerId = trackerId, remoteId = remoteId,
        libraryId = null, title = "", lastEpisodeSeen = 0.0, totalEpisodes = 0,
        status = 0, score = 0.0, remoteUrl = "", startDate = 0, finishDate = 0,
        private = false,
    )

    private fun mapping(imdb: String?) = AnimeIdBridge.AnimeMapping(
        malId = null, malDubId = null, anilistId = null, kitsuId = null,
        anidbId = null, simklId = null, tvdbId = null, tmdbId = null,
        imdbId = imdb, traktId = null, title = null, tvdbSeason = null,
        tvdbPart = null, duration = null, score = null, status = null,
        animeMediaType = null, animeMediaEpisodes = null,
        globalMediaEpisodes = null, dubStatus = null,
    )

    @Test
    fun `manga resolves through work resolver with manga prefix`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } returns
            WorkIdResolver.CanonicalWorkId("mk:mb:42", "manga", WorkIdResolver.Confidence.TITLE)
        val manga = mockk<Manga> {
            every { id } returns 7L
            every { title } returns "Saga"
            every { url } returns "/manga/saga"
            every { author } returns null
        }
        assertEquals("manga:mk:mb:42", resolver.forManga(manga))
    }

    @Test
    fun `novel emits the books carrier prefix`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } returns
            WorkIdResolver.CanonicalWorkId("mk:mb:99", "books", WorkIdResolver.Confidence.TITLE)
        val novel = mockk<Novel> {
            every { id } returns 3L
            every { title } returns "Shadow Slave"
            every { url } returns "/novel/shadow-slave"
            every { author } returns null
            every { genre } returns null
            every { source } returns 1L
            every { isAudiobook } returns false
        }
        assertEquals("books:mk:mb:99", resolver.forNovel(novel))
    }

    @Test
    fun `cinemeta url emits the mk-imdb shows thread directly`() = runBlocking {
        val a = anime(1, "cinemeta:series:tt0903747")
        assertEquals("shows:mk:imdb:tt0903747", resolver.forShows(a))
    }

    @Test
    fun `anilist track lifts to mk-imdb via map`() = runBlocking {
        val a = anime(2)
        coEvery { getAnimeTracks.await(2) } returns listOf(track(TrackerManager.ANILIST, 85208))
        every { mikoDbManager.bridge() } returns bridge
        every { bridge.byAnilist(85208) } returns mapping("tt1234567")
        assertEquals("shows:mk:imdb:tt1234567", resolver.forShows(a))
    }

    @Test
    fun `resolved shows work id beats tracker proto-ids`() = runBlocking {
        val a = anime(3)
        coEvery { getAnimeTracks.await(3) } returns listOf(track(TrackerManager.ANILIST, 42))
        every { mikoDbManager.bridge() } returns bridge
        every { bridge.byAnilist(42) } returns mapping(null)
        every { bridge.byTitle("Show") } returns null
        coEvery { workIdResolver.resolve(any()) } returns
            WorkIdResolver.CanonicalWorkId("mk:hs:some-show", "shows", WorkIdResolver.Confidence.WORKER)
        assertEquals("shows:mk:hs:some-show", resolver.forShows(a))
    }

    @Test
    fun `bare mk-m mint yields to tracker proto-ids`() = runBlocking {
        val a = anime(5)
        coEvery { getAnimeTracks.await(5) } returns listOf(track(TrackerManager.ANILIST, 42))
        every { mikoDbManager.bridge() } returns bridge
        every { bridge.byAnilist(42) } returns mapping(null)
        every { bridge.byTitle("Show") } returns null
        coEvery { workIdResolver.resolve(any()) } returns
            WorkIdResolver.CanonicalWorkId("mk:m:abc", "shows", WorkIdResolver.Confidence.LOCAL_MINT)
        assertEquals("shows:al:42", resolver.forShows(a))
    }

    @Test
    fun `provisional mk-m mint survives when no tracker id exists`() = runBlocking {
        val a = anime(6)
        coEvery { getAnimeTracks.await(6) } returns emptyList()
        every { mikoDbManager.bridge() } returns bridge
        every { bridge.byTitle("Show") } returns null
        coEvery { workIdResolver.resolve(any()) } returns
            WorkIdResolver.CanonicalWorkId("mk:m:abc", "shows", WorkIdResolver.Confidence.LOCAL_MINT)
        assertEquals("shows:mk:m:abc", resolver.forShows(a))
    }

    @Test
    fun `unresolved show with no tracker id returns null`() = runBlocking {
        val a = anime(4)
        coEvery { getAnimeTracks.await(4) } returns emptyList()
        every { mikoDbManager.bridge() } returns bridge
        every { bridge.byTitle("Show") } returns null
        coEvery { workIdResolver.resolve(any()) } returns null
        assertNull(resolver.forShows(a))
    }
}
