package eu.kanade.presentation.achievement.components

import com.woowla.compose.icon.collections.tabler.Tabler
import com.woowla.compose.icon.collections.tabler.tabler.Outline
import com.woowla.compose.icon.collections.tabler.tabler.outline.Anchor
import com.woowla.compose.icon.collections.tabler.tabler.outline.Award
import com.woowla.compose.icon.collections.tabler.tabler.outline.Book2
import com.woowla.compose.icon.collections.tabler.tabler.outline.Books
import com.woowla.compose.icon.collections.tabler.tabler.outline.Bolt
import com.woowla.compose.icon.collections.tabler.tabler.outline.Broadcast
import com.woowla.compose.icon.collections.tabler.tabler.outline.ChartBar
import com.woowla.compose.icon.collections.tabler.tabler.outline.Chess
import com.woowla.compose.icon.collections.tabler.tabler.outline.CircleCheck
import com.woowla.compose.icon.collections.tabler.tabler.outline.ClockHour4
import com.woowla.compose.icon.collections.tabler.tabler.outline.Compass
import com.woowla.compose.icon.collections.tabler.tabler.outline.Crown
import com.woowla.compose.icon.collections.tabler.tabler.outline.DatabaseExport
import com.woowla.compose.icon.collections.tabler.tabler.outline.Door
import com.woowla.compose.icon.collections.tabler.tabler.outline.Download
import com.woowla.compose.icon.collections.tabler.tabler.outline.Filter
import com.woowla.compose.icon.collections.tabler.tabler.outline.Flag
import com.woowla.compose.icon.collections.tabler.tabler.outline.Flame
import com.woowla.compose.icon.collections.tabler.tabler.outline.Ghost
import com.woowla.compose.icon.collections.tabler.tabler.outline.HandClick
import com.woowla.compose.icon.collections.tabler.tabler.outline.Heart
import com.woowla.compose.icon.collections.tabler.tabler.outline.HeartHandshake
import com.woowla.compose.icon.collections.tabler.tabler.outline.Hearts
import com.woowla.compose.icon.collections.tabler.tabler.outline.Hourglass
import com.woowla.compose.icon.collections.tabler.tabler.outline.Map2
import com.woowla.compose.icon.collections.tabler.tabler.outline.MasksTheater
import com.woowla.compose.icon.collections.tabler.tabler.outline.Medal
import com.woowla.compose.icon.collections.tabler.tabler.outline.MilitaryRank
import com.woowla.compose.icon.collections.tabler.tabler.outline.Moon
import com.woowla.compose.icon.collections.tabler.tabler.outline.MoodCry
import com.woowla.compose.icon.collections.tabler.tabler.outline.Notes
import com.woowla.compose.icon.collections.tabler.tabler.outline.Palette
import com.woowla.compose.icon.collections.tabler.tabler.outline.QuestionMark
import com.woowla.compose.icon.collections.tabler.tabler.outline.Rocket
import com.woowla.compose.icon.collections.tabler.tabler.outline.Route
import com.woowla.compose.icon.collections.tabler.tabler.outline.Scale
import com.woowla.compose.icon.collections.tabler.tabler.outline.Search
import com.woowla.compose.icon.collections.tabler.tabler.outline.Settings
import com.woowla.compose.icon.collections.tabler.tabler.outline.Skull
import com.woowla.compose.icon.collections.tabler.tabler.outline.Sparkles
import com.woowla.compose.icon.collections.tabler.tabler.outline.Stack2
import com.woowla.compose.icon.collections.tabler.tabler.outline.Sunrise
import com.woowla.compose.icon.collections.tabler.tabler.outline.Sword
import com.woowla.compose.icon.collections.tabler.tabler.outline.Target
import com.woowla.compose.icon.collections.tabler.tabler.outline.Transfer
import com.woowla.compose.icon.collections.tabler.tabler.outline.TriangleSquareCircle
import com.woowla.compose.icon.collections.tabler.tabler.outline.Trophy
import com.woowla.compose.icon.collections.tabler.tabler.outline.Truck
import com.woowla.compose.icon.collections.tabler.tabler.outline.Users
import com.woowla.compose.icon.collections.tabler.tabler.outline.Video
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementRarity
import tachiyomi.domain.achievement.model.AchievementType

class AchievementIconContentTest {

    private fun achievement(
        id: String = "test",
        type: AchievementType = AchievementType.EVENT,
        category: AchievementCategory = AchievementCategory.BOTH,
        threshold: Int? = null,
        isSecret: Boolean = false,
        tierGroup: String? = null,
        tierLevel: Int? = null,
    ) = Achievement(
        id = id,
        type = type,
        category = category,
        threshold = threshold,
        title = "Test",
        isSecret = isSecret,
        tierGroup = tierGroup,
        tierLevel = tierLevel,
        rarity = AchievementRarity.COMMON,
    )

    // ── Secret resolution ────────────────────────────────────────────────

    @Test
    fun `secret achievement without mapped icon resolves to Secret`() {
        val a = achievement(id = "unknown_secret", isSecret = true, threshold = 1)
        resolveIconContent(a) shouldBe IconContent.Secret
    }

    @Test
    fun `secret achievement with mapped icon resolves to Glyph`() {
        val a = achievement(id = "secret_hall_unlocked", isSecret = true, threshold = 1)
        val content = resolveIconContent(a)
        content.shouldBeInstanceOf<IconContent.Glyph>()
        content.vector shouldBe Tabler.Outline.Door
    }

    // ── Per-achievement icon mapping ─────────────────────────────────────

    @Test
    fun `manga chapter achievements resolve to Book2`() {
        resolveIconContent(achievement(id = "first_chapter", type = AchievementType.EVENT, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Book2)
        resolveIconContent(achievement(id = "read_10_chapters", type = AchievementType.QUANTITY, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.Book2)
        resolveIconContent(achievement(id = "read_1000_chapters", type = AchievementType.QUANTITY, threshold = 1000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Book2)
    }

    @Test
    fun `novel chapter achievements resolve to Notes`() {
        resolveIconContent(achievement(id = "first_novel_chapter", type = AchievementType.EVENT, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Notes)
        resolveIconContent(achievement(id = "read_100_novel_chapters", type = AchievementType.QUANTITY, threshold = 100)) shouldBe
            IconContent.Glyph(Tabler.Outline.Notes)
    }

    @Test
    fun `anime episode achievements resolve to Video`() {
        resolveIconContent(achievement(id = "first_episode", type = AchievementType.EVENT, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Video)
        resolveIconContent(achievement(id = "watch_500_episodes", type = AchievementType.QUANTITY, threshold = 500)) shouldBe
            IconContent.Glyph(Tabler.Outline.Video)
    }

    @Test
    fun `completion achievements resolve to CircleCheck`() {
        resolveIconContent(achievement(id = "complete_1_manga", type = AchievementType.EVENT, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.CircleCheck)
        resolveIconContent(achievement(id = "complete_50_novel", type = AchievementType.QUANTITY, threshold = 50)) shouldBe
            IconContent.Glyph(Tabler.Outline.CircleCheck)
        resolveIconContent(achievement(id = "complete_10_anime", type = AchievementType.QUANTITY, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.CircleCheck)
    }

    @Test
    fun `long-haul achievements resolve to Route`() {
        resolveIconContent(achievement(id = "read_long_manga", type = AchievementType.EVENT, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Route)
        resolveIconContent(achievement(id = "read_long_novel", type = AchievementType.EVENT, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Route)
    }

    @Test
    fun `genre explorer achievements resolve to Compass`() {
        resolveIconContent(achievement(id = "genre_explorer", type = AchievementType.DIVERSITY, threshold = 5)) shouldBe
            IconContent.Glyph(Tabler.Outline.Compass)
        resolveIconContent(achievement(id = "genre_explorer_ultimate", type = AchievementType.DIVERSITY, threshold = 20)) shouldBe
            IconContent.Glyph(Tabler.Outline.Compass)
    }

    @Test
    fun `streak achievements resolve to Flame`() {
        resolveIconContent(achievement(id = "week_warrior", type = AchievementType.STREAK, threshold = 7)) shouldBe
            IconContent.Glyph(Tabler.Outline.Flame)
        resolveIconContent(achievement(id = "yearly_devotee", type = AchievementType.STREAK, threshold = 365)) shouldBe
            IconContent.Glyph(Tabler.Outline.Flame)
    }

    @Test
    fun `library size achievements resolve to Stack2`() {
        resolveIconContent(achievement(id = "library_collector", type = AchievementType.LIBRARY, threshold = 100)) shouldBe
            IconContent.Glyph(Tabler.Outline.Stack2)
        resolveIconContent(achievement(id = "library_god", type = AchievementType.LIBRARY, threshold = 5000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Stack2)
    }

    @Test
    fun `content volume achievements resolve to Trophy`() {
        resolveIconContent(achievement(id = "content_master", type = AchievementType.QUANTITY, threshold = 1000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Trophy)
        resolveIconContent(achievement(id = "content_overlord", type = AchievementType.QUANTITY, threshold = 10000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Trophy)
    }

    @Test
    fun `meta unlock achievements resolve to Award`() {
        resolveIconContent(achievement(id = "master_achiever", type = AchievementType.META, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.Award)
        resolveIconContent(achievement(id = "achievement_completionist", type = AchievementType.META, threshold = 75)) shouldBe
            IconContent.Glyph(Tabler.Outline.Award)
    }

    @Test
    fun `hybrid balance achievements resolve to Scale`() {
        resolveIconContent(achievement(id = "balanced_fan", type = AchievementType.BALANCED, threshold = 50)) shouldBe
            IconContent.Glyph(Tabler.Outline.Scale)
        resolveIconContent(achievement(id = "perfect_balance", type = AchievementType.BALANCED, threshold = 1000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Scale)
    }

    @Test
    fun `trinity achievements resolve to TriangleSquareCircle`() {
        resolveIconContent(achievement(id = "trinity_initiate", type = AchievementType.BALANCED, threshold = 25)) shouldBe
            IconContent.Glyph(Tabler.Outline.TriangleSquareCircle)
        resolveIconContent(achievement(id = "trinity_legend", type = AchievementType.BALANCED, threshold = 1000)) shouldBe
            IconContent.Glyph(Tabler.Outline.TriangleSquareCircle)
        resolveIconContent(achievement(id = "three_realms_collector", type = AchievementType.LIBRARY, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.TriangleSquareCircle)
    }

    @Test
    fun `time-based achievements resolve correctly`() {
        resolveIconContent(achievement(id = "night_owl", type = AchievementType.TIME_BASED, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Moon)
        resolveIconContent(achievement(id = "early_bird", type = AchievementType.TIME_BASED, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Sunrise)
        resolveIconContent(achievement(id = "marathon_reader", type = AchievementType.TIME_BASED, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Hourglass)
    }

    @Test
    fun `feature-based achievements resolve correctly`() {
        resolveIconContent(achievement(id = "download_starter", type = AchievementType.FEATURE_BASED, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.Download)
        resolveIconContent(achievement(id = "search_user", type = AchievementType.FEATURE_BASED, threshold = 25)) shouldBe
            IconContent.Glyph(Tabler.Outline.Search)
        resolveIconContent(achievement(id = "filter_master", type = AchievementType.FEATURE_BASED, threshold = 20)) shouldBe
            IconContent.Glyph(Tabler.Outline.Filter)
        resolveIconContent(achievement(id = "backup_master", type = AchievementType.FEATURE_BASED, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.DatabaseExport)
        resolveIconContent(achievement(id = "settings_explorer", type = AchievementType.FEATURE_BASED, threshold = 15)) shouldBe
            IconContent.Glyph(Tabler.Outline.Settings)
        resolveIconContent(achievement(id = "stats_viewer", type = AchievementType.FEATURE_BASED, threshold = 5)) shouldBe
            IconContent.Glyph(Tabler.Outline.ChartBar)
        resolveIconContent(achievement(id = "theme_changer", type = AchievementType.FEATURE_BASED, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.Palette)
    }

    @Test
    fun `reading immersion achievements resolve to Hourglass`() {
        resolveIconContent(achievement(id = "reading_immersion_bronze", type = AchievementType.FEATURE_BASED, threshold = 60)) shouldBe
            IconContent.Glyph(Tabler.Outline.Hourglass)
        resolveIconContent(achievement(id = "reading_immersion_platinum", type = AchievementType.FEATURE_BASED, threshold = 600)) shouldBe
            IconContent.Glyph(Tabler.Outline.Hourglass)
    }

    @Test
    fun `cross-media champion achievements resolve to Transfer`() {
        resolveIconContent(achievement(id = "cross_media_champion_bronze", type = AchievementType.BALANCED, threshold = 50)) shouldBe
            IconContent.Glyph(Tabler.Outline.Transfer)
        resolveIconContent(achievement(id = "cross_media_champion_gold", type = AchievementType.BALANCED, threshold = 2000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Transfer)
    }

    @Test
    fun `rank achievements resolve to MilitaryRank or Crown`() {
        resolveIconContent(achievement(id = "rank_up_1", type = AchievementType.META, threshold = 100)) shouldBe
            IconContent.Glyph(Tabler.Outline.MilitaryRank)
        resolveIconContent(achievement(id = "rank_up_6", type = AchievementType.META, threshold = 10000)) shouldBe
            IconContent.Glyph(Tabler.Outline.MilitaryRank)
        resolveIconContent(achievement(id = "rank_up_7", type = AchievementType.META, threshold = 25000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Crown)
        resolveIconContent(achievement(id = "rank_up_10", type = AchievementType.META, threshold = 250000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Crown)
    }

    @Test
    fun `genre-specific achievements resolve correctly`() {
        resolveIconContent(achievement(id = "romance_devotee", type = AchievementType.DIVERSITY, threshold = 15)) shouldBe
            IconContent.Glyph(Tabler.Outline.Heart)
        resolveIconContent(achievement(id = "horror_aficionado", type = AchievementType.DIVERSITY, threshold = 15)) shouldBe
            IconContent.Glyph(Tabler.Outline.Ghost)
        resolveIconContent(achievement(id = "isekai_addict", type = AchievementType.DIVERSITY, threshold = 20)) shouldBe
            IconContent.Glyph(Tabler.Outline.Truck)
        resolveIconContent(achievement(id = "slice_of_life_zen", type = AchievementType.DIVERSITY, threshold = 15)) shouldBe
            IconContent.Glyph(Tabler.Outline.Sparkles)
    }

    @Test
    fun `completion achievements resolve to Flag and Target`() {
        resolveIconContent(achievement(id = "the_finisher", type = AchievementType.LIBRARY, threshold = 50)) shouldBe
            IconContent.Glyph(Tabler.Outline.Flag)
        resolveIconContent(achievement(id = "the_closer", type = AchievementType.LIBRARY, threshold = 90)) shouldBe
            IconContent.Glyph(Tabler.Outline.Target)
    }

    @Test
    fun `event horizon cartographer resolves to Map2`() {
        resolveIconContent(achievement(id = "event_horizon_cartographer", type = AchievementType.DIVERSITY, threshold = 100)) shouldBe
            IconContent.Glyph(Tabler.Outline.Map2)
    }

    // ── Secret achievement icon mapping ──────────────────────────────────

    @Test
    fun `secret achievements with mapped icons resolve to their specific glyphs`() {
        resolveIconContent(achievement(id = "persistent_clicker", isSecret = true, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.HandClick)
        resolveIconContent(achievement(id = "secret_crybaby", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.MoodCry)
        resolveIconContent(achievement(id = "secret_harem_king", isSecret = true, threshold = 20)) shouldBe
            IconContent.Glyph(Tabler.Outline.Hearts)
        resolveIconContent(achievement(id = "secret_isekai_truck", isSecret = true, threshold = 20)) shouldBe
            IconContent.Glyph(Tabler.Outline.Truck)
        resolveIconContent(achievement(id = "secret_chad", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.MilitaryRank)
        resolveIconContent(achievement(id = "secret_shonen", isSecret = true, threshold = 10)) shouldBe
            IconContent.Glyph(Tabler.Outline.HeartHandshake)
        resolveIconContent(achievement(id = "secret_deku", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Sword)
        resolveIconContent(achievement(id = "secret_eren", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Sword)
        resolveIconContent(achievement(id = "secret_lelouch", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Chess)
        resolveIconContent(achievement(id = "secret_saitama", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Bolt)
        resolveIconContent(achievement(id = "secret_jojo", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.MasksTheater)
        resolveIconContent(achievement(id = "secret_onepiece", isSecret = true, threshold = 1000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Anchor)
        resolveIconContent(achievement(id = "secret_goku", isSecret = true, threshold = 9000)) shouldBe
            IconContent.Glyph(Tabler.Outline.Rocket)
        resolveIconContent(achievement(id = "secret_shadow_monarch", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Skull)
        resolveIconContent(achievement(id = "secret_weeb_awakening", isSecret = true, threshold = 50)) shouldBe
            IconContent.Glyph(Tabler.Outline.Rocket)
        resolveIconContent(achievement(id = "time_paradox", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.ClockHour4)
        resolveIconContent(achievement(id = "void_broadcast_unlocked", isSecret = true, threshold = 1)) shouldBe
            IconContent.Glyph(Tabler.Outline.Broadcast)
    }

    // ── Fallback for unknown IDs ─────────────────────────────────────────

    @Test
    fun `unknown library achievement falls back to Books`() {
        val a = achievement(id = "unknown_id", type = AchievementType.LIBRARY, threshold = 50)
        resolveIconContent(a) shouldBe IconContent.Glyph(Tabler.Outline.Books)
    }

    @Test
    fun `unknown event achievement falls back to Award`() {
        val a = achievement(id = "unknown_id", type = AchievementType.EVENT, threshold = 1)
        resolveIconContent(a) shouldBe IconContent.Glyph(Tabler.Outline.Award)
    }

    @Test
    fun `unknown meta achievement falls back to Medal`() {
        val a = achievement(id = "unknown_id", type = AchievementType.META, threshold = 10)
        resolveIconContent(a) shouldBe IconContent.Glyph(Tabler.Outline.Medal)
    }

    @Test
    fun `unknown secret achievement falls back to Secret`() {
        val a = achievement(id = "unknown_secret", isSecret = true, threshold = 1)
        resolveIconContent(a) shouldBe IconContent.Secret
    }
}
