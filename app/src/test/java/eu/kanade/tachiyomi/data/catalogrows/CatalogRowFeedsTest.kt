package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.entries.shows.interactor.GetLibraryAnime
import tachiyomi.domain.entries.shows.model.AnimeCover
import tachiyomi.domain.entries.shows.repository.AnimeRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.shows.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Phase 18-02 — feed-arm coverage for the facets and dispatch the
 * resolver routes to [ExtensionCatalogFeed] and [LibraryCatalogFeed].
 * The network-backed arms (TMDB/Cinemeta/canonical) can't run in a JVM
 * test — their mapping logic is asserted through the store codec tests
 * and manual verification.
 */
class CatalogRowFeedsTest {

    // ------------------------------------------------------- extension

    private fun mangaSpec(
        kind: ExtKind = ExtKind.POPULAR,
        genreNames: List<String> = emptyList(),
    ) = CatalogRowSpec(
        id = "r1",
        medium = WorkIdResolver.MEDIUM_MANGA,
        feed = Feed.Extension("pkg.test", SOURCE_ID, kind),
        genreNames = genreNames,
        order = 0,
    )

    private fun extensionFeed(mangaSource: CatalogueSource?): ExtensionCatalogFeed {
        val mangaManager = mockk<MangaSourceManager> {
            every { get(SOURCE_ID) } returns mangaSource
        }
        return ExtensionCatalogFeed(mangaManager, mockk(), mockk())
    }

    @Test
    fun `extension popular maps page one to ExtensionEntry targets`() = runBlocking {
        val source = mockk<CatalogueSource> {
            every { name } returns "TestSrc"
            every { supportsLatest } returns true
            coEvery { getPopularManga(1) } returns MangasPage(
                listOf(
                    SManga.create().apply {
                        title = "Alpha"
                        url = "/manga/alpha"
                        thumbnail_url = "https://x/a.jpg"
                    },
                    SManga.create().apply {
                        title = "Beta"
                        url = "/manga/beta"
                    },
                ),
                false,
            )
        }
        val items = extensionFeed(source).fetch(mangaSpec(), Feed.Extension("pkg.test", SOURCE_ID, ExtKind.POPULAR))
        assertEquals(listOf("Alpha", "Beta"), items.map { it.title })
        items.forEach { item ->
            val target = assertInstanceOf(RowTarget.ExtensionEntry::class.java, item.target)
            assertEquals(SOURCE_ID, target.sourceId)
            assertEquals(WorkIdResolver.MEDIUM_MANGA, target.medium)
            assertInstanceOf(MangaCover::class.java, item.coverUrl)
            // Listing rows carry no per-item count — the subtitle is
            // null rather than repeating the source name on every card.
            assertNull(item.subtitle)
        }
    }

    @Test
    fun `extension latest calls getLatestUpdates`() = runBlocking {
        var latestCalled = false
        val source = mockk<CatalogueSource> {
            every { name } returns "TestSrc"
            every { supportsLatest } returns true
            coEvery { getLatestUpdates(1) } answers {
                latestCalled = true
                MangasPage(
                    listOf(
                        SManga.create().apply {
                            title = "New"
                            url = "/n"
                        },
                    ),
                    false,
                )
            }
        }
        extensionFeed(source).fetch(mangaSpec(ExtKind.LATEST), Feed.Extension("pkg.test", SOURCE_ID, ExtKind.LATEST))
        assertTrue(latestCalled)
    }

    @Test
    fun `latest on unsupported source throws FeedNotImplemented`() {
        val source = mockk<CatalogueSource> {
            every { name } returns "TestSrc"
            every { supportsLatest } returns false
        }
        assertThrows(FeedNotImplementedException::class.java) {
            runBlocking {
                extensionFeed(source).fetch(
                    mangaSpec(ExtKind.LATEST),
                    Feed.Extension("pkg.test", SOURCE_ID, ExtKind.LATEST),
                )
            }
        }
    }

    @Test
    fun `stub source surfaces ExtensionUnavailable`() {
        // The stub a manager returns for an uninstalled extension is a
        // MangaSource, not a CatalogueSource — the cast must fail.
        val mangaManager = mockk<MangaSourceManager> {
            every { get(SOURCE_ID) } returns StubMangaSource(SOURCE_ID, "en", "TestSrc")
        }
        assertThrows(ExtensionUnavailableException::class.java) {
            runBlocking {
                ExtensionCatalogFeed(mangaManager, mockk(), mockk())
                    .fetch(mangaSpec(), Feed.Extension("pkg.test", SOURCE_ID, ExtKind.POPULAR))
            }
        }
    }

    @Test
    fun `unknown source id surfaces ExtensionUnavailable`() {
        assertThrows(ExtensionUnavailableException::class.java) {
            runBlocking {
                extensionFeed(null).fetch(mangaSpec(), Feed.Extension("pkg.test", SOURCE_ID, ExtKind.POPULAR))
            }
        }
    }

    @Test
    fun `extension genreNames filter matches csv genres`() = runBlocking {
        val source = mockk<CatalogueSource> {
            every { name } returns "TestSrc"
            every { supportsLatest } returns true
            every { getFilterList() } returns FilterList(emptyList())
            coEvery { getPopularManga(1) } returns MangasPage(
                listOf(
                    SManga.create().apply {
                        title = "Action One"
                        url = "/a"
                        genre = "Action, Drama"
                    },
                    SManga.create().apply {
                        title = "Cooking"
                        url = "/c"
                        genre = "Comedy, Slice of Life"
                    },
                ),
                false,
            )
        }
        val items = extensionFeed(source).fetch(
            mangaSpec(genreNames = listOf("action")),
            Feed.Extension("pkg.test", SOURCE_ID, ExtKind.POPULAR),
        )
        assertEquals(listOf("Action One"), items.map { it.title })
    }

    @Test
    fun `webtoon kind drives a filtered search with the type filter applied`() = runBlocking {
        // The Weeb Central regression: a "Webtoons" spec must narrow the
        // query itself, not post-filter genres. The source's TriState
        // type group gets INCLUDE set and the fetch routes through
        // getSearchManga — an unfiltered popular page is never served.
        val webtoon = TestTriState("Webtoon")
        val filters = FilterList(
            listOf(
                TestGroup(
                    "Type",
                    listOf(TestTriState("Manga"), webtoon, TestTriState("Comic")),
                ),
            ),
        )
        var searched = false
        val source = mockk<CatalogueSource> {
            every { name } returns "WeebTest"
            every { supportsLatest } returns true
            every { getFilterList() } returns filters
            coEvery { getSearchManga(1, "", any()) } answers {
                searched = true
                MangasPage(
                    listOf(
                        SManga.create().apply {
                            title = "WT"
                            url = "/w"
                        },
                    ),
                    false,
                )
            }
        }
        val feed = Feed.Extension(
            "pkg.test",
            SOURCE_ID,
            ExtKind.POPULAR,
            kindsCsv = "manhwa,manhua,webtoon,oel,other",
        )
        val items = extensionFeed(source).fetch(mangaSpec(), feed)
        assertTrue(searched)
        assertEquals(Filter.TriState.STATE_INCLUDE, webtoon.state)
        assertEquals(listOf("WT"), items.map { it.title })
    }

    @Test
    fun `narrowing kind a source cannot express throws FeedNotImplemented`() {
        val source = mockk<CatalogueSource> {
            every { name } returns "NoTypeSrc"
            every { supportsLatest } returns true
            every { getFilterList() } returns FilterList(emptyList())
        }
        val feed = Feed.Extension(
            "pkg.test",
            SOURCE_ID,
            ExtKind.POPULAR,
            kindsCsv = "webtoon",
        )
        assertThrows(FeedNotImplementedException::class.java) {
            runBlocking { extensionFeed(source).fetch(mangaSpec(), feed) }
        }
    }

    @Test
    fun `genre picks map onto the source's own genre group`() = runBlocking {
        val action = TestTriState("Action")
        val filters = FilterList(
            listOf(TestGroup("Genres", listOf(action, TestTriState("Drama")))),
        )
        var searched = false
        val source = mockk<CatalogueSource> {
            every { name } returns "TestSrc"
            every { supportsLatest } returns true
            every { getFilterList() } returns filters
            coEvery { getSearchManga(1, "", any()) } answers {
                searched = true
                MangasPage(
                    listOf(
                        SManga.create().apply {
                            title = "A"
                            url = "/a"
                        },
                    ),
                    false,
                )
            }
        }
        extensionFeed(source).fetch(
            mangaSpec(genreNames = listOf("action")),
            Feed.Extension("pkg.test", SOURCE_ID, ExtKind.POPULAR),
        )
        assertTrue(searched)
        assertEquals(Filter.TriState.STATE_INCLUDE, action.state)
    }

    @Test
    fun `baseline manga kind soft-applies a type filter when present`() = runBlocking {
        // A "Manga" row on a mixed source (manga+webtoons) narrows to
        // manga-only via the source's type Select — the symmetric half
        // of the Weeb Central fix.
        val source = mockk<CatalogueSource> {
            every { name } returns "MixedSrc"
            every { supportsLatest } returns true
            every { getFilterList() } returns FilterList(
                listOf(TestSelect("Type", arrayOf("All", "Manga", "Webtoon"))),
            )
            coEvery { getSearchManga(1, "", any()) } answers {
                MangasPage(
                    listOf(
                        SManga.create().apply {
                            title = "M"
                            url = "/m"
                        },
                    ),
                    false,
                )
            }
        }
        val feed = Feed.Extension(
            "pkg.test",
            SOURCE_ID,
            ExtKind.POPULAR,
            kindsCsv = "manga,comic",
            kindStrict = false,
        )
        val items = extensionFeed(source).fetch(mangaSpec(), feed)
        val select = source.getFilterList()[0] as TestSelect
        assertEquals(1, select.state) // "Manga"
        assertEquals(listOf("M"), items.map { it.title })
    }

    @Test
    fun `baseline manga kind on a source without type vocab stays unfiltered`() = runBlocking {
        // Same row on a single-kind source with no filter vocabulary —
        // soft kinds degrade to plain popular instead of failing.
        var popularCalled = false
        val source = mockk<CatalogueSource> {
            every { name } returns "PlainSrc"
            every { supportsLatest } returns true
            every { getFilterList() } returns FilterList(emptyList())
            coEvery { getPopularManga(1) } answers {
                popularCalled = true
                MangasPage(
                    listOf(
                        SManga.create().apply {
                            title = "P"
                            url = "/p"
                        },
                    ),
                    false,
                )
            }
        }
        val feed = Feed.Extension(
            "pkg.test",
            SOURCE_ID,
            ExtKind.POPULAR,
            kindsCsv = "manga,comic",
            kindStrict = false,
        )
        val items = extensionFeed(source).fetch(mangaSpec(), feed)
        assertTrue(popularCalled)
        assertEquals(listOf("P"), items.map { it.title })
    }

    private class TestTriState(name: String) : Filter.TriState(name)
    private class TestSelect(name: String, values: Array<String>) :
        Filter.Select<String>(name, values)
    private class TestGroup(name: String, state: List<Filter<*>>) :
        Filter.Group<Filter<*>>(name, state)

    @Test
    fun `shows medium routes to the anime source manager`() = runBlocking {
        var animeCalled = false
        val animeSource = mockk<AnimeCatalogueSource> {
            every { name } returns "AnimeSrc"
            every { supportsLatest } returns true
            coEvery { getPopularAnime(1) } answers {
                animeCalled = true
                AnimesPage(
                    listOf(
                        SAnime.create().apply {
                            title = "Show"
                            url = "/s"
                        },
                    ),
                    false,
                )
            }
        }
        val feed = ExtensionCatalogFeed(
            mockk(),
            mockk<AnimeSourceManager> { every { get(SOURCE_ID) } returns animeSource },
            mockk(),
        )
        val spec = CatalogRowSpec(
            id = "r1",
            medium = WorkIdResolver.MEDIUM_SHOWS,
            feed = Feed.Extension("pkg.a", SOURCE_ID, ExtKind.POPULAR),
            order = 0,
        )
        val items = feed.fetch(spec, spec.feed as Feed.Extension)
        assertTrue(animeCalled)
        val target = assertInstanceOf(RowTarget.ExtensionEntry::class.java, items.single().target)
        assertEquals(WorkIdResolver.MEDIUM_SHOWS, target.medium)
        assertInstanceOf(AnimeCover::class.java, items.single().coverUrl)
    }

    @Test
    fun `books medium routes to the novel source manager`() = runBlocking {
        val novelSource = mockk<NovelCatalogueSource> {
            every { name } returns "NovelSrc"
            every { supportsLatest } returns true
            coEvery { getPopularNovels(1) } returns NovelsPage(
                listOf(
                    SNovel.create().apply {
                        title = "Book"
                        url = "/b"
                    },
                ),
                false,
            )
        }
        val feed = ExtensionCatalogFeed(
            mockk(),
            mockk(),
            mockk<NovelSourceManager> { every { get(SOURCE_ID) } returns novelSource },
        )
        val spec = CatalogRowSpec(
            id = "r1",
            medium = WorkIdResolver.MEDIUM_BOOKS,
            feed = Feed.Extension("pkg.n", SOURCE_ID, ExtKind.POPULAR),
            order = 0,
        )
        val items = feed.fetch(spec, spec.feed as Feed.Extension)
        val target = assertInstanceOf(RowTarget.ExtensionEntry::class.java, items.single().target)
        assertEquals(WorkIdResolver.MEDIUM_BOOKS, target.medium)
    }

    // --------------------------------------------------------- library

    private fun libraryFeed(
        manga: List<LibraryManga> = emptyList(),
        anime: List<LibraryAnime> = emptyList(),
        novels: List<LibraryNovel> = emptyList(),
    ) = LibraryCatalogFeed(
        GetLibraryManga(mockk<MangaRepository> { coEvery { getLibraryManga() } returns manga }),
        GetLibraryAnime(mockk<AnimeRepository> { coEvery { getLibraryAnime() } returns anime }),
        GetLibraryNovels(mockk<NovelRepository> { coEvery { getLibraryNovels() } returns novels }),
    )

    private fun libManga(id: Long, title: String, added: Long, lastRead: Long, genre: List<String>? = null) =
        LibraryManga(
            manga = Manga.create().copy(
                id = id,
                title = title,
                dateAdded = added,
                genre = genre,
            ),
            collection = 0L,
            totalChapters = 0L,
            readCount = 0L,
            bookmarkCount = 0L,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = lastRead,
        )

    private fun libSpec(sort: String = "default", genreNames: List<String> = emptyList()) =
        CatalogRowSpec(
            id = "r1",
            medium = WorkIdResolver.MEDIUM_MANGA,
            feed = Feed.Library,
            genreNames = genreNames,
            sort = sort,
            order = 0,
        )

    @Test
    fun `library recently added sorts by dateAdded desc`() = runBlocking {
        val feed = libraryFeed(
            manga = listOf(
                libManga(1, "Old", added = 100, lastRead = 0),
                libManga(2, "New", added = 300, lastRead = 0),
                libManga(3, "Mid", added = 200, lastRead = 0),
            ),
        )
        val items = feed.fetch(libSpec())
        assertEquals(listOf("New", "Mid", "Old"), items.map { it.title })
        items.forEach {
            assertInstanceOf(RowTarget.LibraryEntry::class.java, it.target)
            assertInstanceOf(MangaCover::class.java, it.coverUrl)
        }
    }

    @Test
    fun `library title sort is alphabetical`() = runBlocking {
        val feed = libraryFeed(
            manga = listOf(
                libManga(1, "Zeta", added = 300, lastRead = 0),
                libManga(2, "Alpha", added = 100, lastRead = 0),
            ),
        )
        val items = feed.fetch(libSpec(sort = "TITLE"))
        assertEquals(listOf("Alpha", "Zeta"), items.map { it.title })
    }

    @Test
    fun `library last read sorts by lastRead desc`() = runBlocking {
        val feed = libraryFeed(
            manga = listOf(
                libManga(1, "Stale", added = 300, lastRead = 5),
                libManga(2, "Fresh", added = 100, lastRead = 50),
            ),
        )
        val items = feed.fetch(libSpec(sort = "LAST_READ"))
        assertEquals(listOf("Fresh", "Stale"), items.map { it.title })
    }

    @Test
    fun `library sort falls back to recently added on unknown names`() = runBlocking {
        val feed = libraryFeed(
            manga = listOf(
                libManga(1, "Old", added = 100, lastRead = 0),
                libManga(2, "New", added = 300, lastRead = 0),
            ),
        )
        val items = feed.fetch(libSpec(sort = "garbage"))
        assertEquals(listOf("New", "Old"), items.map { it.title })
    }

    @Test
    fun `library genreNames filter contains-matches`() = runBlocking {
        val feed = libraryFeed(
            manga = listOf(
                libManga(1, "Shonen", added = 300, lastRead = 0, genre = listOf("Action", "Shounen")),
                libManga(2, "Cooking", added = 200, lastRead = 0, genre = listOf("Comedy")),
            ),
        )
        val items = feed.fetch(libSpec(genreNames = listOf("act")))
        assertEquals(listOf("Shonen"), items.map { it.title })
    }

    @Test
    fun `library rows cap at twenty items`() = runBlocking {
        val feed = libraryFeed(
            manga = (1L..30L).map { libManga(it, "M$it", added = it, lastRead = 0) },
        )
        assertEquals(20, feed.fetch(libSpec()).size)
    }

    // --------------------------------------------------------- helpers

    @Test
    fun `matchesGenreNames semantics`() {
        assertTrue(matchesGenreNames(emptyList(), null))
        assertTrue(matchesGenreNames(listOf("action"), "Action, Drama"))
        assertTrue(matchesGenreNames(listOf("ACT"), "action"))
        assertTrue(!matchesGenreNames(listOf("horror"), "Action, Drama"))
        assertTrue(!matchesGenreNames(listOf("action"), null))
    }

    @Test
    fun `parseLibrarySort falls back for unknown names`() {
        assertEquals(LibrarySort.TITLE, parseLibrarySort("TITLE"))
        assertEquals(LibrarySort.LAST_READ, parseLibrarySort("LAST_READ"))
        assertEquals(LibrarySort.RECENTLY_ADDED, parseLibrarySort("default"))
        assertEquals(LibrarySort.RECENTLY_ADDED, parseLibrarySort("nonsense"))
    }

    companion object {
        private const val SOURCE_ID = 42L
    }
}
