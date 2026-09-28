package eu.kanade.tachiyomi.data.mikodb

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Provider-slug mapping for `links_v2` → `provider_links` (Phase 15-07
 * follow-up). The slugs must match what extension `miko.json` blocks
 * declare — [GoldenSources] joins on them verbatim.
 */
class MangaBakaEnrichTest {

    private fun link(url: String, name: String? = null, type: String = "webplatform") =
        MangaBakaEnrich.LinkDto(url = url, name = name, type = type)

    @Test
    fun `webtoon title_no urls map to webtoons with the id extracted`() {
        val links = MangaBakaEnrich.providerLinksOfLinks(
            listOf(link("https://www.webtoons.com/en/fantasy/lore-olympus/list?title_no=1320")),
        )
        assertEquals(1, links.size)
        assertEquals("webtoons", links[0].provider)
        assertEquals("1320", links[0].externalId)
    }

    @Test
    fun `royalroad fiction urls map to royalroad with the id extracted`() {
        val links = MangaBakaEnrich.providerLinksOfLinks(
            listOf(link("https://www.royalroad.com/fiction/21220/mother-of-learning")),
        )
        assertEquals("royalroad", links[0].provider)
        assertEquals("21220", links[0].externalId)
    }

    @Test
    fun `mangadex title urls map to mangadex with the uuid extracted`() {
        val uuid = "b0b721ff-c388-4486-aa0f-c2a0e5ff33f6"
        val links = MangaBakaEnrich.providerLinksOfLinks(
            listOf(link("https://mangadex.org/title/$uuid/some-title")),
        )
        assertEquals("mangadex", links[0].provider)
        assertEquals(uuid, links[0].externalId)
    }

    @Test
    fun `webnovel host falls back to the slug table`() {
        val links = MangaBakaEnrich.providerLinksOfLinks(
            listOf(link("https://www.webnovel.com/book/22196546206090805", name = "www.webnovel.com")),
        )
        assertEquals("webnovel", links[0].provider)
        assertEquals(null, links[0].externalId)
    }

    @Test
    fun `kakao and naver subdomains resolve to their provider`() {
        val links = MangaBakaEnrich.providerLinksOfLinks(
            listOf(
                link("https://webtoon.kakao.com/content/foo/1234"),
                link("https://comic.naver.com/webtoon/list?titleId=732955"),
            ),
        )
        assertEquals("kakao", links[0].provider)
        // titleId= regex wins before the host table — naver, id extracted.
        assertEquals("naver", links[1].provider)
        assertEquals("732955", links[1].externalId)
    }

    @Test
    fun `non-webplatform links are skipped`() {
        val links = MangaBakaEnrich.providerLinksOfLinks(
            listOf(link("https://twitter.com/foo", type = "social")),
        )
        assertEquals(0, links.size)
    }

    @Test
    fun `unknown hosts fall back to the second-level domain label`() {
        assertEquals("example", MangaBakaEnrich.hostProviderOf("https://comic.example.com/x", null))
        assertEquals("example", MangaBakaEnrich.hostProviderOf("https://example.com/x", null))
    }
}
