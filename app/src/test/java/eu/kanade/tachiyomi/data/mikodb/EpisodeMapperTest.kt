package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.mikodb.EpisodeMapper.MappingRow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class EpisodeMapperTest {

    private fun row(
        malId: Long? = null,
        anilistId: Long? = null,
        type: String? = "TV",
        season: String? = "1",
        part: Int? = 1,
        animeEps: String? = null,
        globalEps: String? = null,
    ) = MappingRow(malId, anilistId, type, season, part, animeEps, globalEps)

    // --- Naruto: single TV row, absolute ordering ("a"), 1-220 ---
    private val naruto = listOf(
        row(malId = 20, anilistId = 20, season = "a", animeEps = "1 - 220", globalEps = "1 - 220"),
        row(malId = 1074, anilistId = 1074, type = "OVA", season = "0", animeEps = "1 - 1", globalEps = "1 - 1"),
        row(malId = 7367, anilistId = 7367, type = "SPECIAL", season = "0", animeEps = "1 - 1", globalEps = "1 - 1"),
    )

    @Test
    fun `absolute-ordered show maps S1E5 directly`() {
        val b = EpisodeMapper.bind(naruto, season = 1, episode = 5)!!
        b.row.anilistId shouldBe 20
        b.seasonEpisode shouldBe 5
        b.absoluteEpisode shouldBe 5
        b.entryEpisode shouldBe 5
    }

    @Test
    fun `absolute-ordered show maps late episode`() {
        val b = EpisodeMapper.bind(naruto, season = 1, episode = 220)!!
        b.absoluteEpisode shouldBe 220
    }

    @Test
    fun `episode beyond absolute range returns null`() {
        EpisodeMapper.bind(naruto, season = 1, episode = 300) shouldBe null
    }

    // --- Attack on Titan: split cours ---
    private val aot = listOf(
        row(malId = 16498, anilistId = 16498, season = "1", animeEps = "1 - 25", globalEps = "1 - 25"),
        row(malId = 18397, anilistId = 18397, type = "OVA", season = "0", animeEps = "1 - 8", globalEps = "1 - 8"),
        row(malId = 25777, anilistId = 20958, season = "2", animeEps = "1 - 12", globalEps = "1 - 12"),
        row(malId = 35760, anilistId = 99147, season = "3", part = 1, animeEps = "1 - 12", globalEps = "1 - 12"),
        row(malId = 38524, anilistId = 104578, season = "3", part = 2, animeEps = "1 - 10", globalEps = "13 - 22"),
        row(malId = 40028, anilistId = 110277, season = "4", part = 1, animeEps = "1 - 16", globalEps = "1 - 16"),
        row(malId = 48583, anilistId = 131681, season = "4", part = 2, animeEps = "1 - 12", globalEps = "17 - 28"),
    )

    @Test
    fun `split-cour S3E15 lands in part 2`() {
        val b = EpisodeMapper.bind(aot, season = 3, episode = 15)!!
        b.row.anilistId shouldBe 104578 // Season 3 Part 2 entry
        b.entryEpisode shouldBe 3 // ep 3 within that entry
        b.seasonEpisode shouldBe 15
        b.absoluteEpisode shouldBe 25 + 12 + 15 // 52
    }

    @Test
    fun `S3E1 lands in part 1`() {
        val b = EpisodeMapper.bind(aot, season = 3, episode = 1)!!
        b.row.anilistId shouldBe 99147
        b.entryEpisode shouldBe 1
        b.absoluteEpisode shouldBe 38 // 25 + 12 + 1
    }

    @Test
    fun `S4E28 lands in part 2`() {
        val b = EpisodeMapper.bind(aot, season = 4, episode = 28)!!
        b.row.anilistId shouldBe 131681
        b.entryEpisode shouldBe 12
    }

    @Test
    fun `episode out of season range returns null`() {
        EpisodeMapper.bind(aot, season = 2, episode = 99) shouldBe null
    }

    @Test
    fun `missing season returns null`() {
        EpisodeMapper.bind(aot, season = 9, episode = 1) shouldBe null
    }

    @Test
    fun `empty row set returns null`() {
        EpisodeMapper.bind(emptyList(), 1, 1) shouldBe null
    }

    @Test
    fun `parseRange handles single and ranged forms`() {
        EpisodeMapper.parseRange("1 - 220") shouldBe EpisodeMapper.Range(1, 220)
        EpisodeMapper.parseRange("5") shouldBe EpisodeMapper.Range(5, 5)
        EpisodeMapper.parseRange("") shouldBe null
        EpisodeMapper.parseRange(null) shouldBe null
        EpisodeMapper.parseRange("a - b") shouldBe null
    }
}
