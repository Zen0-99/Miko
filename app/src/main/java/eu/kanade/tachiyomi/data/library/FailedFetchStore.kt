package eu.kanade.tachiyomi.data.library

import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.library.interactor.ClearFailedFetches
import tachiyomi.domain.library.interactor.InsertFailedFetch
import tachiyomi.domain.library.model.FailedFetch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Process-wide store that the 3 library update jobs (Manga/Anime/Novel) call into
 * when a fetch run completes with failures. Persists each failure to the
 * FailedFetch table so the Fetching tab can render them until the user clears,
 * migrates, or swipes them away.
 *
 * The store is a thin wrapper over [InsertFailedFetch]; it exists so jobs don't
 * have to inject the interactor directly and can call a singleton from any
 * background context.
 */
object FailedFetchStore {

    suspend fun insert(failures: List<FailedEntry>) {
        if (failures.isEmpty()) return
        val now = System.currentTimeMillis()
        val inserts = failures.map { fe ->
            FailedFetch(
                id = 0L,
                entryId = fe.entry.id,
                entryKind = fe.entry.kind,
                title = fe.entry.title,
                coverUrl = null, // cover art is resolved lazily by the Fetching screen via entry id
                sourceId = fe.entry.sourceId,
                sourceName = fe.sourceName,
                reason = fe.reason,
                timestamp = now,
            )
        }
        withIOContext {
            Injekt.get<InsertFailedFetch>().awaitAll(inserts)
        }
    }

    /**
     * Clear all persisted failed fetches. Called when a new library update
     * run starts so the Fetching tab shows only the current run's failures
     * (not accumulated failures from previous runs).
     */
    suspend fun clearAll() {
        withIOContext {
            Injekt.get<ClearFailedFetches>().await()
        }
    }
}

