package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.collection.shows.interactor.GetAnimeCollections
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class CollectionPreferencesCleanupMigration : Migration {
    override val version: Float = 129f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val libraryPreferences = migrationContext.get<LibraryPreferences>() ?: return@withIOContext false
        val downloadPreferences = migrationContext.get<DownloadPreferences>() ?: return@withIOContext false

        val getAnimeCollections = migrationContext.get<GetAnimeCollections>() ?: return@withIOContext false
        val getMangaCollections = migrationContext.get<GetMangaCollections>() ?: return@withIOContext false
        val allAnimeCollections = getAnimeCollections.await().map { it.id.toString() }.toSet()
        val allMangaCollections = getMangaCollections.await().map { it.id.toString() }.toSet()

        val defaultShowsCollection = libraryPreferences.defaultShowsCollection().get()
        if (defaultShowsCollection.toString() !in allAnimeCollections) {
            libraryPreferences.defaultShowsCollection().delete()
        }
        val defaultMangaCollection = libraryPreferences.defaultMangaCollection().get()
        if (defaultMangaCollection.toString() !in allMangaCollections) {
            libraryPreferences.defaultMangaCollection().delete()
        }

        val collectionPreferences = listOf(
            libraryPreferences.showsUpdateCollections(),
            libraryPreferences.mangaUpdateCollections(),
            libraryPreferences.showsUpdateCollectionsExclude(),
            libraryPreferences.mangaUpdateCollectionsExclude(),
            downloadPreferences.removeExcludeCollections(),
            downloadPreferences.removeExcludeAnimeCollections(),
            downloadPreferences.downloadNewChapterCollections(),
            downloadPreferences.downloadNewEpisodeCollections(),
            downloadPreferences.downloadNewChapterCollectionsExclude(),
            downloadPreferences.downloadNewEpisodeCollectionsExclude(),
        )
        collectionPreferences.forEach { preference ->
            val ids = preference.get()
            val garbageIds = ids
                .minus(allAnimeCollections)
                .minus(allMangaCollections)
            if (garbageIds.isEmpty()) return@forEach
            preference.set(ids.minus(garbageIds))
        }
        return@withIOContext true
    }
}
