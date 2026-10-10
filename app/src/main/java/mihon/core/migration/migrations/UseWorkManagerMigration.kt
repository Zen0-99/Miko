package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.shows.ShowsLibraryUpdateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class UseWorkManagerMigration : Migration {
    override val version = 96f

    // Fully utilize WorkManager for library updates
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false

        MangaLibraryUpdateJob.cancelAllWorks(context)
        ShowsLibraryUpdateJob.cancelAllWorks(context)
        MangaLibraryUpdateJob.setupTask(context)
        ShowsLibraryUpdateJob.setupTask(context)

        return true
    }
}
