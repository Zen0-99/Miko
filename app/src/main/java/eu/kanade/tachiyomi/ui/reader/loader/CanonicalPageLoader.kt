package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.items.chapter.model.toDbChapter
import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter
import eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CompletableDeferred
import tachiyomi.domain.entries.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Page loader for canonical `canon:{workId}:{seq}` chapters (13-04):
 * resolves the best installed carrier via [CanonicalChapterRouter]
 * (stickiness → tier → failover), then delegates per-page image
 * loading to a carrier-bound [HttpPageLoader] so headers, data-saver,
 * and the chapter cache behave exactly like reading that source
 * directly.
 *
 * Throws [NoCarrierException] when no installed golden source serves
 * the chapter — the reader's error surface handles it as an ordinary
 * load failure.
 */
internal class CanonicalPageLoader(
    private val chapter: ReaderChapter,
    private val manga: Manga,
    private val router: CanonicalChapterRouter = Injekt.get(),
) : PageLoader() {

    override var isLocal: Boolean = false

    // Page holders can call loadPage the moment ChapterLoader assigns
    // chapter.pageLoader — before getPages finishes carrier resolution.
    // They must await the delegate, not crash the dispatcher.
    private var delegate: HttpPageLoader? = null
    private val delegateReady = CompletableDeferred<HttpPageLoader>()

    override suspend fun getPages(): List<ReaderPage> {
        try {
            val resolved = router.resolveForRead(
                manga,
                chapter.chapter.url,
                chapter.chapter.name,
            ) ?: throw NoCarrierException(chapter.chapter.name)
            sourceName = resolved.source.name

            // Rebuild the reader chapter with the carrier's real URL so the
            // carrier's HttpSource resolves image/page paths correctly.
            val routed = chapter.chapter.toDomainChapter()
                ?.copy(url = resolved.chapter.url)
                ?.let { ReaderChapter(it.toDbChapter()) }
                ?: chapter
            val carrier = HttpPageLoader(routed, resolved.source)
            delegate = carrier
            delegateReady.complete(carrier)

            return resolved.pages.mapIndexed { index, page ->
                ReaderPage(index, page.url, page.imageUrl)
            }
        } catch (e: Throwable) {
            delegateReady.completeExceptionally(e)
            throw e
        }
    }

    override suspend fun loadPage(page: ReaderPage) {
        delegateReady.await().loadPage(page)
    }

    override fun retryPage(page: ReaderPage) {
        delegate?.retryPage(page)
    }

    override fun recycle() {
        delegate?.recycle()
        super.recycle()
    }
}

/** No installed source carries this canonical chapter. */
class NoCarrierException(chapterName: String) :
    Exception("No installed source carries \"$chapterName\"")
