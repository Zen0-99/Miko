package eu.kanade.tachiyomi.data.backup.restore

import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupNovelSource
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * Builds a mapping from backup source IDs to installed source IDs by matching
 * on normalized [baseUrl] (primary) or source name (fallback).
 *
 * This is used during backup restore to handle cross-format backups — e.g. a
 * backup from Tadami/Hayai (JS-based extensions with SHA-256(pluginId) source IDs)
 * imported into Miko (APK-based extensions with MD5(name/lang/versionId) source IDs).
 *
 * The mapping is built once at the start of restore and passed to each restorer,
 * which uses [mapSourceId] to translate backup source IDs to installed source IDs
 * before database lookups and inserts.
 *
 * Backups created by Miko include `baseUrl` in the backup source models.
 * Backups from other apps that don't include `baseUrl` fall back to name matching.
 */
class SourceIdMapper {

    /**
     * Build a mapping from backup manga source IDs to installed manga source IDs.
     */
    fun buildMangaMapping(
        backupSources: List<BackupSource>,
        sourceManager: MangaSourceManager,
    ): Map<Long, Long> {
        val installedSources = sourceManager.getOnlineSources()
        return buildMapping(
            backupSources = backupSources.associate { it.sourceId to SourceInfo(it.name, it.baseUrl) },
            installedSources = installedSources.associate { it.id to SourceInfo(it.name, (it as? HttpSource)?.baseUrl ?: "") },
        )
    }

    /**
     * Build a mapping from backup anime source IDs to installed anime source IDs.
     */
    fun buildAnimeMapping(
        backupSources: List<BackupAnimeSource>,
        sourceManager: AnimeSourceManager,
    ): Map<Long, Long> {
        val installedSources = sourceManager.getOnlineSources()
        return buildMapping(
            backupSources = backupSources.associate { it.sourceId to SourceInfo(it.name, it.baseUrl) },
            installedSources = installedSources.associate { it.id to SourceInfo(it.name, (it as? AnimeHttpSource)?.baseUrl ?: "") },
        )
    }

    /**
     * Build a mapping from backup novel source IDs to installed novel source IDs.
     */
    fun buildNovelMapping(
        backupSources: List<BackupNovelSource>,
        sourceManager: NovelSourceManager,
    ): Map<Long, Long> {
        val installedSources = sourceManager.getOnlineSources()
        return buildMapping(
            backupSources = backupSources.associate { it.sourceId to SourceInfo(it.name, it.baseUrl) },
            installedSources = installedSources.associate { it.id to SourceInfo(it.name, (it as? NovelHttpSource)?.baseUrl ?: "") },
        )
    }

    private fun buildMapping(
        backupSources: Map<Long, SourceInfo>,
        installedSources: Map<Long, SourceInfo>,
    ): Map<Long, Long> {
        val mapping = mutableMapOf<Long, Long>()

        // Index installed sources by normalized baseUrl and name for fast lookup
        val installedByBaseUrl = mutableMapOf<String, Long>()
        val installedByName = mutableMapOf<String, Long>()
        for ((id, info) in installedSources) {
            if (info.baseUrl.isNotBlank()) {
                installedByBaseUrl[normalizeBaseUrl(info.baseUrl)] = id
            }
            installedByName[info.name.lowercase().trim()] = id
        }

        for ((backupId, backupInfo) in backupSources) {
            // If the backup source ID already matches an installed source, no remapping needed
            if (backupId in installedSources) {
                mapping[backupId] = backupId
                continue
            }

            // Try baseUrl match first (most reliable)
            if (backupInfo.baseUrl.isNotBlank()) {
                val normalized = normalizeBaseUrl(backupInfo.baseUrl)
                installedByBaseUrl[normalized]?.let { installedId ->
                    mapping[backupId] = installedId
                    continue
                }
            }

            // Fall back to name match (case-insensitive)
            val nameKey = backupInfo.name.lowercase().trim()
            if (nameKey.isNotBlank()) {
                installedByName[nameKey]?.let { installedId ->
                    mapping[backupId] = installedId
                    continue
                }
            }

            // No match found — source ID stays as-is (will create stub source)
            mapping[backupId] = backupId
        }

        return mapping
    }

    private data class SourceInfo(val name: String, val baseUrl: String)

    companion object {
        /**
         * Normalize a baseUrl for comparison: remove trailing slash, lowercase,
         * strip "www." prefix, and remove protocol.
         */
        fun normalizeBaseUrl(url: String): String {
            var normalized = url.trim().lowercase()
            // Remove protocol
            normalized = normalized.removePrefix("https://").removePrefix("http://")
            // Remove www. prefix
            normalized = normalized.removePrefix("www.")
            // Remove trailing slash
            normalized = normalized.trimEnd('/')
            return normalized
        }
    }
}
