package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanonicalCatalogTest {

    // ---------- URL scheme ----------

    @Test
    fun `manga url round-trips work id`() {
        val url = CanonicalCatalog.MANGA_URL_PREFIX + "mk:mb:123"
        assertEquals("mk:mb:123", CanonicalCatalog.workIdFromMangaUrl(url))
        assertTrue(CanonicalCatalog.isCanonicalUrl(url))
    }

    @Test
    fun `novel url round-trips work id`() {
        val url = CanonicalCatalog.NOVEL_URL_PREFIX + "mk:ol:OL59800W"
        assertEquals("mk:ol:OL59800W", CanonicalCatalog.workIdFromNovelUrl(url))
        assertTrue(CanonicalCatalog.isCanonicalUrl(url))
    }

    @Test
    fun `mediums never mix through urls`() {
        val mangaUrl = CanonicalCatalog.MANGA_URL_PREFIX + "mk:mb:1"
        val novelUrl = CanonicalCatalog.NOVEL_URL_PREFIX + "mk:mb:1"
        assertNull(CanonicalCatalog.workIdFromNovelUrl(mangaUrl))
        assertNull(CanonicalCatalog.workIdFromMangaUrl(novelUrl))
    }

    @Test
    fun `extension urls are not canonical`() {
        assertFalse(CanonicalCatalog.isCanonicalUrl("/manga/some-extension-path"))
        assertFalse(CanonicalCatalog.isCanonicalUrl("https://mangadex.org/title/abc"))
        assertFalse(CanonicalCatalog.isCanonicalUrl("mikox:manga:mk:mb:1"))
    }

    // ---------- medium type filtering ----------

    @Test
    fun `manga medium excludes novels and books`() {
        val types = WorkIdResolver.allowedTypes(WorkIdResolver.MEDIUM_MANGA)
        assertTrue("manga" in types)
        assertTrue("manhwa" in types)
        assertTrue("manhua" in types)
        assertFalse("novel" in types)
        assertFalse("book" in types)
    }

    @Test
    fun `novel medium includes books`() {
        val types = WorkIdResolver.allowedTypes(WorkIdResolver.MEDIUM_BOOKS)
        assertTrue("novel" in types)
        assertTrue("book" in types)
        assertFalse("manga" in types)
        assertFalse("manhwa" in types)
    }

    // ---------- library binding author gate ----------

    @Test
    fun `author name-order flip still fits`() {
        // Weeb Central "KUBO Tite" vs MangaBaka "Tite Kubo" — the Bleach
        // bind miss that hid the in-library badge.
        assertTrue(CanonicalCatalog.authorFits("KUBO Tite", "tite kubo"))
        assertTrue(CanonicalCatalog.authorFits("Tite Kubo", "kubo tite"))
    }

    @Test
    fun `co-author subsets still fit`() {
        // Extension lists full credits, the work row keeps a single name.
        assertTrue(CanonicalCatalog.authorFits("MURATA Yuusuke, ONE", "one"))
        assertTrue(CanonicalCatalog.authorFits("ONE", "murata yuusuke one"))
    }

    @Test
    fun `unrelated authors do not fit`() {
        assertFalse(CanonicalCatalog.authorFits("Eiichiro Oda", "tite kubo"))
        assertFalse(CanonicalCatalog.authorFits("Oda Eiichiro", "kubo tite"))
    }

    @Test
    fun `blank author never blocks a bind`() {
        assertTrue(CanonicalCatalog.authorFits(null, "tite kubo"))
        assertTrue(CanonicalCatalog.authorFits("  ", "tite kubo"))
        assertTrue(CanonicalCatalog.authorFits("Anyone", ""))
    }

    // ---------- search ranking ----------

    @Test
    fun `exact normalized match ranks first`() {
        assertEquals(0, MangaNovelIdBridge.titleRank("solo leveling", "solo leveling"))
        assertEquals(1, MangaNovelIdBridge.titleRank("solo leveling ragnarok", "solo leveling"))
        assertEquals(2, MangaNovelIdBridge.titleRank("the solo leveling", "solo leveling"))
    }

    // ---------- status mapping ----------

    @Test
    fun `manga status maps mangabaka vocabulary`() {
        assertEquals(SManga.COMPLETED.toLong(), CanonicalCatalog.statusOf("completed", manga = true))
        assertEquals(SManga.ONGOING.toLong(), CanonicalCatalog.statusOf("releasing", manga = true))
        assertEquals(SManga.ON_HIATUS.toLong(), CanonicalCatalog.statusOf("hiatus", manga = true))
        assertEquals(SManga.CANCELLED.toLong(), CanonicalCatalog.statusOf("cancelled", manga = true))
        assertEquals(SManga.UNKNOWN.toLong(), CanonicalCatalog.statusOf(null, manga = true))
        assertEquals(SManga.UNKNOWN.toLong(), CanonicalCatalog.statusOf("anomalous", manga = true))
    }

    @Test
    fun `novel status uses novel constants`() {
        assertEquals(SNovel.COMPLETED.toLong(), CanonicalCatalog.statusOf("completed", manga = false))
        assertEquals(SNovel.ONGOING.toLong(), CanonicalCatalog.statusOf("releasing", manga = false))
        assertEquals(SNovel.UNKNOWN.toLong(), CanonicalCatalog.statusOf("?", manga = false))
    }
}
