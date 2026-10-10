package eu.kanade.tachiyomi.ui.home.hub

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Lightweight SharedPreferences + memory cache for the Home Hub.
 * Stores a serialised snapshot of the hero card, recent history titles, and
 * greeting stats so the Home tab can render instantly on cold start before
 * the database queries complete.
 *
 * Ported from Tadami's HomeHubFastCache, adapted for aniyomi-fork's unified
 * media-type state.
 */
class HomeHubFastCache(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "home_hub_fast_cache",
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var memoryCache: CachedHomeHubState? = null

    fun load(): CachedHomeHubState {
        memoryCache?.let { return it }

        val heroJson = prefs.getString(KEY_HERO, null)
        val heroesJson = prefs.getString(KEY_HEROES, null)
        val historyJson = prefs.getString(KEY_HISTORY, null)
        val recsJson = prefs.getString(KEY_RECOMMENDATIONS, null)
        val rowsJson = prefs.getString(KEY_HISTORY_ROWS, null)
        val userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        val state = CachedHomeHubState(
            hero = heroJson?.let { runCatching { json.decodeFromString<CachedHeroItem>(it) }.getOrNull() },
            heroes = heroesJson?.let {
                runCatching {
                    json.decodeFromString<Map<String, List<CachedHeroItem>>>(it)
                }.getOrNull()
            } ?: emptyMap(),
            history = historyJson?.let {
                runCatching { json.decodeFromString<List<CachedHistoryItem>>(it) }.getOrNull()
            } ?: emptyList(),
            recommendations = recsJson?.let {
                runCatching { json.decodeFromString<List<CachedRecommendationItem>>(it) }.getOrNull()
            } ?: emptyList(),
            historyRows = rowsJson?.let {
                runCatching {
                    json.decodeFromString<Map<String, List<CachedRowItem>>>(it)
                }.getOrNull()
            } ?: emptyMap(),
            userName = userName,
            isInitialized = initialized,
        )

        memoryCache = state
        return state
    }

    fun save(state: CachedHomeHubState) {
        if (memoryCache == state) return

        memoryCache = state

        prefs.edit().apply {
            putString(KEY_HERO, state.hero?.let { json.encodeToString(it) })
            putString(KEY_HEROES, json.encodeToString(state.heroes))
            putString(KEY_HISTORY, json.encodeToString(state.history))
            putString(KEY_RECOMMENDATIONS, json.encodeToString(state.recommendations))
            putString(KEY_HISTORY_ROWS, json.encodeToString(state.historyRows))
            putString(KEY_USER_NAME, state.userName)
            putBoolean(KEY_INITIALIZED, state.isInitialized)
            apply()
        }
    }

    fun markInitialized() {
        prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
        memoryCache = memoryCache?.copy(isInitialized = true)
    }

    fun updateUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name).apply()
        memoryCache = memoryCache?.copy(userName = name)
    }

    companion object {
        private const val KEY_HERO = "hero"
        private const val KEY_HEROES = "heroes"
        private const val KEY_HISTORY = "history"
        private const val KEY_RECOMMENDATIONS = "recommendations"
        private const val KEY_HISTORY_ROWS = "history_rows"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_INITIALIZED = "initialized"
    }
}

@Serializable
data class CachedHomeHubState(
    // Legacy single-hero slot — decode-only now; the per-mode `heroes`
    // map is the real store. Kept populated on save so a rollback can
    // still read the current mode's first hero.
    val hero: CachedHeroItem? = null,
    val heroes: Map<String, List<CachedHeroItem>> = emptyMap(),
    val history: List<CachedHistoryItem> = emptyList(),
    val recommendations: List<CachedRecommendationItem> = emptyList(),
    /** History-feed catalog rows by spec id — painted on cold start while
     *  the DB combine is still running (~2s on a large library), then
     *  replaced by the live rows. */
    val historyRows: Map<String, List<CachedRowItem>> = emptyMap(),
    val userName: String = "",
    val isInitialized: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hero == null && heroes.values.all { it.isEmpty() } && history.isEmpty()

    /** Cached carousel pages for one mode — with the legacy single-hero
     *  slot folded in so pre-map caches still serve their card. Keys are
     *  normalized through [HomeHubMediaType.fromKey] so a legacy "anime"
     *  entry still lands under "shows". */
    fun heroesFor(modeKey: String): List<CachedHeroItem> {
        val want = HomeHubMediaType.fromKey(modeKey)
        val list = heroes.entries.firstOrNull { (k, v) ->
            HomeHubMediaType.fromKey(k) == want && v.isNotEmpty()
        }?.value.orEmpty()
        return if (list.isEmpty() &&
            hero != null &&
            HomeHubMediaType.fromKey(hero.mediaType) == want
        ) {
            listOf(hero)
        } else {
            list
        }
    }
}

@Serializable
data class CachedHeroItem(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
    val subId: Long,
    val mediaType: String,
    // Defaults keep pre-existing cached JSON parseable — without backdropUrl
    // a cached shows hero renders the poster instead of the fanart the live
    // hero (and the detail screen) shows.
    val backdropUrl: String? = null,
    val season: Int? = null,
    val runtime: String? = null,
    val isCinemetaMovie: Boolean = false,
    val genres: List<String> = emptyList(),
    val logoId: Long? = null,
    val chapterTitle: String? = null,
    val hasStarted: Boolean = false,
    val continueText: String? = null,
)

@Serializable
data class CachedHistoryItem(
    val entryId: Long,
    val title: String,
    val progressNumber: Double,
    val coverUrl: String?,
    val coverLastModified: Long,
    val mediaType: String,
)

/** One history-row card. The cover fields rebuild the exact cover model
 *  the live row uses, so the bitmap memory key matches and the live
 *  swap paints the same pixels. */
@Serializable
data class CachedRowItem(
    val title: String,
    val subtitle: String? = null,
    val entryId: Long,
    val medium: String,
    val mintKey: String? = null,
    val coverKind: String,
    val coverEntryId: Long = 0,
    val coverSourceId: Long = 0,
    val coverFavorite: Boolean = false,
    val coverUrl: String? = null,
    val coverLastModified: Long = 0,
)

@Serializable
data class CachedRecommendationItem(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val mediaType: String,
)
