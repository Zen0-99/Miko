package mihon.domain.items.chapter.interactor

import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Interactor responsible for determining which chapters of a manga should be downloaded.
 *
 * @property getChaptersByMangaId Interactor for retrieving chapters by manga ID.
 * @property downloadPreferences User preferences related to chapter downloads.
 * @property getCollections Interactor for retrieving collections associated with a manga.
 */
class FilterChaptersForDownload(
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val downloadPreferences: DownloadPreferences,
    private val getCollections: GetMangaCollections,
) {

    /**
     * Determines which chapters of a manga should be downloaded based on user preferences.
     *
     * @param manga The manga for which chapters may be downloaded.
     * @param newChapters The list of new chapters available for the manga.
     * @return A list of chapters that should be downloaded
     */
    suspend fun await(manga: Manga, newChapters: List<Chapter>): List<Chapter> {
        if (
            newChapters.isEmpty() ||
            !downloadPreferences.downloadNewChapters().get() ||
            !manga.shouldDownloadNewChapters()
        ) {
            return emptyList()
        }
        if (!downloadPreferences.downloadNewUnreadChaptersOnly().get()) return newChapters
        val readChapterNumbers = getChaptersByMangaId.await(manga.id)
            .asSequence()
            .filter { it.read && it.isRecognizedNumber }
            .map { it.chapterNumber }
            .toSet()
        return newChapters.filterNot { it.chapterNumber in readChapterNumbers }
    }

    /**
     * Determines whether new chapters should be downloaded for the manga based on user preferences and the
     * collections to which the manga belongs.
     *
     * @return `true` if chapters of the manga should be downloaded
     */
    private suspend fun Manga.shouldDownloadNewChapters(): Boolean {
        if (!favorite) return false
        val collections = getCollections.await(id).map { it.id }.ifEmpty { listOf(DEFAULT_COLLECTION_ID) }
        val includedCollections = downloadPreferences.downloadNewChapterCollections().get().map { it.toLong() }
        val excludedCollections = downloadPreferences.downloadNewChapterCollectionsExclude().get().map { it.toLong() }
        return when {
            // Default Download from all collections
            includedCollections.isEmpty() && excludedCollections.isEmpty() -> true
            // In excluded collection
            collections.any { it in excludedCollections } -> false
            // Included collection not selected
            includedCollections.isEmpty() -> true
            // In included collection
            else -> collections.any { it in includedCollections }
        }
    }

    companion object {
        private const val DEFAULT_COLLECTION_ID = 0L
    }
}
