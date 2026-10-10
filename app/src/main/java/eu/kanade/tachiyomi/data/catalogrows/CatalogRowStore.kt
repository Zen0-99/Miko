package eu.kanade.tachiyomi.data.catalogrows

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Persistence for user-built home catalog rows (Phase 18).
 *
 * Dedicated pref-backed store rather than a `UiPreferences` key: specs
 * are a JSON list needing decode ownership, order normalization, and a
 * hot `StateFlow`. Rides the shared `AndroidPreferenceStore` so rows
 * ride backup/restore for free.
 *
 * [rows] is always emitted sorted by `(order, id)` — `id` breaks order
 * collisions deterministically (specs written by older builds or a
 * mid-edit crash can share an order value).
 */
class CatalogRowStore(
    preferenceStore: PreferenceStore,
    private val json: Json,
) {
    private val pref = preferenceStore.getString(KEY)

    /** One-shot flag — the seeded recents rows land exactly once, so a
     *  user deleting them is never re-seeded. */
    private val seededPref = preferenceStore.getBoolean("${KEY}_recents_seeded", false)

    /**
     * In-memory source of truth — writes land synchronously so readers
     * never observe a stale list. No `pref.changes()` bridge: this store
     * is the only writer of [KEY] and a restore-level external write
     * lands via app restart anyway, while an async replay of pref
     * emissions races our own writes (conflated snapshots overwrite
     * newer state).
     */
    private val _rows = MutableStateFlow(decode(pref.get()))
    val rows: StateFlow<List<CatalogRowSpec>> = _rows

    init {
        // Recents-as-catalog: the built-in "Recently Read/Watched" rows
        // became ordinary spec rows (Phase 18 UAT) — seed one per medium,
        // first in order, once. Runs for fresh installs and upgrades
        // alike; a later user delete/hide is final.
        if (!seededPref.get()) {
            seededPref.set(true)
            val existing = rows.value
            val missing = listOf(
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_SHOWS,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_MANGA,
                eu.kanade.tachiyomi.data.mikodb.WorkIdResolver.MEDIUM_BOOKS,
            ).filter { m -> existing.none { it.medium == m && it.feed is Feed.History } }
            if (missing.isNotEmpty()) {
                val seeds = missing.mapIndexed { i, m ->
                    CatalogRowSpec(
                        id = "row_${java.util.UUID.randomUUID()}",
                        medium = m,
                        feed = Feed.History,
                        order = i,
                    )
                }
                write(
                    seeds + existing.mapIndexed { i, s -> s.copy(order = i + seeds.size) },
                )
            }
        }
    }

    /**
     * Insert or replace a spec by id. New specs append at
     * `max(order) + 1`; edits keep the existing row's position.
     */
    fun upsert(spec: CatalogRowSpec): CatalogRowSpec {
        val current = rows.value
        val existingOrder = current.firstOrNull { it.id == spec.id }?.order
        val resolved = spec.copy(order = existingOrder ?: ((current.maxOfOrNull { it.order } ?: -1) + 1))
        write(current.filter { it.id != spec.id } + resolved)
        return resolved
    }

    fun delete(id: String) {
        write(rows.value.filterNot { it.id == id })
    }

    /**
     * Reorder to [orderedIds]' sequence. Specs missing from the list
     * keep their relative order, appended after the reordered set.
     */
    fun reorder(orderedIds: List<String>) {
        val byId = rows.value.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] }
            .mapIndexed { index, spec -> spec.copy(order = index) }
        val idSet = orderedIds.toSet()
        val remaining = rows.value.filter { it.id !in idSet }
            .mapIndexed { index, spec -> spec.copy(order = reordered.size + index) }
        write(reordered + remaining)
    }

    fun setVisible(id: String, visible: Boolean) {
        write(rows.value.map { if (it.id == id) it.copy(visible = visible) else it })
    }

    /**
     * Cache key for a spec's fetch result — content-affecting fields
     * only: reorder and visibility toggles must not refetch the row.
     */
    fun contentKey(spec: CatalogRowSpec): String =
        json.encodeToString(CatalogRowSpec.serializer(), spec.copy(order = 0, visible = true))

    private fun decode(raw: String): List<CatalogRowSpec> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CatalogRowSpec>>(raw)
                .sortedWith(compareBy({ it.order }, { it.id }))
        }.onFailure {
            logcat(LogPriority.WARN) { "CatalogRowStore: decode failed: ${it.message}" }
        }.getOrDefault(emptyList())
    }

    private fun write(specs: List<CatalogRowSpec>) {
        val normalized = specs.sortedWith(compareBy({ it.order }, { it.id }))
        _rows.value = normalized
        pref.set(json.encodeToString(normalized))
    }

    companion object {
        const val KEY = "home_catalog_rows"
    }
}
