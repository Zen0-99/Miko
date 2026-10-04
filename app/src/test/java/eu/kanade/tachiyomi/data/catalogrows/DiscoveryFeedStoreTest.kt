package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DiscoveryFeedStoreTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @TempDir
    lateinit var dir: File

    private fun store() = DiscoveryFeedStore(File(dir, "discovery_feeds.json"), json)

    private fun workItem(
        id: String,
        title: String = "Title $id",
        cover: String? = "https://covers/$id.jpg",
    ) = CatalogItem(
        work = WorkRow(
            mikoWorkId = id,
            type = "manga",
            title = title,
            author = "Author",
            year = 2020,
            nsfw = false,
            genres = listOf("Action"),
            coverUrl = cover,
        ),
        coverUrl = cover,
    )

    private fun provisionalItem() = CatalogItem(
        work = WorkRow(
            mikoWorkId = "${EXT_ID_PREFIX}pkg.webtoon:/series/42",
            type = "webtoon",
            title = "Webtoon Row",
            coverUrl = "https://covers/w.jpg",
            externalIds = mapOf(
                EXT_SOURCE_KEY to "777",
                EXT_URL_KEY to "/series/42",
            ),
        ),
        coverUrl = "https://covers/w.jpg",
        mintCandidate = WorkIdResolver.Candidate(
            medium = WorkIdResolver.MEDIUM_MANGA,
            title = "Webtoon Row",
            url = "https://webtoons.com/series/42",
            author = "W",
            webviewUrls = listOf("https://webtoons.com/series/42"),
            isbn = "9781234567890",
            isBook = true,
            kindHint = "webtoon",
        ),
    )

    @Test
    fun `missing file loads empty`() = runBlocking {
        assertTrue(store().load().isEmpty())
    }

    @Test
    fun `feeds round-trip items, cursor, and bindings`() = runBlocking {
        val items = listOf(workItem("mk:mb:1"), workItem("mk:mb:2", cover = null))
        val bound = mapOf("mk:mb:1" to 77L)
        store().save("manga|manga,comic", items, page = 2, hasMore = true, bound = bound)

        val loaded = store().load()
        val feed = loaded.getValue("manga|manga,comic")
        assertEquals(2, feed.page)
        assertTrue(feed.hasMore)
        assertEquals(bound, feed.bound)
        assertEquals(items, feed.items)
        assertTrue(feed.savedAt > 0)
    }

    @Test
    fun `provisional ext rows keep coordinates and mint candidate`() = runBlocking {
        val item = provisionalItem()
        store().save("manga|webtoon:pkg.webtoon", listOf(item), 1, false, emptyMap())

        val loaded = store().load().getValue("manga|webtoon:pkg.webtoon")
        val restored = loaded.items.single()
        assertEquals(item.work, restored.work)
        assertEquals(item.coverUrl, restored.coverUrl)

        val c = restored.mintCandidate!!
        assertEquals(WorkIdResolver.MEDIUM_MANGA, c.medium)
        assertEquals("Webtoon Row", c.title)
        assertEquals("https://webtoons.com/series/42", c.url)
        assertEquals("W", c.author)
        assertEquals(listOf("https://webtoons.com/series/42"), c.webviewUrls)
        assertEquals("9781234567890", c.isbn)
        assertTrue(c.isBook)
        assertEquals("webtoon", c.kindHint)
    }

    @Test
    fun `canonical rows have no mint candidate after reload`() = runBlocking {
        store().save("manga|manga,comic", listOf(workItem("mk:mb:9")), 1, false, emptyMap())
        assertNull(store().load().getValue("manga|manga,comic").items.single().mintCandidate)
    }

    @Test
    fun `save preserves sibling feeds`() = runBlocking {
        store().save("manga|manga,comic", listOf(workItem("mk:mb:1")), 1, true, emptyMap())
        store().save("books|ol", listOf(workItem("mk:ol:OL1W")), 1, false, mapOf("mk:ol:OL1W" to 5L))

        val loaded = store().load()
        assertEquals(setOf("manga|manga,comic", "books|ol"), loaded.keys)
        assertEquals(5L, loaded.getValue("books|ol").bound.getValue("mk:ol:OL1W"))
    }

    @Test
    fun `corrupt file degrades to empty`() = runBlocking {
        File(dir, "discovery_feeds.json").writeText("{not json")
        assertTrue(store().load().isEmpty())
    }
}
