package mihon.core.migration.migrations

import android.app.Application
import eu.kanade.tachiyomi.data.library.shows.ShowsLibraryUpdateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class SetupAnimeLibraryUpdateMigration : Migration {
    override val version: Float = Migration.ALWAYS

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false

        ShowsLibraryUpdateJob.setupTask(context)

        return true
    }
}
