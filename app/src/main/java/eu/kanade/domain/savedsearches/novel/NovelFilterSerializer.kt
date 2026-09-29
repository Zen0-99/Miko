package eu.kanade.domain.savedsearches.novel

import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class NovelFilterState(
    val name: String,
    val type: String,
    val state: String,
)

class NovelFilterSerializer {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(filterList: NovelFilterList): String {
        val states = filterList.mapNotNull { filter -> encodeFilter(filter) }
        return json.encodeToString(ListSerializer(NovelFilterState.serializer()), states)
    }

    fun decode(filterList: NovelFilterList, encoded: String): NovelFilterList {
        val states = try {
            json.decodeFromString(ListSerializer(NovelFilterState.serializer()), encoded)
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

    private fun encodeFilter(filter: NovelFilter<*>): NovelFilterState? {
        return when (filter) {
            is NovelFilter.Header, is NovelFilter.Separator -> null
            is NovelFilter.Text -> NovelFilterState(filter.name, "Text", filter.state)
            is NovelFilter.CheckBox -> NovelFilterState(filter.name, "CheckBox", filter.state.toString())
            is NovelFilter.TriState -> NovelFilterState(filter.name, "TriState", filter.state.toString())
            is NovelFilter.Select<*> -> NovelFilterState(filter.name, "Select", filter.state.toString())
            is NovelFilter.Sort -> {
                val selection = filter.state
                val stateStr = if (selection != null) "${selection.index};${selection.ascending}" else "null"
                NovelFilterState(filter.name, "Sort", stateStr)
            }
            is NovelFilter.Group<*> -> {
                val subStates = filter.state.mapNotNull { subFilter ->
                    if (subFilter is NovelFilter<*>) encodeFilter(subFilter) else null
                }
                NovelFilterState(
                    filter.name,
                    "Group",
                    json.encodeToString(ListSerializer(NovelFilterState.serializer()), subStates),
                )
            }
            else -> null
        }
    }

    private fun applyState(filter: NovelFilter<*>, savedState: NovelFilterState) {
        when (filter) {
            is NovelFilter.Text -> {
                if (savedState.type == "Text") filter.state = savedState.state
            }
            is NovelFilter.CheckBox -> {
                if (savedState.type == "CheckBox") filter.state = savedState.state.toBoolean()
            }
            is NovelFilter.TriState -> {
                if (savedState.type == "TriState") filter.state = savedState.state.toIntOrNull() ?: 0
            }
            is NovelFilter.Select<*> -> {
                if (savedState.type == "Select") filter.state = savedState.state.toIntOrNull() ?: 0
            }
            is NovelFilter.Sort -> {
                if (savedState.type == "Sort" && savedState.state != "null") {
                    val parts = savedState.state.split(";")
                    if (parts.size == 2) {
                        filter.state = NovelFilter.Sort.Selection(
                            index = parts[0].toIntOrNull() ?: 0,
                            ascending = parts[1].toBoolean(),
                        )
                    }
                }
            }
            is NovelFilter.Group<*> -> {
                if (savedState.type == "Group") {
                    val subStates = try {
                        json.decodeFromString(ListSerializer(NovelFilterState.serializer()), savedState.state)
                    } catch (e: Exception) {
                        return
                    }
                    val subStateMap = subStates.associateBy { it.name }
                    filter.state.forEach { subFilter ->
                        if (subFilter is NovelFilter<*>) {
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
