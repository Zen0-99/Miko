package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.domain.ui.model.Carrier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * App-scoped driver for the canonical enrich → canonize → sync chain
 * (13-02/13-03). Previously ran in `screenModelScope`, where navigating
 * away — or simply leaving an entry open long enough for the scope to
 * recycle — cancelled the chain mid-flight and `runCatching` swallowed
 * the CancellationException, leaving every canonical row chapter-less.
 *
 * Now the chain survives screen disposal: the entry screens only kick
 * off the work and observe results through their chapter flows. Dedupe
 * is per work id — repeat opens while a run is in flight are no-ops,
 * and finished runs re-check `CanonicalChapterMap.isStale` cheaply.
 */
class CanonicalPipelineRunner(
    private val mangaBakaEnrich: MangaBakaEnrich = Injekt.get(),
    private val canonizeWork: CanonizeWork = Injekt.get(),
    private val canonicalChapterSync: CanonicalChapterSync = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()

    /** Enrich → canonize → materialize chapters for a canonical manga row. */
    fun launchManga(workId: String, manga: Manga) {
        if (!running.add(workId)) return
        scope.launch {
            try {
                // Seeded rows arrive with empty provider_links — enrich
                // first so golden-source selection has links to bind.
                mangaBakaEnrich.run(workId, manga)
                canonizeWork.run(workId, Carrier.MANGA)
                canonicalChapterSync.sync(manga, workId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "[CanonicalPipeline] manga run failed for $workId" }
            } finally {
                running.remove(workId)
            }
        }
    }

    /** Same as [launchManga] for canonical novel/book rows. */
    fun launchNovel(workId: String, novel: Novel) {
        if (!running.add(workId)) return
        scope.launch {
            try {
                mangaBakaEnrich.run(workId, novel)
                canonizeWork.run(workId, Carrier.BOOKS)
                canonicalChapterSync.syncNovel(novel, workId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "[CanonicalPipeline] novel run failed for $workId" }
            } finally {
                running.remove(workId)
            }
        }
    }
}
