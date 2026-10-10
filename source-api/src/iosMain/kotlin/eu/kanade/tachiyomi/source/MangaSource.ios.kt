package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * iOS contract — suspend-first, no RxJava.
 */
actual interface MangaSource {

    actual val id: Long

    actual val name: String

    val lang: String
        get() = ""

    suspend fun getMangaDetails(manga: SManga): SManga

    suspend fun getChapterList(manga: SManga): List<SChapter>

    suspend fun getPageList(chapter: SChapter): List<Page>
}
