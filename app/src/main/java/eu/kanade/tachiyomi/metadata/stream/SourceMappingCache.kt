package eu.kanade.tachiyomi.metadata.stream

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Caches the mapping between a Cinemeta anime entry and the source anime
 * the user selected for streaming. This lets us skip the stream picker
 * on subsequent plays.
 *
 * Keyed by the Cinemeta anime URL (e.g. "cinemeta:series:tt1234567").
 */
class SourceMappingCache(
    private val preferenceStore: PreferenceStore,
) {
    @Serializable
    data class SourceMapping(
        val sourceId: Long,
        val sourceAnimeUrl: String,
        val sourceAnimeId: Long,
        val sourceName: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val mappingsPref = preferenceStore.getObject(
        key = "cinemeta_source_mappings",
        defaultValue = emptyMap<String, SourceMapping>(),
        serializer = { map ->
            if (map.isEmpty()) "" else json.encodeToString(serializer(), map)
        },
        deserializer = { str ->
            if (str.isBlank()) emptyMap() else json.decodeFromString(serializer(), str)
        },
    )

    private fun serializer() = MapSerializer(
        String.serializer(),
        SourceMapping.serializer(),
    )

    fun get(cinemetaUrl: String): SourceMapping? {
        return mappingsPref.get()[cinemetaUrl]
    }

    fun put(cinemetaUrl: String, mapping: SourceMapping) {
        val current = mappingsPref.get().toMutableMap()
        current[cinemetaUrl] = mapping
        mappingsPref.set(current)
    }

    fun remove(cinemetaUrl: String) {
        val current = mappingsPref.get().toMutableMap()
        current.remove(cinemetaUrl)
        mappingsPref.set(current)
    }
}
