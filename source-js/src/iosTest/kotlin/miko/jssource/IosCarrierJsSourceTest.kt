package miko.jssource

import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

/**
 * End-to-end bridge tests for the manga/anime JS source adapters: a real
 * [IosJsRuntimeFactory] runtime executes minimal plugins through
 * [IosMangaJsSource]/[IosAnimeJsSource] and the parsed model objects are
 * asserted — the same path Sources/Browse/Details take at runtime.
 */
class IosCarrierJsSourceTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sourceIdsAreStableAndMatchNovelScheme() {
        // Library rows and backups resolve the same source across carriers
        // and platforms only if every bridge derives ids identically.
        assertEquals(
            NovelPluginId.toSourceId("com.example.plugin"),
            JsSourceIds.toSourceId("com.example.plugin"),
        )
        assertEquals(JsSourceIds.toSourceId("a"), JsSourceIds.toSourceId("a"))
        assertTrue(JsSourceIds.toSourceId("a") != JsSourceIds.toSourceId("b"))
        assertTrue(JsSourceIds.toSourceId("a") >= 0)
    }

    private fun mangaSource(script: String): IosMangaJsSource = IosMangaJsSource(
        runtimeProvider = { IosJsRuntimeFactory().create("test-manga-src") },
        script = script,
        pluginId = "test-manga-src",
        json = json,
        id = 11L,
        name = "Test Manga",
        lang = "en",
    )

    private fun animeSource(script: String): IosAnimeJsSource = IosAnimeJsSource(
        runtimeProvider = { IosJsRuntimeFactory().create("test-anime-src") },
        script = script,
        pluginId = "test-anime-src",
        json = json,
        id = 12L,
        name = "Test Anime",
        lang = "en",
    )

    // ---------------------------------------------------------------
    // Manga
    // ---------------------------------------------------------------

    private val mangaPlugin = """
        module.exports = {
            popularManga: function(page, options) {
                return Promise.resolve({
                    mangas: [
                        {
                            path: "/m/one",
                            name: "One",
                            cover: "https://x/c1.jpg",
                            genres: ["Action", "Drama"],
                            status: "ongoing",
                            author: "Auth"
                        },
                        {
                            url: "/m/two",
                            title: "Two",
                            thumbnail_url: "https://x/c2.jpg",
                            genre: "Romance",
                            status: "completed"
                        }
                    ],
                    hasNextPage: true
                });
            },
            searchManga: function(query, page, filters) {
                return Promise.resolve([
                    { path: "/m/q-" + query, name: "Q " + query }
                ]);
            },
            parseManga: function(url) {
                return Promise.resolve({
                    name: "Parsed " + url,
                    summary: "the summary",
                    genres: "Action, Drama",
                    status: "on hiatus",
                    cover: "https://x/pd.jpg",
                    artist: "Art",
                    chapters: [
                        {
                            path: "/m/one/c1",
                            name: "Ch 1",
                            chapterNumber: 1,
                            releaseTime: "2024-05-01",
                            scanlator: "grp"
                        },
                        {
                            url: "/m/one/c2",
                            title: "Ch 2",
                            chapter_number: 2.5,
                            release_time: "2024-05-02T00:00:00Z"
                        }
                    ]
                });
            },
            pageList: function(url) {
                return Promise.resolve([
                    "https://x/p1.jpg",
                    { imageUrl: "https://x/p2.jpg", referer: "https://x/ch" },
                    { image: "https://x/p3.jpg" }
                ]);
            }
        };
    """.trimIndent()

    @Test
    fun mangaPopularParsesWrappedPage() = runBlocking {
        val source = mangaSource(mangaPlugin)
        try {
            val page = source.getPopularManga(1)
            assertTrue(page.hasNextPage)
            assertEquals(2, page.mangas.size)
            val first = page.mangas[0]
            assertEquals("/m/one", first.url)
            assertEquals("One", first.title)
            assertEquals("https://x/c1.jpg", first.thumbnail_url)
            assertEquals("Action, Drama", first.genre)
            assertEquals(SManga.ONGOING, first.status)
            assertEquals("Auth", first.author)
            val second = page.mangas[1]
            assertEquals("Two", second.title)
            assertEquals("Romance", second.genre)
            assertEquals(SManga.COMPLETED, second.status)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun mangaSearchParsesRawArray() = runBlocking {
        val source = mangaSource(mangaPlugin)
        try {
            val page = source.getSearchManga(1, "abc", source.getFilterList())
            assertEquals(1, page.mangas.size)
            assertEquals("/m/q-abc", page.mangas[0].url)
            assertEquals("Q abc", page.mangas[0].title)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun mangaLatestFallsBackToPopularWhenMissing() = runBlocking {
        val source = mangaSource(mangaPlugin)
        try {
            // Plugin has no latestManga — the popular listing doubles.
            val page = source.getLatestUpdates(1)
            assertEquals(2, page.mangas.size)
            assertEquals("/m/one", page.mangas[0].url)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun mangaDetailsAndChaptersParse() = runBlocking {
        val source = mangaSource(mangaPlugin)
        try {
            // Listing objects always carry url+title — copy() reads both.
            val stub = SManga.create().apply { url = "/m/one"; title = "One" }
            val details = source.getMangaDetails(stub)
            assertEquals("Parsed /m/one", details.title)
            assertEquals("the summary", details.description)
            assertEquals("Action, Drama", details.genre)
            assertEquals(SManga.ON_HIATUS, details.status)
            assertEquals("https://x/pd.jpg", details.thumbnail_url)
            assertEquals("Art", details.artist)
            assertTrue(details.initialized)

            val chapters = source.getChapterList(stub)
            assertEquals(2, chapters.size)
            assertEquals("/m/one/c1", chapters[0].url)
            assertEquals("Ch 1", chapters[0].name)
            assertEquals(1f, chapters[0].chapter_number)
            assertTrue(chapters[0].date_upload > 0)
            assertEquals("grp", chapters[0].scanlator)
            assertEquals(2.5f, chapters[1].chapter_number)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun mangaPageListParsesStringAndObjectForms() = runBlocking {
        val source = mangaSource(mangaPlugin)
        try {
            val chapter = SChapter.create().apply { url = "/m/one/c1" }
            val pages = source.getPageList(chapter)
            assertEquals(3, pages.size)
            assertEquals("https://x/p1.jpg", pages[0].imageUrl)
            assertEquals("https://x/p2.jpg", pages[1].imageUrl)
            assertEquals("https://x/ch", pages[1].url)
            assertEquals("https://x/p3.jpg", pages[2].imageUrl)
            assertEquals(0, pages[0].index)
            assertEquals(2, pages[2].index)
        } finally {
            source.shutdown()
        }
    }

    // ---------------------------------------------------------------
    // Anime
    // ---------------------------------------------------------------

    private val animePlugin = """
        module.exports = {
            popularAnime: function(page, options) {
                return Promise.resolve({
                    animes: [
                        { path: "/a/one", name: "One", cover: "https://x/a1.jpg" }
                    ],
                    hasNextPage: false
                });
            },
            parseAnime: function(url) {
                return Promise.resolve({
                    name: "Anime " + url,
                    genres: ["Action", "Sci-Fi"],
                    background: "https://x/bg.jpg",
                    status: "completed",
                    episodes: [
                        {
                            path: "/a/one/e1",
                            name: "Ep 1",
                            episodeNumber: 1,
                            releaseTime: "2024-05-01",
                            filler: true,
                            summary: "ep summary",
                            preview: "https://x/prev.jpg"
                        },
                        {
                            url: "/a/one/e2",
                            title: "Ep 2",
                            episode_number: 2
                        }
                    ],
                    seasons: [
                        { path: "/a/one/s1", name: "Season 1", seasonNumber: 1 }
                    ]
                });
            },
            hosterList: function(url) {
                return Promise.resolve([
                    {
                        url: "https://h/a",
                        name: "StreamA",
                        videos: [
                            { videoUrl: "https://v/a1.mp4", title: "1080p" }
                        ]
                    },
                    {
                        url: "https://h/b",
                        name: "StreamB",
                        internalData: url + "?host=b"
                    }
                ]);
            },
            videoList: function(target) {
                return Promise.resolve([
                    { videoUrl: "https://v/x.mp4", title: "720p", resolution: 720 },
                    "https://v/raw.mp4"
                ]);
            }
        };
    """.trimIndent()

    @Test
    fun animePopularParsesWrappedPage() = runBlocking {
        val source = animeSource(animePlugin)
        try {
            val page = source.getPopularAnime(1)
            assertFalse(page.hasNextPage)
            assertEquals(1, page.animes.size)
            assertEquals("/a/one", page.animes[0].url)
            assertEquals("One", page.animes[0].title)
            assertEquals("https://x/a1.jpg", page.animes[0].thumbnail_url)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun animeDetailsEpisodesAndSeasonsParse() = runBlocking {
        val source = animeSource(animePlugin)
        try {
            val stub = eu.kanade.tachiyomi.animesource.model.SAnime.create()
                .apply { url = "/a/one"; title = "One" }
            val details = source.getAnimeDetails(stub)
            assertEquals("Anime /a/one", details.title)
            assertEquals("Action, Sci-Fi", details.genre)
            assertEquals("https://x/bg.jpg", details.background_url)
            assertTrue(details.initialized)

            val episodes = source.getEpisodeList(stub)
            assertEquals(2, episodes.size)
            assertEquals("/a/one/e1", episodes[0].url)
            assertEquals("Ep 1", episodes[0].name)
            assertEquals(1f, episodes[0].episode_number)
            assertTrue(episodes[0].fillermark)
            assertEquals("ep summary", episodes[0].summary)
            assertEquals("https://x/prev.jpg", episodes[0].preview_url)
            assertEquals(2f, episodes[1].episode_number)

            val seasons = source.getSeasonList(stub)
            assertEquals(1, seasons.size)
            assertEquals("/a/one/s1", seasons[0].url)
            assertEquals("Season 1", seasons[0].title)
            assertEquals(1.0, seasons[0].season_number)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun animeHostersEmbedOrLazyResolve() = runBlocking {
        val source = animeSource(animePlugin)
        try {
            val episode = SEpisode.create().apply { url = "/a/one/e1" }
            val hosters = source.getHosterList(episode)
            assertEquals(2, hosters.size)

            val embedded = hosters[0]
            assertEquals("StreamA", embedded.hosterName)
            assertFalse(embedded.lazy)
            assertNotNull(embedded.videoList)
            assertEquals("https://v/a1.mp4", embedded.videoList!![0].videoUrl)
            assertEquals("1080p", embedded.videoList!![0].videoTitle)

            val lazy = hosters[1]
            assertEquals("StreamB", lazy.hosterName)
            assertTrue(lazy.lazy)
            assertNull(lazy.videoList)
            assertEquals("/a/one/e1?host=b", lazy.internalData)

            val videos = source.getVideoList(lazy)
            assertEquals(2, videos.size)
            assertEquals("https://v/x.mp4", videos[0].videoUrl)
            assertEquals("720p", videos[0].videoTitle)
            assertEquals(720, videos[0].resolution)
            assertEquals("https://v/raw.mp4", videos[1].videoUrl)
        } finally {
            source.shutdown()
        }
    }

    @Test
    fun animeNoHosterPluginFallsBackToSentinel() = runBlocking {
        val noHosterPlugin = """
            module.exports = {
                videoList: function(url) {
                    return Promise.resolve([{ url: "https://v/direct.mp4" }]);
                }
            };
        """.trimIndent()
        val source = animeSource(noHosterPlugin)
        try {
            val episode = SEpisode.create().apply { url = "/a/direct/e1" }
            val hosters = source.getHosterList(episode)
            assertEquals(1, hosters.size)
            assertEquals(
                eu.kanade.tachiyomi.animesource.model.Hoster.NO_HOSTER_LIST,
                hosters[0].hosterName,
            )
            assertTrue(hosters[0].lazy)
            assertEquals("/a/direct/e1", hosters[0].internalData)

            val videos = source.getVideoList(episode)
            assertEquals(1, videos.size)
            assertEquals("https://v/direct.mp4", videos[0].videoUrl)
        } finally {
            source.shutdown()
        }
    }
}
