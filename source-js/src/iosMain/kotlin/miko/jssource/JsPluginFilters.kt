package miko.jssource

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.putJsonObject

/**
 * LNReader `__plugin.filters` descriptor → carrier FilterLists, and
 * FilterList state → the `{key:{type,value}}` values envelope plugins
 * expect on `searchX`/`popularX` calls. Ported from Android's
 * NovelPluginFilterMapper, generalized across the three carriers.
 *
 * Manga/Anime filter hierarchies lack Switch/XCheckBox — JS Switch maps
 * to CheckBox, XCheckbox maps to a TriState group; the outbound `type`
 * stays faithful to the descriptor either way.
 */

internal object JsFilterTypes {
    const val TEXT = "Text"
    const val PICKER = "Picker"
    const val CHECKBOX = "Checkbox"
    const val SWITCH = "Switch"
    const val XCHECKBOX = "XCheckbox"
}

internal data class JsFilterOption(val label: String, val value: String)

internal data class JsFilterDef(
    val key: String,
    val type: String,
    val label: String,
    val value: JsonElement?,
    val options: List<JsFilterOption>,
)

internal fun parseJsFilterDefs(payload: String?, json: Json): List<JsFilterDef> {
    if (payload.isNullOrBlank() || payload == "null") return emptyList()
    val root = runCatching { json.parseToJsonElement(payload) as? JsonObject }
        .getOrNull() ?: return emptyList()
    return root.mapNotNull { (key, element) ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val type = (obj["type"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val label = (obj["label"] as? JsonPrimitive)?.contentOrNull ?: key
        val options = (obj["options"] as? JsonArray)?.mapNotNull { option ->
            val optionObj = option as? JsonObject ?: return@mapNotNull null
            val optionValue = (optionObj["value"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            val optionLabel = (optionObj["label"] as? JsonPrimitive)?.contentOrNull
                ?: optionValue
            JsFilterOption(label = optionLabel, value = optionValue)
        }.orEmpty()
        JsFilterDef(
            key = key,
            type = type,
            label = label,
            value = obj["value"],
            options = options,
        )
    }
}

// ------------------------------------------------------------------
// Descriptor → carrier FilterLists
// ------------------------------------------------------------------

internal fun jsDefsToNovelFilterList(defs: List<JsFilterDef>): NovelFilterList {
    return NovelFilterList(defs.mapNotNull { def -> def.toNovelFilter() })
}

private fun JsFilterDef.toNovelFilter(): NovelFilter<*>? = when (type) {
    JsFilterTypes.TEXT -> JsNovelTextFilter(key, label, primitiveContent(value).orEmpty())
    JsFilterTypes.SWITCH -> JsNovelSwitchFilter(key, label, primitiveBoolean(value))
    JsFilterTypes.PICKER -> JsNovelPickerFilter(
        key,
        label,
        options,
        pickerIndex(options, value),
    )
    JsFilterTypes.CHECKBOX -> JsNovelCheckGroup(key, label, options, stringValues(value))
    JsFilterTypes.XCHECKBOX -> {
        val valueObj = value as? JsonObject
        JsNovelTriGroup(
            key,
            label,
            options,
            stringValues(valueObj?.get("include")),
            stringValues(valueObj?.get("exclude")),
        )
    }
    else -> null
}

internal fun jsDefsToMangaFilterList(defs: List<JsFilterDef>): FilterList {
    return FilterList(defs.mapNotNull { def -> def.toMangaFilter() })
}

private fun JsFilterDef.toMangaFilter(): Filter<*>? = when (type) {
    JsFilterTypes.TEXT -> JsMangaTextFilter(key, label, primitiveContent(value).orEmpty())
    // Manga's Filter hierarchy has no Switch — CheckBox carries the state;
    // jsType keeps the outbound envelope faithful to the descriptor.
    JsFilterTypes.SWITCH -> JsMangaCheckBox(
        key = key,
        jsType = JsFilterTypes.SWITCH,
        name = label,
        state = primitiveBoolean(value),
    )
    JsFilterTypes.PICKER -> JsMangaPickerFilter(
        key,
        label,
        options,
        pickerIndex(options, value),
    )
    JsFilterTypes.CHECKBOX -> JsMangaCheckGroup(key, label, options, stringValues(value))
    JsFilterTypes.XCHECKBOX -> {
        val valueObj = value as? JsonObject
        JsMangaTriGroup(
            key,
            label,
            options,
            stringValues(valueObj?.get("include")),
            stringValues(valueObj?.get("exclude")),
        )
    }
    else -> null
}

internal fun jsDefsToAnimeFilterList(defs: List<JsFilterDef>): AnimeFilterList {
    return AnimeFilterList(defs.mapNotNull { def -> def.toAnimeFilter() })
}

private fun JsFilterDef.toAnimeFilter(): AnimeFilter<*>? = when (type) {
    JsFilterTypes.TEXT -> JsAnimeTextFilter(key, label, primitiveContent(value).orEmpty())
    JsFilterTypes.SWITCH -> JsAnimeCheckBox(
        key = key,
        jsType = JsFilterTypes.SWITCH,
        name = label,
        state = primitiveBoolean(value),
    )
    JsFilterTypes.PICKER -> JsAnimePickerFilter(
        key,
        label,
        options,
        pickerIndex(options, value),
    )
    JsFilterTypes.CHECKBOX -> JsAnimeCheckGroup(key, label, options, stringValues(value))
    JsFilterTypes.XCHECKBOX -> {
        val valueObj = value as? JsonObject
        JsAnimeTriGroup(
            key,
            label,
            options,
            stringValues(valueObj?.get("include")),
            stringValues(valueObj?.get("exclude")),
        )
    }
    else -> null
}

// ------------------------------------------------------------------
// FilterList state → {key:{type,value}} values envelope
// ------------------------------------------------------------------

internal fun novelFilterValuesJson(filters: NovelFilterList): String {
    if (filters.isEmpty()) return "{}"
    val obj = buildJsonObject {
        filters.forEach { filter ->
            when (filter) {
                is JsNovelTextFilter -> putFilter(filter.key, JsFilterTypes.TEXT, JsonPrimitive(filter.state))
                is JsNovelSwitchFilter -> putFilter(filter.key, JsFilterTypes.SWITCH, JsonPrimitive(filter.state))
                is JsNovelPickerFilter -> putFilter(
                    filter.key,
                    JsFilterTypes.PICKER,
                    JsonPrimitive(filter.options.getOrNull(filter.state)?.value ?: ""),
                )
                is JsNovelCheckGroup -> putFilter(
                    filter.key,
                    JsFilterTypes.CHECKBOX,
                    checkedValues(filter.state, { it as? JsNovelCheckBox }, { it.state }, { it.value }),
                )
                is JsNovelTriGroup -> putFilter(
                    filter.key,
                    JsFilterTypes.XCHECKBOX,
                    triValues(filter.state, { it as? JsNovelTriBox }, { it.state }, { it.value }),
                )
                else -> Unit
            }
        }
    }
    return obj.toString()
}

internal fun mangaFilterValuesJson(filters: FilterList): String {
    if (filters.isEmpty()) return "{}"
    val obj = buildJsonObject {
        filters.forEach { filter ->
            when (filter) {
                is JsMangaTextFilter -> putFilter(filter.key, JsFilterTypes.TEXT, JsonPrimitive(filter.state))
                is JsMangaCheckBox -> putFilter(filter.key, filter.jsType, JsonPrimitive(filter.state))
                is JsMangaPickerFilter -> putFilter(
                    filter.key,
                    JsFilterTypes.PICKER,
                    JsonPrimitive(filter.options.getOrNull(filter.state)?.value ?: ""),
                )
                is JsMangaCheckGroup -> putFilter(
                    filter.key,
                    JsFilterTypes.CHECKBOX,
                    checkedValues(filter.state, { it as? JsMangaCheckBox }, { it.state }, { it.value }),
                )
                is JsMangaTriGroup -> putFilter(
                    filter.key,
                    JsFilterTypes.XCHECKBOX,
                    triValues(filter.state, { it as? JsMangaTriBox }, { it.state }, { it.value }),
                )
                else -> Unit
            }
        }
    }
    return obj.toString()
}

internal fun animeFilterValuesJson(filters: AnimeFilterList): String {
    if (filters.isEmpty()) return "{}"
    val obj = buildJsonObject {
        filters.forEach { filter ->
            when (filter) {
                is JsAnimeTextFilter -> putFilter(filter.key, JsFilterTypes.TEXT, JsonPrimitive(filter.state))
                is JsAnimeCheckBox -> putFilter(filter.key, filter.jsType, JsonPrimitive(filter.state))
                is JsAnimePickerFilter -> putFilter(
                    filter.key,
                    JsFilterTypes.PICKER,
                    JsonPrimitive(filter.options.getOrNull(filter.state)?.value ?: ""),
                )
                is JsAnimeCheckGroup -> putFilter(
                    filter.key,
                    JsFilterTypes.CHECKBOX,
                    checkedValues(filter.state, { it as? JsAnimeCheckBox }, { it.state }, { it.value }),
                )
                is JsAnimeTriGroup -> putFilter(
                    filter.key,
                    JsFilterTypes.XCHECKBOX,
                    triValues(filter.state, { it as? JsAnimeTriBox }, { it.state }, { it.value }),
                )
                else -> Unit
            }
        }
    }
    return obj.toString()
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putFilter(
    key: String,
    type: String,
    value: JsonElement,
) {
    putJsonObject(key) {
        put("type", JsonPrimitive(type))
        put("value", value)
    }
}

private fun <T> checkedValues(
    state: List<*>,
    cast: (Any?) -> T?,
    checked: (T) -> Boolean,
    valueOf: (T) -> String,
): JsonArray = JsonArray(
    state.mapNotNull(cast)
        .filter(checked)
        .map { JsonPrimitive(valueOf(it)) },
)

private fun <T> triValues(
    state: List<*>,
    cast: (Any?) -> T?,
    stateOf: (T) -> Int,
    valueOf: (T) -> String,
): JsonElement = buildJsonObject {
    val boxes = state.mapNotNull(cast)
    put(
        "include",
        JsonArray(
            boxes.filter { stateOf(it) == Filter.TriState.STATE_INCLUDE }
                .map { JsonPrimitive(valueOf(it)) },
        ),
    )
    put(
        "exclude",
        JsonArray(
            boxes.filter { stateOf(it) == Filter.TriState.STATE_EXCLUDE }
                .map { JsonPrimitive(valueOf(it)) },
        ),
    )
}

// ------------------------------------------------------------------
// Descriptor helpers
// ------------------------------------------------------------------

private fun primitiveContent(value: JsonElement?): String? =
    (value as? JsonPrimitive)?.contentOrNull

private fun primitiveBoolean(value: JsonElement?): Boolean {
    val primitive = value as? JsonPrimitive ?: return false
    primitive.booleanOrNull?.let { return it }
    return primitive.contentOrNull?.equals("true", ignoreCase = true) == true
}

private fun pickerIndex(options: List<JsFilterOption>, value: JsonElement?): Int {
    val defaultValue = primitiveContent(value)
    return when {
        defaultValue.isNullOrBlank() -> -1
        else -> options.indexOfFirst { it.value == defaultValue }.takeIf { it >= 0 } ?: 0
    }
}

private fun stringValues(value: JsonElement?): Set<String> = when (value) {
    is JsonArray -> value.asSequence()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    is JsonPrimitive -> delimitedValues(value.contentOrNull).toSet()
    else -> emptySet()
}

private fun delimitedValues(value: String?): List<String> {
    val raw = value?.trim().orEmpty()
    if (raw.isEmpty()) return emptyList()
    if (',' !in raw && ';' !in raw && '|' !in raw) return listOf(raw)
    return raw.split(',', ';', '|')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

// ------------------------------------------------------------------
// Concrete filter subclasses carrying plugin key/type metadata
// ------------------------------------------------------------------

// --- Novel (full hierarchy: Switch + XCheckBox exist) ---

internal class JsNovelTextFilter(
    val key: String,
    name: String,
    state: String,
) : NovelFilter.Text(name, state)

internal class JsNovelSwitchFilter(
    val key: String,
    name: String,
    state: Boolean,
) : NovelFilter.Switch(name, state)

internal class JsNovelPickerFilter(
    val key: String,
    name: String,
    val options: List<JsFilterOption>,
    state: Int,
) : NovelFilter.Picker<String>(name, options.map { it.label }.toTypedArray(), state)

internal class JsNovelCheckBox(
    val value: String,
    name: String,
    state: Boolean,
) : NovelFilter.CheckBox(name, state)

internal class JsNovelCheckGroup(
    val key: String,
    name: String,
    options: List<JsFilterOption>,
    selected: Set<String>,
) : NovelFilter.Group<JsNovelCheckBox>(
    name,
    options.map { JsNovelCheckBox(it.value, it.label, it.value in selected) },
)

internal class JsNovelTriBox(
    val value: String,
    name: String,
    state: Int,
) : NovelFilter.XCheckBox(name, state)

internal class JsNovelTriGroup(
    val key: String,
    name: String,
    options: List<JsFilterOption>,
    include: Set<String>,
    exclude: Set<String>,
) : NovelFilter.Group<JsNovelTriBox>(
    name,
    options.map { option ->
        JsNovelTriBox(
            value = option.value,
            name = option.label,
            state = when (option.value) {
                in include -> NovelFilter.TriState.STATE_INCLUDE
                in exclude -> NovelFilter.TriState.STATE_EXCLUDE
                else -> NovelFilter.TriState.STATE_IGNORE
            },
        )
    },
)

// --- Manga (Switch→CheckBox, XCheckbox→TriState group) ---

internal class JsMangaTextFilter(
    val key: String,
    name: String,
    state: String,
) : Filter.Text(name, state)

internal class JsMangaCheckBox(
    val key: String,
    val jsType: String,
    name: String,
    state: Boolean,
    /** Option value for group members; unused on standalone Switch rows. */
    var value: String = "",
) : Filter.CheckBox(name, state)

internal class JsMangaPickerFilter(
    val key: String,
    name: String,
    val options: List<JsFilterOption>,
    state: Int,
) : Filter.Select<String>(name, options.map { it.label }.toTypedArray(), state)

internal class JsMangaCheckGroup(
    val key: String,
    name: String,
    options: List<JsFilterOption>,
    selected: Set<String>,
) : Filter.Group<JsMangaCheckBox>(
    name,
    options.map {
        JsMangaCheckBox(key, JsFilterTypes.CHECKBOX, it.label, it.value in selected, it.value)
    },
)

internal class JsMangaTriBox(
    val value: String,
    name: String,
    state: Int,
) : Filter.TriState(name, state)

internal class JsMangaTriGroup(
    val key: String,
    name: String,
    options: List<JsFilterOption>,
    include: Set<String>,
    exclude: Set<String>,
) : Filter.Group<JsMangaTriBox>(
    name,
    options.map { option ->
        JsMangaTriBox(
            value = option.value,
            name = option.label,
            state = when (option.value) {
                in include -> Filter.TriState.STATE_INCLUDE
                in exclude -> Filter.TriState.STATE_EXCLUDE
                else -> Filter.TriState.STATE_IGNORE
            },
        )
    },
)

// --- Anime (same mapping as manga) ---

internal class JsAnimeTextFilter(
    val key: String,
    name: String,
    state: String,
) : AnimeFilter.Text(name, state)

internal class JsAnimeCheckBox(
    val key: String,
    val jsType: String,
    name: String,
    state: Boolean,
    /** Option value for group members; unused on standalone Switch rows. */
    var value: String = "",
) : AnimeFilter.CheckBox(name, state)

internal class JsAnimePickerFilter(
    val key: String,
    name: String,
    val options: List<JsFilterOption>,
    state: Int,
) : AnimeFilter.Select<String>(name, options.map { it.label }.toTypedArray(), state)

internal class JsAnimeCheckGroup(
    val key: String,
    name: String,
    options: List<JsFilterOption>,
    selected: Set<String>,
) : AnimeFilter.Group<JsAnimeCheckBox>(
    name,
    options.map {
        JsAnimeCheckBox(key, JsFilterTypes.CHECKBOX, it.label, it.value in selected, it.value)
    },
)

internal class JsAnimeTriBox(
    val value: String,
    name: String,
    state: Int,
) : AnimeFilter.TriState(name, state)

internal class JsAnimeTriGroup(
    val key: String,
    name: String,
    options: List<JsFilterOption>,
    include: Set<String>,
    exclude: Set<String>,
) : AnimeFilter.Group<JsAnimeTriBox>(
    name,
    options.map { option ->
        JsAnimeTriBox(
            value = option.value,
            name = option.label,
            state = when (option.value) {
                in include -> AnimeFilter.TriState.STATE_INCLUDE
                in exclude -> AnimeFilter.TriState.STATE_EXCLUDE
                else -> AnimeFilter.TriState.STATE_IGNORE
            },
        )
    },
)
