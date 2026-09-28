package eu.kanade.tachiyomi.util

import androidx.annotation.ColorInt
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the extracted cover (accent) colors for entries (novels, manga, anime),
 * keyed by entry id within each [EntryType].
 *
 * Generalized from [eu.kanade.tachiyomi.util.novel.NovelCoverMetadata] so that
 * manga and anime covers can use the same caching mechanism. Colors are
 * pre-extracted while browsing the library/sources so that when the user opens
 * a detail screen the theme color is already known and can be applied
 * synchronously — eliminating the flash of the default app theme being swapped
 * for the cover-derived accent.
 *
 * The cache is backed by an in-memory map and persisted to preferences so it
 * survives app restarts. Stored values are the raw palette colors (post
 * saturation injection, pre theme-light/dark adjustment); the light/dark
 * adjustment is applied at read time.
 */
object EntryCoverMetadata {

    enum class EntryType(val prefKey: String) {
        NOVEL("novel_cover_colors"),
        MANGA("manga_cover_colors"),

        /** Legacy pref key `anime_cover_colors` retained — it's a storage id. */
        SHOWS("anime_cover_colors"),
    }

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val colorMaps = ConcurrentHashMap<EntryType, ConcurrentHashMap<Long, Int>>()

    // Which cover the cached color was extracted from (hash of the cover
    // data). When the cover changes — e.g. a Cinemeta anime's per-season
    // poster swap — the cached color no longer applies and must be
    // re-extracted.
    private val coverKeyMaps = ConcurrentHashMap<EntryType, ConcurrentHashMap<Long, Int>>()

    private val loaded = java.util.Collections.synchronizedSet(mutableSetOf<EntryType>())

    private fun ensureLoaded(type: EntryType) {
        if (type in loaded) return
        synchronized(this) {
            if (type in loaded) return
            val restored = preferenceStore.getStringSet(type.prefKey).get()
                .mapNotNull { entry ->
                    val splits = entry.split("|")
                    val id = splits.firstOrNull()?.toLongOrNull()
                    val color = splits.getOrNull(1)?.toIntOrNull()
                    val coverKey = splits.getOrNull(2)?.toIntOrNull()
                    if (id != null && color != null) Triple(id, color, coverKey) else null
                }
            colorMaps[type] = ConcurrentHashMap(restored.associate { it.first to it.second })
            coverKeyMaps[type] = ConcurrentHashMap(
                restored.mapNotNull { (id, _, key) -> key?.let { id to it } }.toMap(),
            )
            loaded.add(type)
        }
    }

    /**
     * Returns the cached base (raw) cover color for [entryId], or null if not yet
     * extracted. When [coverKey] is provided, the cached color is only returned
     * if it was extracted from that same cover — a changed cover (e.g. season
     * poster swap) forces re-extraction. The caller must apply theme-light/dark
     * adjustment before use.
     */
    @ColorInt
    fun getBaseColor(type: EntryType, entryId: Long?, coverKey: String? = null): Int? {
        entryId ?: return null
        ensureLoaded(type)
        if (coverKey != null && coverKeyMaps[type]?.get(entryId) != coverKey.hashCode()) {
            return null
        }
        return colorMaps[type]?.get(entryId)
    }

    /**
     * Stores the extracted base cover color for [entryId], tagged with the
     * [coverKey] it was extracted from so a cover change invalidates it.
     */
    fun setBaseColor(type: EntryType, entryId: Long?, @ColorInt color: Int, coverKey: String? = null) {
        entryId ?: return
        ensureLoaded(type)
        colorMaps[type]?.put(entryId, color)
        if (coverKey != null) {
            coverKeyMaps[type]?.put(entryId, coverKey.hashCode())
        }
    }

    /**
     * Removes the cached color for [entryId].
     */
    fun remove(type: EntryType, entryId: Long?) {
        entryId ?: return
        ensureLoaded(type)
        colorMaps[type]?.remove(entryId)
        coverKeyMaps[type]?.remove(entryId)
    }

    /**
     * Persists the current cache for [type] to preferences.
     */
    fun savePrefs(type: EntryType) {
        ensureLoaded(type)
        val encoded = colorMaps[type]?.map { (id, color) ->
            "$id|$color|${coverKeyMaps[type]?.get(id) ?: ""}"
        }?.toSet() ?: emptySet()
        preferenceStore.getStringSet(type.prefKey).set(encoded)
    }
}
