package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.CanonicalWorkId
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.Confidence
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository

class CanonicalEntryUnifierTest {

    private val mangaRepository = mockk<MangaRepository>()
    private val novelRepository = mockk<NovelRepository>()
    private val workIdResolver = mockk<WorkIdResolver>()
    private val chapterRepository = mockk<ChapterRepository>()
    private val novelChapterRepository = mockk<NovelChapterRepository>()
    private val unifier = CanonicalEntryUnifier(
        mangaRepository,
        novelRepository,
        workIdResolver,
        chapterRepository,
        novelChapterRepository,
    )

    private fun extensionManga(id: Long = 7L) = Manga.create().copy(
        id = id,
        url = "/manga/solo-leveling",
        title = "Solo Leveling",
        source = 42L,
    )

    private fun canonicalManga(id: Long = 900L) = Manga.create().copy(
        id = id,
        url = "${CanonicalCatalog.MANGA_URL_PREFIX}mk:mb:333",
        title = "Solo Leveling",
        source = CanonicalCatalog.CANONICAL_SOURCE_ID,
        initialized = true,
    )

    @Test
    fun `already-canonical manga never redirects`() = runBlocking {
        val manga = extensionManga().copy(url = "${CanonicalCatalog.MANGA_URL_PREFIX}mk:mb:1")
        assertNull(unifier.resolve(manga))
        coVerify(exactly = 0) { workIdResolver.resolve(any()) }
    }

    @Test
    fun `canonical source id never redirects`() = runBlocking {
        val manga = extensionManga().copy(source = CanonicalCatalog.CANONICAL_SOURCE_ID)
        assertNull(unifier.resolve(manga))
        coVerify(exactly = 0) { workIdResolver.resolve(any()) }
    }

    @Test
    fun `unmapped entry returns null`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } returns null
        assertNull(unifier.resolve(extensionManga()))
        coVerify(exactly = 0) { mangaRepository.getMangaByUrlAndSourceId(any(), any()) }
    }

    @Test
    fun `resolver offline returns null instead of throwing`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } throws RuntimeException("offline")
        assertNull(unifier.resolve(extensionManga()))
    }

    @Test
    fun `local mint never redirects`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:local:abc", "manga", Confidence.LOCAL_MINT)
        assertNull(unifier.resolve(extensionManga()))
        coVerify(exactly = 0) { mangaRepository.getMangaByUrlAndSourceId(any(), any()) }
    }

    @Test
    fun `ambiguous title match never redirects`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:mb:1", "manga", Confidence.TITLE, ambiguous = true)
        assertNull(unifier.resolve(extensionManga()))
        coVerify(exactly = 0) { mangaRepository.getMangaByUrlAndSourceId(any(), any()) }
    }

    @Test
    fun `mapped entry with no canonical row returns null`() = runBlocking {
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:mb:333", "manga", Confidence.EXTERNAL_ID)
        coEvery { mangaRepository.getMangaByUrlAndSourceId(any(), any()) } returns null
        assertNull(unifier.resolve(extensionManga()))
    }

    @Test
    fun `mapped entry redirects to canonical row`() = runBlocking {
        val canonical = canonicalManga()
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:mb:333", "manga", Confidence.EXTERNAL_ID)
        coEvery {
            mangaRepository.getMangaByUrlAndSourceId(
                "${CanonicalCatalog.MANGA_URL_PREFIX}mk:mb:333",
                CanonicalCatalog.CANONICAL_SOURCE_ID,
            )
        } returns canonical
        coEvery { chapterRepository.getChapterByMangaId(canonical.id) } returns
            listOf(Chapter.create())
        assertEquals(canonical, unifier.resolve(extensionManga()))
    }

    @Test
    fun `chapter-less canonical stub never redirects`() = runBlocking {
        val canonical = canonicalManga()
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:mb:333", "manga", Confidence.EXTERNAL_ID)
        coEvery { mangaRepository.getMangaByUrlAndSourceId(any(), any()) } returns canonical
        coEvery { chapterRepository.getChapterByMangaId(canonical.id) } returns emptyList()
        assertNull(unifier.resolve(extensionManga()))
    }

    @Test
    fun `favorite entry never redirects`() = runBlocking {
        val manga = extensionManga().copy(favorite = true)
        assertNull(unifier.resolve(manga))
        coVerify(exactly = 0) { workIdResolver.resolve(any()) }
    }

    @Test
    fun `chapter-less novel canonical stub never redirects`() = runBlocking {
        val novel = Novel.create().copy(
            id = 3L,
            url = "/book/dune",
            title = "Dune",
            source = 6400L,
        )
        val canonicalNovel = Novel.create().copy(
            id = 77L,
            url = "${CanonicalCatalog.NOVEL_URL_PREFIX}mk:ol:OL1W",
            source = CanonicalCatalog.CANONICAL_SOURCE_ID,
        )
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:ol:OL1W", "books", Confidence.EXTERNAL_ID)
        coEvery { novelRepository.getNovelByUrlAndSourceId(any(), any()) } returns canonicalNovel
        coEvery {
            novelChapterRepository.getNovelChaptersByNovelId(canonicalNovel.id)
        } returns emptyList()
        assertNull(unifier.resolve(novel))
    }

    @Test
    fun `novel resolution carries isbn from genre`() = runBlocking {
        val novel = Novel.create().copy(
            id = 3L,
            url = "/book/dune",
            title = "Dune",
            source = 6400L,
            genre = listOf("isbn:9780441172719", "en"),
        )
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:ol:OL1W", "books", Confidence.EXTERNAL_ID)
        coEvery { novelRepository.getNovelByUrlAndSourceId(any(), any()) } returns null

        assertNull(unifier.resolve(novel))
        coVerify {
            workIdResolver.resolve(match { it.isbn == "9780441172719" && it.medium == "books" })
        }
    }

    @Test
    fun `cached verdict skips resolver on second call`() = runBlocking {
        val canonical = canonicalManga()
        coEvery { workIdResolver.resolve(any()) } returns
            CanonicalWorkId("mk:mb:333", "manga", Confidence.EXTERNAL_ID)
        coEvery { mangaRepository.getMangaByUrlAndSourceId(any(), any()) } returns canonical
        coEvery { chapterRepository.getChapterByMangaId(canonical.id) } returns
            listOf(Chapter.create())

        val manga = extensionManga()
        assertEquals(canonical, unifier.resolve(manga))
        assertEquals(canonical, unifier.resolve(manga))
        coVerify(exactly = 1) { workIdResolver.resolve(any()) }
    }
}
