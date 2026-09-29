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

    /**
     * Links a Cinemeta virtual episode to the real source episode a
     * download was queued from — lets the Cinemeta detail view reflect
     * queue/downloaded state and route cancel/delete to the right entry.
     */
    @Serializable
    data class EpisodeDownloadRef(
        val sourceId: Long,
        val sourceAnimeId: Long,
        val sourceEpisodeId: Long,
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

    private val episodeDownloadsPref = preferenceStore.getObject(
        key = "cinemeta_episode_downloads",
        defaultValue = emptyMap<String, EpisodeDownloadRef>(),
        serializer = { map ->
            if (map.isEmpty()) "" else json.encodeToString(episodeDownloadSerializer(), map)
        },
        deserializer = { str ->
            if (str.isBlank()) emptyMap() else json.decodeFromString(episodeDownloadSerializer(), str)
        },
    )

    private fun episodeDownloadSerializer() = MapSerializer(
        String.serializer(),
        EpisodeDownloadRef.serializer(),
    )

    /** cinemetaEpisodeId → the source episode the download was queued from. */
    fun episodeDownloadRefs(): Map<Long, EpisodeDownloadRef> {
        return episodeDownloadsPref.get()
            .mapKeysNotNull { it.key.toLongOrNull() }
    }

    fun episodeDownloadRef(cinemetaEpisodeId: Long): EpisodeDownloadRef? {
        return episodeDownloadsPref.get()[cinemetaEpisodeId.toString()]
    }

    fun putEpisodeDownload(cinemetaEpisodeId: Long, ref: EpisodeDownloadRef) {
        val current = episodeDownloadsPref.get().toMutableMap()
        current[cinemetaEpisodeId.toString()] = ref
        episodeDownloadsPref.set(current)
    }

    fun removeEpisodeDownload(cinemetaEpisodeId: Long) {
        val current = episodeDownloadsPref.get().toMutableMap()
        if (current.remove(cinemetaEpisodeId.toString()) != null) {
            episodeDownloadsPref.set(current)
        }
    }

    /** Reverse lookup — the Cinemeta episode a source-episode download belongs to. */
    fun cinemetaEpisodeIdFor(sourceEpisodeId: Long): Long? {
        return episodeDownloadRefs()
            .entries
            .firstOrNull { it.value.sourceEpisodeId == sourceEpisodeId }
            ?.key
    }

    private inline fun <K, V, R : Any> Map<K, V>.mapKeysNotNull(transform: (Map.Entry<K, V>) -> R?): Map<R, V> {
        val result = LinkedHashMap<R, V>(size)
        for (entry in this) {
            val key = transform(entry) ?: continue
            result[key] = entry.value
        }
        return result
    }

    fun get(cinemetaUrl: String): SourceMapping? {
        return mappingsPref.get()[cinemetaUrl]
    }

    /** All cached source anime ids — protected from junk-row cleanup. */
    fun allMappedAnimeIds(): Set<Long> {
        return mappingsPref.get().values.map { it.sourceAnimeId }.toSet()
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
