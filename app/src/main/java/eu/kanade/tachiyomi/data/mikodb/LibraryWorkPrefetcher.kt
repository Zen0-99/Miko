package eu.kanade.tachiyomi.data.mikodb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The per-device "delta": resolves the user's actual library into
 * `works_cache` once, so work identity is instant+offline afterwards.
 * Sequential and self-paced — roughly one Worker round-trip per entry on
 * a cold cache, cache-hits afterwards.
 *
 * Phase 14: the pass doubles as the mass-minting driver. Every
 * resolved id is reported to `/v1/ids/seen` once per process (ID-only
 * telemetry — no user/device fields). The pass tail then hands the
 * favorites list to [LibraryCanonizer], which does the actual
 * canonize + favorite-transfer per entry (extension fan-out lives
 * there, not here — see [resolveQuietly]).
 */
class LibraryWorkPrefetcher {

    private val mangaHandler: MangaDatabaseHandler by injectLazy()
    private val novelHandler: NovelDatabaseHandler by injectLazy()
    private val animeHandler: AnimeDatabaseHandler by injectLazy()
    private val resolver: WorkIdResolver by injectLazy()
    private val api: WorkIdentityApi by injectLazy()
    private val canonizer: LibraryCanonizer by injectLazy()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    /** Upgrades a running resolve-only pass to include canonization. */
    private val canonizeRequested = AtomicBoolean(false)

    /** Re-run after the current pass — set when canonize arrives mid-run
     *  (entries added during the pass would otherwise be missed). */
    private val rerunRequested = AtomicBoolean(false)

    /** Process-lifetime dedup — each resolved id is reported once. */
    private val reportedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Idempotent background pass over both favorite lists. */
    fun start(canonize: Boolean = false) {
        if (canonize) canonizeRequested.set(true)
        if (job?.isActive == true) {
            if (canonize) rerunRequested.set(true)
            return
        }
        job = scope.launch {
            try {
                do {
                    rerunRequested.set(false)
                    prefetchManga()
                    prefetchNovels()
                    prefetchShows()
                } while (rerunRequested.get())
                // Pass tail: move favorites onto canonical rows. On a
                // canonize pass (library refresh) the full enrich +
                // canonize + sync chain runs per entry; on plain passes
                // only works with an existing canon sequence migrate —
                // no extension traffic for a speculative batch.
                try {
                    canonizer.migrateAllFavorites(networkHeavy = canonizeRequested.get())
                } catch (e: Exception) {
                    logcat { "canonize pass failed: ${e.message}" }
                }
            } finally {
                canonizeRequested.set(false)
            }
        }
    }

    private suspend fun prefetchManga() {
        val favorites = try {
            mangaHandler.awaitList { mangasQueries.getFavorites() }
        } catch (e: Exception) {
            logcat { "prefetch manga list failed: ${e.message}" }
            return
        }
        for (manga in favorites) {
            resolveQuietly(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_MANGA,
                    title = manga.title,
                    url = manga.url,
                    author = manga.author,
                ),
            )
        }
    }

    private suspend fun prefetchNovels() {
        val favorites = try {
            novelHandler.awaitList { novelsQueries.getFavorites() }
        } catch (e: Exception) {
            logcat { "prefetch novel list failed: ${e.message}" }
            return
        }
        for (novel in favorites) {
            resolveQuietly(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_BOOKS,
                    title = novel.title,
                    url = novel.url,
                    author = novel.author,
                    isbn = WorkIdResolver.isbnFromGenre(novel.genre),
                    isBook = novel.source == ANNAS_ARCHIVE_SOURCE_ID,
                ),
            )
        }
    }

    /**
     * Shows entries participate in resolve/markSeen like the other
     * carriers (Phase 15-02) — cinemeta: urls carry ('imdb', ttid) into
     * the resolver's external-ID mining. canonize stays gated below:
     * CanonizeWork is chapter-shaped and never runs on shows.
     */
    private suspend fun prefetchShows() {
        val favorites = try {
            animeHandler.awaitList { animesQueries.getFavorites() }
        } catch (e: Exception) {
            logcat { "prefetch shows list failed: ${e.message}" }
            return
        }
        for (anime in favorites) {
            resolveQuietly(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_SHOWS,
                    title = anime.title,
                    url = anime.url,
                    // cinemeta:{series|movie}:tt… type seeds the
                    // fetch-order hint (never the resolved kind).
                    kindHint = WorkIdResolver.cinemetaRef(anime.url)?.first,
                ),
            )
        }
    }

    /**
     * Resolve → markSeen, nothing more. Canonize deliberately does NOT
     * run here: [LibraryCanonizer.migrateManga] canonizes each favorite
     * itself (with the entry's own source injected as a feed candidate)
     * right before syncing its chapters, and [CanonizeWork]'s staleness
     * gate makes repeat calls cheap. Fanning out to extensions inside
     * the prefetch loop serialized ~500 extension crawls ahead of the
     * migration tail — the pass ran for hours and produced zero visible
     * migrations before dying with the process.
     */
    private suspend fun resolveQuietly(candidate: WorkIdResolver.Candidate) {
        try {
            val resolved = resolver.resolve(candidate) ?: return
            if (reportedIds.add(resolved.value)) {
                api.markSeen(resolved.value, resolved.medium)
            }
            delay(150) // keep bursts off the Worker/MangaBaka path
        } catch (e: Exception) {
            logcat { "prefetch resolve failed for '${candidate.title}': ${e.message}" }
        }
    }

    private companion object {
        const val ANNAS_ARCHIVE_SOURCE_ID = 6400L
    }
}
