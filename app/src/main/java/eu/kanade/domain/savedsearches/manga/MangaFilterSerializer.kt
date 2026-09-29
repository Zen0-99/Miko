package eu.kanade.domain.savedsearches.manga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class MangaFilterState(
    val name: String,
    val type: String,
    val state: String,
)

class MangaFilterSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(filterList: FilterList): String {
        val states = filterList.mapNotNull { filter -> encodeFilter(filter) }
        return json.encodeToString(ListSerializer(MangaFilterState.serializer()), states)
    }

    fun decode(filterList: FilterList, encoded: String): FilterList {
        val states = try {
            json.decodeFromString(ListSerializer(MangaFilterState.serializer()), encoded)
        } catch (e: Exception) {
            return filterList
        }

        val stateMap = states.associateBy { it.name }
        filterList.forEach { filter ->
            val savedState = stateMap[filter.name] ?: return@forEach
            applyState(filter, savedState)
        }
        return filterList
    }

    private fun encodeFilter(filter: Filter<*>): MangaFilterState? {
        return when (filter) {
            is Filter.Header, is Filter.Separator -> null
            is Filter.Text -> MangaFilterState(filter.name, "Text", filter.state)
            is Filter.CheckBox -> MangaFilterState(filter.name, "CheckBox", filter.state.toString())
            is Filter.TriState -> MangaFilterState(filter.name, "TriState", filter.state.toString())
            is Filter.Select<*> -> MangaFilterState(filter.name, "Select", filter.state.toString())
            is Filter.Sort -> {
                val selection = filter.state
                val stateStr = if (selection != null) "${selection.index};${selection.ascending}" else "null"
                MangaFilterState(filter.name, "Sort", stateStr)
            }
            is Filter.Group<*> -> {
                val subStates = filter.state.mapNotNull { subFilter ->
                    if (subFilter is Filter<*>) encodeFilter(subFilter) else null
                }
                MangaFilterState(
                    filter.name,
                    "Group",
                    json.encodeToString(ListSerializer(MangaFilterState.serializer()), subStates),
                )
            }
            else -> null
        }
    }

    private fun applyState(filter: Filter<*>, savedState: MangaFilterState) {
        when (filter) {
            is Filter.Text -> {
                if (savedState.type == "Text") filter.state = savedState.state
            }
            is Filter.CheckBox -> {
                if (savedState.type == "CheckBox") filter.state = savedState.state.toBoolean()
            }
            is Filter.TriState -> {
                if (savedState.type == "TriState") filter.state = savedState.state.toIntOrNull() ?: 0
            }
            is Filter.Select<*> -> {
                if (savedState.type == "Select") filter.state = savedState.state.toIntOrNull() ?: 0
            }
            is Filter.Sort -> {
                if (savedState.type == "Sort" && savedState.state != "null") {
                    val parts = savedState.state.split(";")
                    if (parts.size == 2) {
                        filter.state = Filter.Sort.Selection(
                            index = parts[0].toIntOrNull() ?: 0,
                            ascending = parts[1].toBoolean(),
                        )
                    }
                }
            }
            is Filter.Group<*> -> {
                if (savedState.type == "Group") {
                    val subStates = try {
                        json.decodeFromString(ListSerializer(MangaFilterState.serializer()), savedState.state)
                    } catch (e: Exception) {
                        return
                    }
                    val subStateMap = subStates.associateBy { it.name }
                    filter.state.forEach { subFilter ->
                        if (subFilter is Filter<*>) {
                            val subSaved = subStateMap[subFilter.name]
                            if (subSaved != null) applyState(subFilter, subSaved)
                        }
                    }
                }
            }
            else -> {}
        }
    }
}
