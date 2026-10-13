package miko.ui.catalog

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Android's `CatalogRowStore` — pref-backed persistence for user-built
 * home catalog rows under the same `home_catalog_rows` key so Android
 * rows ride backup/restore compatibly. `rows` always emits sorted by
 * `(order, id)`; one-shot seeding lands a `Feed.History` row per medium
 * exactly once so a user delete/hide is final.
 */
@OptIn(ExperimentalUuidApi::class)
class CatalogRowStore(
    preferenceStore: PreferenceStore,
    private val json: Json,
) {
    private val pref = preferenceStore.getString(KEY)
    private val seededPref = preferenceStore.getBoolean("${KEY}_recents_seeded", false)

    private val _rows = MutableStateFlow(decode(pref.get()))
    val rows: StateFlow<List<CatalogRowSpec>> = _rows

    init {
        if (!seededPref.get()) {
            seededPref.set(true)
            val existing = rows.value
            val missing = listOf(
                CatalogRowSpec.MEDIUM_SHOWS,
                CatalogRowSpec.MEDIUM_MANGA,
                CatalogRowSpec.MEDIUM_BOOKS,
            ).filter { m -> existing.none { it.medium == m && it.feed is Feed.History } }
            if (missing.isNotEmpty()) {
                val seeds = missing.mapIndexed { i, m ->
                    CatalogRowSpec(
                        id = "row_${Uuid.random()}",
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

    /** Reorder to [orderedIds]' sequence; unlisted specs keep relative
     *  order, appended after the reordered set. */
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

    /** Cache key for a spec's fetch result — content-affecting fields
     *  only: reorder/visibility must not refetch the row. */
    fun contentKey(spec: CatalogRowSpec): String =
        json.encodeToString(CatalogRowSpec.serializer(), spec.copy(order = 0, visible = true))

    private fun decode(raw: String): List<CatalogRowSpec> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<CatalogRowSpec>>(raw)
                .sortedWith(compareBy({ it.order }, { it.id }))
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
