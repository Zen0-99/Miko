package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class LegacyBackup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) var backupCollections: List<BackupCollection> = emptyList(),
    @ProtoNumber(3) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(4) var backupAnimeCollections: List<BackupCollection> = emptyList(),
    // Bump by 100 to specify this is a 0.x value
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    // @ProtoNumber(102) var backupBrokenAnimeSources, legacy source model with non-compliant proto number,
    @ProtoNumber(103) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(107) var backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(108) var backupMangaExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(109) var backupCustomButton: List<BackupCustomButtons> = emptyList(),
    // Novel specific values
    @ProtoNumber(600) var backupNovels: List<BackupNovel> = emptyList(),
    @ProtoNumber(601) var backupNovelCollections: List<BackupCollection> = emptyList(),
    @ProtoNumber(602) var backupNovelSources: List<BackupNovelSource> = emptyList(),
) {
    fun toBackup(): Backup {
        return Backup(
            backupManga = backupManga,
            backupCollections = backupCollections,
            backupSources = backupSources,
            backupPreferences = backupPreferences,
            backupSourcePreferences = backupSourcePreferences,
            backupMangaExtensionRepo = backupMangaExtensionRepo,

            isLegacy = false, // Only used for detection
            backupAnime = backupAnime,
            backupAnimeCollections = backupAnimeCollections,
            backupAnimeSources = backupAnimeSources,
            backupExtensions = backupExtensions,
            backupAnimeExtensionRepo = backupAnimeExtensionRepo,
            backupCustomButton = backupCustomButton,

            backupNovels = backupNovels,
            backupNovelCollection = backupNovelCollections,
            backupNovelSources = backupNovelSources,
            backupNovelLinks = emptyList(),
        )
    }
}

@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) var backupCollections: List<BackupCollection> = emptyList(),
    // @ProtoNumber(100) var backupBrokenSources, legacy source model with non-compliant proto number,
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(106) var backupMangaExtensionRepo: List<BackupExtensionRepos> = emptyList(),

    // Aniyomi specific values
    @ProtoNumber(500) val isLegacy: Boolean = true,
    @ProtoNumber(501) val backupAnime: List<BackupAnime> = emptyList(),
    @ProtoNumber(502) var backupAnimeCollections: List<BackupCollection> = emptyList(),
    @ProtoNumber(503) var backupAnimeSources: List<BackupAnimeSource> = emptyList(),
    @ProtoNumber(504) var backupExtensions: List<BackupExtension> = emptyList(),
    @ProtoNumber(505) var backupAnimeExtensionRepo: List<BackupExtensionRepos> = emptyList(),
    @ProtoNumber(506) var backupCustomButton: List<BackupCustomButtons> = emptyList(),

    // Novel specific values
    @ProtoNumber(600) val backupNovels: List<BackupNovel> = emptyList(),
    @ProtoNumber(601) var backupNovelCollection: List<BackupCollection> = emptyList(),
    @ProtoNumber(602) var backupNovelSources: List<BackupNovelSource> = emptyList(),
    @ProtoNumber(603) var backupNovelLinks: List<BackupNovelLink> = emptyList(),
)
