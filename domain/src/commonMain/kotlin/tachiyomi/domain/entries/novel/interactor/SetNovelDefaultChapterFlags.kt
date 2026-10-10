package tachiyomi.domain.entries.novel.interactor

import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.library.service.LibraryPreferences

class SetNovelDefaultChapterFlags(
    private val libraryPreferences: LibraryPreferences,
    private val setNovelChapterFlags: SetNovelChapterFlags,
    private val getFavorites: GetNovelFavorites,
) {

    suspend fun await(novel: Novel) {
        withNonCancellableContext {
            with(libraryPreferences) {
                setNovelChapterFlags.awaitSetAllFlags(
                    novel = novel,
                    unreadFilter = filterChapterByRead().get(),
                    downloadedFilter = filterChapterByDownloaded().get(),
                    bookmarkedFilter = filterChapterByBookmarked().get(),
                    sortingMode = sortChapterBySourceOrNumber().get(),
                    sortingDirection = sortChapterByAscendingOrDescending().get(),
                    displayMode = displayChapterByNameOrNumber().get(),
                )
            }
        }
    }

    suspend fun awaitAll() {
        withNonCancellableContext {
            getFavorites.await().forEach { await(it) }
        }
    }
}
