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
        ANIME("anime_cover_colors"),
    }

    private val preferenceStore: PreferenceStore by lazy { Injekt.get() }
    private val colorMaps = ConcurrentHashMap<EntryType, ConcurrentHashMap<Long, Int>>()

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
                    if (id != null && color != null) id to color else null
                }.toMap()
            colorMaps[type] = ConcurrentHashMap(restored)
            loaded.add(type)
        }
    }

    /**
     * Returns the cached base (raw) cover color for [entryId], or null if not yet
     * extracted. The caller must apply theme-light/dark adjustment before use.
     */
    @ColorInt
    fun getBaseColor(type: EntryType, entryId: Long?): Int? {
        entryId ?: return null
        ensureLoaded(type)
        return colorMaps[type]?.get(entryId)
    }

    /**
     * Stores the extracted base cover color for [entryId].
     */
    fun setBaseColor(type: EntryType, entryId: Long?, @ColorInt color: Int) {
        entryId ?: return
        ensureLoaded(type)
        colorMaps[type]?.put(entryId, color)
    }

    /**
     * Removes the cached color for [entryId].
     */
    fun remove(type: EntryType, entryId: Long?) {
        entryId ?: return
        ensureLoaded(type)
        colorMaps[type]?.remove(entryId)
    }

    /**
     * Persists the current cache for [type] to preferences.
     */
    fun savePrefs(type: EntryType) {
        ensureLoaded(type)
        val encoded = colorMaps[type]?.map { "${it.key}|${it.value}" }?.toSet() ?: emptySet()
        preferenceStore.getStringSet(type.prefKey).set(encoded)
    }
}
