package eu.kanade.tachiyomi.extension.novel.runtime

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.data.extension.novel.NovelPluginKeyValueStore

class NovelPluginSettingsBridge(
    private val pluginId: String,
    private val keyValueStore: NovelPluginKeyValueStore,
    private val json: Json,
) {
    private var schema: List<PluginSettingDefinition> = emptyList()

    fun parseSettingsSchema(schemaJson: String): List<PluginSettingDefinition> {
        if (schemaJson.isBlank()) return emptyList()

        val element = runCatching { json.parseToJsonElement(schemaJson) }.getOrNull() ?: return emptyList()

        return when (element) {
            is JsonArray -> element.mapNotNull { item ->
                parseLegacySettingDefinition(item as? JsonObject)
            }
            is JsonObject -> element.mapNotNull { (key, value) ->
                parseLnReaderSettingDefinition(
                    key = key,
                    item = value as? JsonObject,
                )
            }
            else -> emptyList()
        }
    }

    fun loadSettingsSchema(schemaJson: String) {
        schema = parseSettingsSchema(schemaJson)
    }

    fun clearSettingsSchema() {
        schema = emptyList()
    }

    fun setSetting(key: String, value: String) {
        storeSettingValue(key, JsonPrimitive(value))
    }

    fun setSettingValues(key: String, values: Collection<String>) {
        storeSettingValue(
            key = key,
            value = JsonArray(values.map { JsonPrimitive(it) }),
        )
    }

    fun syncSettingToStorage(key: String, value: String) {
        storeStorageValue(key, JsonPrimitive(value))
    }

    fun syncSettingValuesToStorage(key: String, values: Collection<String>) {
        storeStorageValue(key, JsonArray(values.map { JsonPrimitive(it) }))
    }

    fun getSetting(key: String): String? {
        return readSettingValue(key)?.let { displayValue(it) }
    }

    fun getSettingWithDefault(key: String): String? {
        val stored = getSetting(key)
        if (stored != null) return stored

        val definition = schema.find { it.key == key } ?: return null
        val defaultValue = definition.default ?: return null

        return displayValue(defaultValue)
    }

    fun getSettingBooleanWithDefault(key: String): Boolean {
        readSettingValue(key)?.let { value ->
            return when (value) {
                is JsonPrimitive ->
                    value.booleanOrNull
                        ?: value.contentOrNull?.equals("true", ignoreCase = true) == true
                else -> value.toString().equals("true", ignoreCase = true)
            }
        }

        val definition = schema.find { it.key == key } ?: return false
        val defaultValue = definition.default as? JsonPrimitive ?: return false
        return defaultValue.booleanOrNull
            ?: defaultValue.contentOrNull?.equals("true", ignoreCase = true) == true
    }

    fun getSettingValues(key: String): Set<String>? {
        val stored = readSettingValue(key) ?: return null
        return decodeStringSet(stored)
    }

    fun getSettingValuesWithDefault(key: String): Set<String> {
        getSettingValues(key)?.let { return it }

        val definition = schema.find { it.key == key } ?: return emptySet()
        return decodeStringSet(definition.default).orEmpty()
    }

    fun getAllSettings(): Map<String, String> {
        val keys = keyValueStore.keys(pluginId)
        return keys.associateWith { storedKey ->
            val key = storedKey.removePrefix(SETTING_PREFIX)
            readStoredPayloadValue(key)?.let { displayValue(it) } ?: ""
        }.mapKeys { (storedKey, _) ->
            storedKey.removePrefix(SETTING_PREFIX)
        }
    }

    fun removeSetting(key: String) {
        keyValueStore.remove(pluginId, namespacedKey(key))
    }

    fun clearSettings() {
        keyValueStore.clear(pluginId)
    }

    private fun namespacedKey(key: String): String {
        return SETTING_PREFIX + key
    }

    private fun storeStorageValue(key: String, value: JsonElement) {
        keyValueStore.set(
            pluginId,
            STORAGE_PREFIX + key,
            json.encodeToString(
                StoredSettingPayload.serializer(),
                StoredSettingPayload(
                    value = value,
                    created = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private fun storeSettingValue(key: String, value: JsonElement) {
        keyValueStore.set(
            pluginId,
            namespacedKey(key),
            json.encodeToString(
                StoredSettingPayload.serializer(),
                StoredSettingPayload(
                    value = value,
                    created = System.currentTimeMillis(),
                ),
            ),
        )
    }

    private fun readSettingValue(key: String): JsonElement? {
        return readStoredPayloadValue(key)
    }

    private fun readStoredPayloadValue(key: String): JsonElement? {
        val raw = keyValueStore.get(pluginId, namespacedKey(key)) ?: return null
        val parsed = runCatching { json.parseToJsonElement(raw) }.getOrNull()
        return when (parsed) {
            is JsonObject -> parsed["value"] ?: parsed
            null -> JsonPrimitive(raw)
            else -> parsed
        }
    }

    private fun decodeStringSet(value: JsonElement?): Set<String>? {
        return when (value) {
            is JsonArray -> value.mapNotNull { element ->
                (element as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
            }.toSet()
            is JsonPrimitive -> {
                val content = value.contentOrNull?.trim().orEmpty()
                if (content.isEmpty()) {
                    emptySet()
                } else {
                    when {
                        content.equals("true", ignoreCase = true) ||
                            content.equals("false", ignoreCase = true) -> emptySet()
                        content.startsWith("[") && content.endsWith("]") -> {
                            val parsed = runCatching { json.parseToJsonElement(content) }.getOrNull()
                            decodeStringSet(parsed)
                        }
                        content.contains(',') || content.contains(';') || content.contains('|') -> {
                            content.split(',', ';', '|')
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()
                        }
                        else -> setOf(content)
                    }
                }
            }
            else -> null
        }
    }

    private fun displayValue(value: JsonElement): String {
        return when (value) {
            is JsonPrimitive -> value.contentOrNull ?: ""
            is JsonArray -> value.joinToString(", ") { (it as? JsonPrimitive)?.contentOrNull ?: "" }
            is JsonObject -> value.toString()
            JsonNull -> ""
            else -> value.toString()
        }
    }

    private fun parseLegacySettingDefinition(item: JsonObject?): PluginSettingDefinition? {
        if (item == null) return null
        val key = item["key"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = item["label"]?.jsonPrimitive?.contentOrNull
            ?: item["title"]?.jsonPrimitive?.contentOrNull
            ?: key
        val type = item["type"]?.jsonPrimitive?.contentOrNull ?: "text"
        val options = (item["options"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { opt ->
                val label = opt["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val value = opt["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                PluginSettingOption(label, value)
            }}
        val default = item["default"] ?: item["value"]
        return PluginSettingDefinition(
            key = key,
            title = title,
            type = type,
            options = options,
            default = default,
        )
    }

    private fun parseLnReaderSettingDefinition(key: String, item: JsonObject?): PluginSettingDefinition? {
        if (item == null) return null
        val title = item["label"]?.jsonPrimitive?.contentOrNull ?: key
        val type = item["type"]?.jsonPrimitive?.contentOrNull ?: "text"
        val options = (item["options"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let { opt ->
                val label = opt["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val value = opt["value"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                PluginSettingOption(label, value)
            }}
        val default = item["default"] ?: item["value"]
        return PluginSettingDefinition(
            key = key,
            title = title,
            type = type,
            options = options,
            default = default,
        )
    }

    @Serializable
    private data class StoredSettingPayload(
        val value: JsonElement,
        val created: Long,
    )

    private companion object {
        const val SETTING_PREFIX = "setting:"
        const val STORAGE_PREFIX = "storage:"
    }
}

data class PluginSettingDefinition(
    val key: String,
    val title: String,
    val type: String,
    val options: List<PluginSettingOption>? = null,
    val values: List<String>? = null,
    val default: JsonElement? = null,
)

data class PluginSettingOption(
    val label: String,
    val value: String,
)
