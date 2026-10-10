package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.runtime.Immutable
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.tachiyomi.data.catalogrows.CatalogRowSpec
import eu.kanade.tachiyomi.data.catalogrows.RowItem
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.history.manga.model.MangaHistoryWithRelations
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.domain.history.shows.model.AnimeHistoryWithRelations
import tachiyomi.i18n.aniyomi.AYMR

@Immutable
data class HomeHubCardItem(
    val id: Long,
    val title: String,
    val coverData: Any?,
    val mediaType: HomeHubMediaType,
    val progressText: String? = null,
)

/**
 * Home Hub state — recently viewed + greeting + hero card +
 * streak counter + month stats + achievement count + recommendations.
 *
 * Enhanced from the minimal Phase 2.3 implementation with features ported
 * from Tadami's Home Hub.
 */
@Immutable
data class HomeHubState(
    val isLoading: Boolean = true,
    val recentAnime: List<AnimeHistoryWithRelations> = emptyList(),
    val recentManga: List<MangaHistoryWithRelations> = emptyList(),
    val recentNovels: List<NovelHistoryWithRelations> = emptyList(),
    // --- Greeting system ---
    val greeting: StringResource = AYMR.strings.home_greeting_default,
    val greetingReady: Boolean = false,
    val userName: String = "",
    // --- Hero carousel ---
    val hero: HomeHubHero? = null,
    /** Hero pages — most recently consumed entries in the current mode,
     *  newest first; feeds the swipeable full-bleed carousel. [hero] is
     *  always `heroes.firstOrNull()`. */
    val heroes: List<HomeHubHero> = emptyList(),
    // --- Streak counter ---
    val currentStreak: Int = 0,
    // --- Month stats ---
    val monthStats: MonthStats? = null,
    val librarySize: Int = 0,
    // --- Achievement display ---
    val achievementCount: Int = 0,
    val achievementTotal: Int = 0,
    // --- Recommendations ---
    val recommendations: List<HomeHubCardItem> = emptyList(),
    // --- User-built catalog rows (Phase 18) ---
    /** All persisted specs, ordered — mode + visibility filter at render. */
    val catalogSpecs: List<CatalogRowSpec> = emptyList(),
    /** Fetch state per spec id — populated lazily on first visibility. */
    val catalogRows: Map<String, CatalogRowState> = emptyMap(),
    /** Hosted sheet — Manage Catalogs / the builder (Phase 18-03). */
    val dialog: HomeHubDialog? = null,
    // --- Mode-aware filtering ---
    val currentMode: ContentMode = ContentMode.MANGA,
    // --- Hidden collections (set of collection IDs hidden from home) ---
    val hiddenAnimeCollections: Set<Long> = emptySet(),
    val hiddenMangaCollections: Set<Long> = emptySet(),
    val hiddenNovelCollections: Set<Long> = emptySet(),
    // --- Selection mode for long-press overlay ---
    val selection: Set<Long> = emptySet(),
) {
    val selectionMode: Boolean
        get() = selection.isNotEmpty()
    val hasAnyRecent: Boolean
        get() = recentAnime.isNotEmpty() || recentManga.isNotEmpty() || recentNovels.isNotEmpty()

    /**
     * Home is empty only when there is nothing to render IN THIS MODE —
     * a spec living under another medium must not suppress the welcome
     * state here (the user can't see it anyway).
     */
    val isEmpty: Boolean
        get() = !isLoading &&
            !hasAnyRecent &&
            hero == null &&
            recommendations.isEmpty() &&
            catalogSpecs.none {
                it.visible && it.medium == Carrier.fromContentMode(currentMode).medium
            }

    // --- Card helpers for unified rendering ---

    val recentAnimeCards: List<HomeHubCardItem>
        get() = recentAnime
            // showKey merges extension-source duplicates that CinemetaEntryUnifier
            // mapped onto the same canonical entry (e.g. per-episode/per-season
            // source entries all resolving to one "Naruto").
            .distinctBy { it.showKey }
            .map {
                HomeHubCardItem(it.animeId, it.title, it.coverData, HomeHubMediaType.SHOWS, animeProgressLabel(it))
            }

    val recentMangaCards: List<HomeHubCardItem>
        get() = recentManga
            .distinctBy { it.mangaId }
            .map {
                HomeHubCardItem(
                    it.mangaId,
                    it.title,
                    it.coverData,
                    HomeHubMediaType.MANGA,
                    "Ch. ${eu.kanade.presentation.util.formatChapterNumber(it.chapterNumber)}",
                )
            }

    val recentNovelCards: List<HomeHubCardItem>
        get() = recentNovels
            .distinctBy { it.novelId }
            .map {
                HomeHubCardItem(
                    it.novelId,
                    it.title,
                    it.coverData,
                    HomeHubMediaType.NOVEL,
                    "Ch. ${eu.kanade.presentation.util.formatChapterNumber(it.chapterNumber)}",
                )
            }
}

/**
 * Progress label for a recent-anime card / hero:
 * - Cinemeta movies: runtime ("2h 07min") — a movie has one virtual episode,
 *   so "Ep. 1" is meaningless.
 * - Cinemeta series: "S2 · Ep. 5" — the season is encoded in the episode's
 *   scanlator field ("Season N|★R.R").
 * - Everything else: "Ep. 5".
 */
internal fun animeProgressLabel(item: AnimeHistoryWithRelations): String {
    return when {
        item.isCinemetaMovie ->
            eu.kanade.presentation.util.formatRuntime(item.runtime) ?: "Movie"
        else -> buildString {
            item.cinemetaSeason?.takeIf { it > 0 }?.let { append("S$it · ") }
            append("Ep. ")
            append(eu.kanade.presentation.util.formatEpisodeNumber(item.episodeNumber))
        }
    }
}

/**
 * Hero card data — the most recently watched/read entry with progress info.
 */
@Immutable
data class HomeHubHero(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val chapterTitle: String? = null,
    val coverData: Any?,
    val subId: Long,
    val mediaType: HomeHubMediaType,
    val allRead: Boolean = false,
    /** Real progress exists on the entry (any item consumed or an
     *  in-progress position) — mirrors the detail screen's hasStarted
     *  so the pre-filled continue label picks "Continue:" over "Start". */
    val hasStarted: Boolean = false,
    /** The detail-parity progress fragment for the continue-row label
     *  (chapter/episode name, or "Chapter N" for number-display manga).
     *  The compact pill fragment stays in [progressNumber]/[chapterTitle]
     *  /[season] — this is what the loaded bar will actually read. */
    val continueText: String? = null,
    // Anime-only extras for the resume label (season prefix, movie runtime).
    val season: Int? = null,
    val runtime: String? = null,
    val isCinemetaMovie: Boolean = false,
    // Hero presentation extras — prevalence-ordered genres (top 3 shown),
    // a high-res backdrop preferred over the (often low-res) coverData,
    // and the canonical entry id the detail screen resolves to (showKey
    // for shows, the unifier's materialized `miko:` row for manga/novel).
    // Doubles as the Cinemeta clearlogo pref key and the shared-element
    // nav id — pushing it skips the detail screen's canonical redirect,
    // which would run a second transition and pop every shared element.
    val genres: List<String> = emptyList(),
    val backdrop: Any? = null,
    val canonicalId: Long? = null,
)

/**
 * Runtime state for one catalog row (Phase 18) — populated by
 * `onCatalogRowVisible` → resolver fetch, keyed by spec id in
 * [HomeHubState.catalogRows].
 */
@Immutable
data class CatalogRowState(
    val items: List<RowItem> = emptyList(),
    val loading: Boolean = false,
    val error: CatalogRowError? = null,
)

/**
 * Sheets hosted by the hub — rendered before the `isEmpty` early return
 * so a fresh user can open Manage Catalogs and build their first row.
 */
sealed interface HomeHubDialog {
    data object ManageCatalogs : HomeHubDialog

    /**
     * [lockedMedium] scopes the type step to one category — "New
     * catalog" from a manage-sheet medium pill opens the builder with
     * only that category's sub-types offered.
     */
    data class BuildCatalog(
        val existing: CatalogRowSpec?,
        val lockedMedium: String? = null,
    ) : HomeHubDialog
}

enum class CatalogRowError {
    /** No TMDB key resolves — row renders the BYOK notice. */
    NO_TMDB_KEY,

    /** Feed endpoint can't serve the spec (e.g. LATEST on a
     *  `supportsLatest=false` source). */
    UNSUPPORTED_FEED,

    /** The spec's extension is uninstalled or moved to another package. */
    EXTENSION_MISSING,
    FETCH_FAILED,
}

enum class HomeHubMediaType {
    SHOWS,
    MANGA,
    NOVEL,
    ;

    val key: String
        get() = name.lowercase()

    companion object {
        fun fromKey(key: String): HomeHubMediaType {
            // Legacy fast-cache entries carry the pre-15-04 "anime" key.
            if (key == "anime") return SHOWS
            return entries.firstOrNull { it.key == key } ?: SHOWS
        }
    }
}
