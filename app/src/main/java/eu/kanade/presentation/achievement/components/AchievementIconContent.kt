package eu.kanade.presentation.achievement.components

import androidx.compose.ui.graphics.vector.ImageVector
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
import com.woowla.compose.icon.collections.tabler.tabler.outline.Video
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementType

/**
 * Content rendered inside the generative achievement hexagon.
 *
 * The resolver is a pure function with no Compose runtime dependency —
 * [ImageVector] lives in `androidx.compose.ui.graphics` which has no
 * `@Composable` requirement, so this file is unit-testable in isolation.
 */
sealed interface IconContent {
    data class Glyph(val vector: ImageVector) : IconContent
    data object Secret : IconContent
}

/**
 * Per-achievement icon mapping — every achievement ID maps to a hand-picked
 * Tabler Outline glyph that best represents its meaning.
 *
 * Tiered achievements in the same group share an icon (the group's identity),
 * which is intentional: the rarity stars and tier progression provide the
 * differentiation between tiers.
 *
 * Secret achievements are NOT listed here — they resolve to [IconContent.Secret]
 * before this map is consulted.
 */
private val ACHIEVEMENT_ICONS: Map<String, ImageVector> = mapOf(
    // ── Manga chapter reading tiers ──────────────────────────────────────
    "first_chapter" to Tabler.Outline.Book2,
    "read_10_chapters" to Tabler.Outline.Book2,
    "read_50_chapters" to Tabler.Outline.Book2,
    "read_100_chapters" to Tabler.Outline.Book2,
    "read_500_chapters" to Tabler.Outline.Book2,
    "read_1000_chapters" to Tabler.Outline.Book2,

    // ── Manga completion tiers ───────────────────────────────────────────
    "complete_1_manga" to Tabler.Outline.CircleCheck,
    "complete_10_manga" to Tabler.Outline.CircleCheck,
    "complete_50_manga" to Tabler.Outline.CircleCheck,
    "read_long_manga" to Tabler.Outline.Route,

    // ── Novel chapter reading tiers ──────────────────────────────────────
    "first_novel_chapter" to Tabler.Outline.Notes,
    "read_10_novel_chapters" to Tabler.Outline.Notes,
    "read_50_novel_chapters" to Tabler.Outline.Notes,
    "read_100_novel_chapters" to Tabler.Outline.Notes,
    "read_500_novel_chapters" to Tabler.Outline.Notes,
    "read_1000_novel_chapters" to Tabler.Outline.Notes,

    // ── Novel completion tiers ───────────────────────────────────────────
    "complete_1_novel" to Tabler.Outline.CircleCheck,
    "complete_10_novel" to Tabler.Outline.CircleCheck,
    "complete_50_novel" to Tabler.Outline.CircleCheck,
    "read_long_novel" to Tabler.Outline.Route,

    // ── Anime episode watching tiers ─────────────────────────────────────
    "first_episode" to Tabler.Outline.Video,
    "watch_10_episodes" to Tabler.Outline.Video,
    "watch_50_episodes" to Tabler.Outline.Video,
    "watch_100_episodes" to Tabler.Outline.Video,
    "watch_500_episodes" to Tabler.Outline.Video,
    "watch_1000_episodes" to Tabler.Outline.Video,

    // ── Anime completion tiers ───────────────────────────────────────────
    "complete_1_anime" to Tabler.Outline.CircleCheck,
    "complete_10_anime" to Tabler.Outline.CircleCheck,
    "complete_50_anime" to Tabler.Outline.CircleCheck,

    // ── Genre exploration tiers ──────────────────────────────────────────
    "genre_explorer" to Tabler.Outline.Compass,
    "genre_explorer_complete" to Tabler.Outline.Compass,
    "genre_explorer_ultimate" to Tabler.Outline.Compass,

    // ── Streak tiers ─────────────────────────────────────────────────────
    "week_warrior" to Tabler.Outline.Flame,
    "month_master" to Tabler.Outline.Flame,
    "season_champion" to Tabler.Outline.Flame,
    "yearly_devotee" to Tabler.Outline.Flame,

    // ── Library size tiers ───────────────────────────────────────────────
    "library_collector" to Tabler.Outline.Stack2,
    "library_hoarder" to Tabler.Outline.Stack2,
    "library_titan" to Tabler.Outline.Stack2,
    "library_god" to Tabler.Outline.Stack2,

    // ── Content volume tiers ─────────────────────────────────────────────
    "content_master" to Tabler.Outline.Trophy,
    "content_god" to Tabler.Outline.Trophy,
    "content_overlord" to Tabler.Outline.Trophy,

    // ── Meta achievement unlock tiers ────────────────────────────────────
    "master_achiever" to Tabler.Outline.Award,
    "achievement_hunter" to Tabler.Outline.Award,
    "achievement_collector" to Tabler.Outline.Award,
    "achievement_completionist" to Tabler.Outline.Award,

    // ── Hybrid balance tiers (manga + anime) ─────────────────────────────
    "balanced_fan" to Tabler.Outline.Scale,
    "hybrid_connoisseur" to Tabler.Outline.Scale,
    "perfect_balance" to Tabler.Outline.Scale,

    // ── Time-based achievements ──────────────────────────────────────────
    "night_owl" to Tabler.Outline.Moon,
    "early_bird" to Tabler.Outline.Sunrise,
    "marathon_reader" to Tabler.Outline.Hourglass,

    // ── Feature-based achievements ───────────────────────────────────────
    "download_starter" to Tabler.Outline.Download,
    "chapter_collector" to Tabler.Outline.Download,
    "trophy_hunter" to Tabler.Outline.Download,
    "search_user" to Tabler.Outline.Search,
    "advanced_explorer" to Tabler.Outline.Filter,
    "filter_master" to Tabler.Outline.Filter,
    "backup_master" to Tabler.Outline.DatabaseExport,
    "settings_explorer" to Tabler.Outline.Settings,
    "stats_viewer" to Tabler.Outline.ChartBar,
    "theme_changer" to Tabler.Outline.Palette,

    // ── Trinity tiers (manga + anime + novels) ───────────────────────────
    "trinity_initiate" to Tabler.Outline.TriangleSquareCircle,
    "trinity_master" to Tabler.Outline.TriangleSquareCircle,
    "trinity_legend" to Tabler.Outline.TriangleSquareCircle,
    "three_realms_collector" to Tabler.Outline.TriangleSquareCircle,

    // ── Event horizon / cartography ──────────────────────────────────────
    "event_horizon_cartographer" to Tabler.Outline.Map2,

    // ── Completion achievements ──────────────────────────────────────────
    "the_finisher" to Tabler.Outline.Flag,
    "the_closer" to Tabler.Outline.Target,

    // ── Genre-specific achievements ──────────────────────────────────────
    "romance_devotee" to Tabler.Outline.Heart,
    "horror_aficionado" to Tabler.Outline.Ghost,
    "isekai_addict" to Tabler.Outline.Truck,
    "slice_of_life_zen" to Tabler.Outline.Sparkles,

    // ── Reading immersion tiers ──────────────────────────────────────────
    "reading_immersion_bronze" to Tabler.Outline.Hourglass,
    "reading_immersion_silver" to Tabler.Outline.Hourglass,
    "reading_immersion_gold" to Tabler.Outline.Hourglass,
    "reading_immersion_platinum" to Tabler.Outline.Hourglass,

    // ── Anime-novel hybrid tiers ─────────────────────────────────────────
    "anime_novel_hybrid_bronze" to Tabler.Outline.Scale,
    "anime_novel_hybrid_silver" to Tabler.Outline.Scale,
    "anime_novel_hybrid_gold" to Tabler.Outline.Scale,

    // ── Cross-media champion tiers ───────────────────────────────────────
    "cross_media_champion_bronze" to Tabler.Outline.Transfer,
    "cross_media_champion_silver" to Tabler.Outline.Transfer,
    "cross_media_champion_gold" to Tabler.Outline.Transfer,

    // ── Prestige rank tiers ──────────────────────────────────────────────
    "rank_up_1" to Tabler.Outline.MilitaryRank,
    "rank_up_2" to Tabler.Outline.MilitaryRank,
    "rank_up_3" to Tabler.Outline.MilitaryRank,
    "rank_up_4" to Tabler.Outline.MilitaryRank,
    "rank_up_5" to Tabler.Outline.MilitaryRank,
    "rank_up_6" to Tabler.Outline.MilitaryRank,
    "rank_up_7" to Tabler.Outline.Crown,
    "rank_up_8" to Tabler.Outline.Crown,
    "rank_up_9" to Tabler.Outline.Crown,
    "rank_up_10" to Tabler.Outline.Crown,

    // ── Secret achievements (non-secret icon for secret hall) ────────────
    // secret_hall_unlocked is secret but uses a door glyph instead of the
    // generic secret mark — it's about opening a hidden hall.
    // All other secrets resolve to IconContent.Secret before this map.
    "secret_hall_unlocked" to Tabler.Outline.Door,
    "persistent_clicker" to Tabler.Outline.HandClick,
    "secret_crybaby" to Tabler.Outline.MoodCry,
    "secret_harem_king" to Tabler.Outline.Hearts,
    "secret_isekai_truck" to Tabler.Outline.Truck,
    "secret_chad" to Tabler.Outline.MilitaryRank,
    "secret_shonen" to Tabler.Outline.HeartHandshake,
    "secret_deku" to Tabler.Outline.Sword,
    "secret_eren" to Tabler.Outline.Sword,
    "secret_lelouch" to Tabler.Outline.Chess,
    "secret_saitama" to Tabler.Outline.Bolt,
    "secret_jojo" to Tabler.Outline.MasksTheater,
    "secret_onepiece" to Tabler.Outline.Anchor,
    "secret_goku" to Tabler.Outline.Rocket,
    "secret_shadow_monarch" to Tabler.Outline.Skull,
    "secret_weeb_awakening" to Tabler.Outline.Rocket,
    "time_paradox" to Tabler.Outline.ClockHour4,
    "void_broadcast_unlocked" to Tabler.Outline.Broadcast,
)

/**
 * Fallback glyph used when an achievement ID is not in [ACHIEVEMENT_ICONS].
 * Picked by [AchievementType] as a sensible default.
 */
private fun fallbackGlyph(achievement: Achievement): ImageVector = when (achievement.type) {
    AchievementType.LIBRARY -> Tabler.Outline.Books
    AchievementType.DIVERSITY -> Tabler.Outline.Compass
    AchievementType.TIME_BASED -> Tabler.Outline.ClockHour4
    AchievementType.FEATURE_BASED -> Tabler.Outline.Settings
    AchievementType.BALANCED -> Tabler.Outline.Scale
    AchievementType.STREAK -> Tabler.Outline.Flame
    AchievementType.EVENT -> Tabler.Outline.Award
    AchievementType.QUANTITY -> Tabler.Outline.Trophy
    AchievementType.META -> Tabler.Outline.Medal
    else -> Tabler.Outline.Trophy
}

/**
 * Resolves the [IconContent] for an achievement from its metadata.
 *
 * Priority:
 * 1. [Achievement.isSecret] and not in the per-achievement icon map → [IconContent.Secret]
 * 2. Achievement ID in [ACHIEVEMENT_ICONS] → [IconContent.Glyph] with the mapped icon
 * 3. Otherwise → [IconContent.Glyph] via [fallbackGlyph] by [AchievementType]
 */
fun resolveIconContent(achievement: Achievement): IconContent {
    // Secret achievements that have a specific icon in the map use that icon;
    // all other secrets get the generic secret mark.
    val mappedIcon = ACHIEVEMENT_ICONS[achievement.id]
    if (achievement.isSecret && mappedIcon == null) {
        return IconContent.Secret
    }
    return IconContent.Glyph(mappedIcon ?: fallbackGlyph(achievement))
}
