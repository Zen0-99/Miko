package eu.kanade.tachiyomi.ui.storage.manga

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.storage.CommonStorageScreenModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.GetVisibleMangaCollections
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaStorageScreenModel(
    downloadCache: MangaDownloadCache = Injekt.get(),
    private val getLibraries: GetLibraryManga = Injekt.get(),
    getCollections: GetMangaCollections = Injekt.get(),
    getVisibleCollections: GetVisibleMangaCollections = Injekt.get(),
    private val downloadManager: MangaDownloadManager = Injekt.get(),
    private val sourceManager: MangaSourceManager = Injekt.get(),
) : CommonStorageScreenModel<LibraryManga>(
    downloadCacheChanges = downloadCache.changes,
    downloadCacheIsInitializing = downloadCache.isInitializing,
    libraries = getLibraries.subscribe(),
    collections = { hideHiddenCollections ->
        if (hideHiddenCollections) {
            getVisibleCollections.subscribe()
        } else {
            getCollections.subscribe()
        }
    },
    getDownloadSize = { downloadManager.getDownloadSize(manga) },
    getDownloadCount = { downloadManager.getDownloadCount(manga) },
    getId = { id },
    getCollectionId = { collection },
    getTitle = { manga.title },
    getThumbnail = { manga.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        screenModelScope.launchNonCancellable {
            val manga = getLibraries.await().find {
                it.id == id
            }?.manga ?: return@launchNonCancellable
            val source = sourceManager.get(manga.source) ?: return@launchNonCancellable
            downloadManager.deleteManga(manga, source)
        }
    }
}
