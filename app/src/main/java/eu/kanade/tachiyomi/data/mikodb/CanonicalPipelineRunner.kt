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
import tachiyomi.domain.entries.shows.model.Anime
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
    private val canonizer: LibraryCanonizer = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val lastRanAt = ConcurrentHashMap<String, Long>()

    /**
     * Re-open debounce — every detail open kicks a launch, and the
     * chain itself is internally freshness-gated (enrich tag, isStale),
     * but a full re-run still rewrites the chapter list under the
     * reader's eyes. Repeat launches inside this window are skipped.
     */
    private fun shouldRun(workId: String): Boolean {
        val last = lastRanAt[workId] ?: return true
        return System.currentTimeMillis() - last > DEBOUNCE_MS
    }

    /** Enrich → canonize → materialize chapters for a canonical manga row.
     *  [force] bypasses the re-open debounce for explicit user refreshes. */
    fun launchManga(workId: String, manga: Manga, force: Boolean = false) {
        if ((!force && !shouldRun(workId)) || !running.add(workId)) return
        scope.launch {
            try {
                var id = workId
                var row = manga
                // `mk:m:` rows are provisional local mints — re-resolve
                // so a work the catalog learned since minting retargets
                // onto its real id before enrich/canonize run.
                if (id.startsWith(WorkIdMint.PREFIX)) {
                    canonizer.promoteLocalMint(row)?.let { (m, newId) ->
                        row = m
                        id = newId
                    }
                }
                // Seeded rows arrive with empty provider_links — enrich
                // first so golden-source selection has links to bind.
                mangaBakaEnrich.run(id, row)
                canonizeWork.run(id, Carrier.MANGA)
                canonicalChapterSync.sync(row, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "[CanonicalPipeline] manga run failed for $workId" }
            } finally {
                lastRanAt[workId] = System.currentTimeMillis()
                running.remove(workId)
            }
        }
    }

    /** Same as [launchManga] for canonical novel/book rows. */
    fun launchNovel(workId: String, novel: Novel, force: Boolean = false) {
        if ((!force && !shouldRun(workId)) || !running.add(workId)) return
        scope.launch {
            try {
                var id = workId
                var row = novel
                if (id.startsWith(WorkIdMint.PREFIX)) {
                    canonizer.promoteLocalMint(row)?.let { (n, newId) ->
                        row = n
                        id = newId
                    }
                }
                mangaBakaEnrich.run(id, row)
                canonizeWork.run(id, Carrier.BOOKS)
                canonicalChapterSync.syncNovel(row, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "[CanonicalPipeline] novel run failed for $workId" }
            } finally {
                lastRanAt[workId] = System.currentTimeMillis()
                running.remove(workId)
            }
        }
    }

    /**
     * Shows leg (19-03): canonize episodes for a canonical shows row.
     *
     * Unlike manga/books there is no enrich step — shows works resolve
     * `mk:imdb:`/`mk:hs:` ids server-side (the works_cache row is
     * already the resolve output), there is no MangaBaka-equivalent
     * catalog for anime to fetch, and no `mk:m:`-promotion analog
     * exists for Anime. The artifact itself couriers canonical episode
     * metadata inside the canonize feeds (D-04a). [anime] is carried
     * for the 19-04 hydration seam (canonical_episodes → episode rows)
     * and deliberately skips the cover pass — Cinemeta already supplies
     * show covers, and `carrierCoverUrl` is manga/novel-shaped.
     */
    fun launchShows(workId: String, anime: Anime, force: Boolean = false) {
        if ((!force && !shouldRun(workId)) || !running.add(workId)) return
        scope.launch {
            try {
                canonizeWork.run(workId, Carrier.SHOWS)
                // 19-04: canonical hydration (canonical_episodes →
                // episode rows) lands here, then comment-key binding.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "[CanonicalPipeline] shows run failed for $workId (anime ${anime.id})" }
            } finally {
                lastRanAt[workId] = System.currentTimeMillis()
                running.remove(workId)
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 10L * 60 * 1000
    }
}
