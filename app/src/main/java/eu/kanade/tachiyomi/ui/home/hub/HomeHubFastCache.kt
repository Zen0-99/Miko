package eu.kanade.tachiyomi.ui.home.hub

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNames

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
        val historyJson = prefs.getString(KEY_HISTORY, null)
        val userName = prefs.getString(KEY_USER_NAME, "") ?: ""
        val initialized = prefs.getBoolean(KEY_INITIALIZED, false)

        val state = CachedHomeHubState(
            hero = heroJson?.let { runCatching { json.decodeFromString<CachedHeroItem>(it) }.getOrNull() },
            history = historyJson?.let {
                runCatching { json.decodeFromString<List<CachedHistoryItem>>(it) }.getOrNull()
            } ?: emptyList(),
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
            putString(KEY_HISTORY, json.encodeToString(state.history))
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
        private const val KEY_HISTORY = "history"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_INITIALIZED = "initialized"
    }
}

@Serializable
data class CachedHomeHubState(
    val hero: CachedHeroItem? = null,
    val history: List<CachedHistoryItem> = emptyList(),
    val userName: String = "",
    val isInitialized: Boolean = false,
) {
    val isEmpty: Boolean
        get() = hero == null && history.isEmpty()
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
