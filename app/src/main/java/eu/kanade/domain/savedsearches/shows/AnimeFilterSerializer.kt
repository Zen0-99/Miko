package eu.eu.kanade.domain.savedsearches.shows

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class AnimeFilterState(
    val name: String,
    val type: String,
    val state: String,
)

class AnimeFilterSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(filterList: AnimeFilterList): String {
        val states = filterList.mapNotNull { filter -> encodeFilter(filter) }
        return json.encodeToString(ListSerializer(AnimeFilterState.serializer()), states)
    }

    fun decode(filterList: AnimeFilterList, encoded: String): AnimeFilterList {
        val states = try {
            json.decodeFromString(ListSerializer(AnimeFilterState.serializer()), encoded)
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

    private fun encodeFilter(filter: AnimeFilter<*>): AnimeFilterState? {
        return when (filter) {
            is AnimeFilter.Header, is AnimeFilter.Separator -> null
            is AnimeFilter.Text -> AnimeFilterState(filter.name, "Text", filter.state)
            is AnimeFilter.CheckBox -> AnimeFilterState(filter.name, "CheckBox", filter.state.toString())
            is AnimeFilter.TriState -> AnimeFilterState(filter.name, "TriState", filter.state.toString())
            is AnimeFilter.Select<*> -> AnimeFilterState(filter.name, "Select", filter.state.toString())
            is AnimeFilter.Sort -> {
                val selection = filter.state
                val stateStr = if (selection != null) "${selection.index};${selection.ascending}" else "null"
                AnimeFilterState(filter.name, "Sort", stateStr)
            }
            is AnimeFilter.Group<*> -> {
                val subStates = filter.state.mapNotNull { subFilter ->
                    if (subFilter is AnimeFilter<*>) encodeFilter(subFilter) else null
                }
                AnimeFilterState(
                    filter.name,
                    "Group",
                    json.encodeToString(ListSerializer(AnimeFilterState.serializer()), subStates),
                )
            }
            else -> null
        }
    }

    private fun applyState(filter: AnimeFilter<*>, savedState: AnimeFilterState) {
        when (filter) {
            is AnimeFilter.Text -> {
                if (savedState.type == "Text") filter.state = savedState.state
            }
            is AnimeFilter.CheckBox -> {
                if (savedState.type == "CheckBox") filter.state = savedState.state.toBoolean()
            }
            is AnimeFilter.TriState -> {
                if (savedState.type == "TriState") filter.state = savedState.state.toIntOrNull() ?: 0
            }
            is AnimeFilter.Select<*> -> {
                if (savedState.type == "Select") filter.state = savedState.state.toIntOrNull() ?: 0
            }
            is AnimeFilter.Sort -> {
                if (savedState.type == "Sort" && savedState.state != "null") {
                    val parts = savedState.state.split(";")
                    if (parts.size == 2) {
                        filter.state = AnimeFilter.Sort.Selection(
                            index = parts[0].toIntOrNull() ?: 0,
                            ascending = parts[1].toBoolean(),
                        )
                    }
                }
            }
            is AnimeFilter.Group<*> -> {
                if (savedState.type == "Group") {
                    val subStates = try {
                        json.decodeFromString(ListSerializer(AnimeFilterState.serializer()), savedState.state)
                    } catch (e: Exception) {
                        return
                    }
                    val subStateMap = subStates.associateBy { it.name }
                    filter.state.forEach { subFilter ->
                        if (subFilter is AnimeFilter<*>) {
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
