package eu.kanade.tachiyomi.data.backup.create.creators

import android.content.Context
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.data.backup.models.BackupExtension
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import kotlinx.coroutines.flow.first
import uy.kohesive.injekt.injectLazy
import java.io.File

class ExtensionsBackupCreator(
    private val context: Context,
) {

    private val animeExtensionManager: AnimeExtensionManager by injectLazy()
    private val mangaExtensionManager: MangaExtensionManager by injectLazy()

    suspend operator fun invoke(): List<BackupExtension> {
        animeExtensionManager.isInitialized.first { it }
        mangaExtensionManager.isInitialized.first { it }
        val installedExtensions = mutableListOf<BackupExtension>()
        animeExtensionManager.installedExtensionsFlow.value.forEach {
            val packageName = it.pkgName
            val apk = File(
                context.packageManager
                    .getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA,
                    ).publicSourceDir,
            ).readBytes()
            installedExtensions.add(
                BackupExtension(packageName, apk),
            )
        }
        mangaExtensionManager.installedExtensionsFlow.value.forEach {
            val packageName = it.pkgName
            val apk = File(
                context.packageManager
                    .getApplicationInfo(
                        packageName,
                        PackageManager.GET_META_DATA,
                    ).publicSourceDir,
            ).readBytes()
            installedExtensions.add(
                BackupExtension(packageName, apk),
            )
        }
        return installedExtensions
    }
}
