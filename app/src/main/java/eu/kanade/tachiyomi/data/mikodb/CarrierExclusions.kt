package eu.kanade.tachiyomi.data.mikodb

import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.injectLazy

/**
 * Carrier exclusions (Phase 20-06 / NCM-015) — the local half of the
 * picker's remove action. A (work, provider) row means "this extension
 * does not serve this entry for me" — the provider vanishes from the
 * preferred-source sheet, auto election and install offers, and no
 * worker-side mint state (re-mint, solidify, level-up) can bring it
 * back. Reversible only via the tagged row in Find Providers; exclusion
 * rows export/restore inside MikoBackup so the decision survives
 * reinstall. The companion −0.25 ledger signal lives server-side and is
 * posted/retracted by the caller — the row stores `provider_ref` so a
 * restore can retract the signal without re-deriving it.
 */
class CarrierExclusions {

    private val handler: MangaDatabaseHandler by injectLazy()

    suspend fun exclude(
        workId: String,
        provider: String,
        providerRef: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ) {
        handler.await(true) {
            carrier_exclusionsQueries.insert(workId, provider, providerRef, createdAt)
        }
    }

    suspend fun unexclude(workId: String, provider: String) {
        handler.await(true) {
            carrier_exclusionsQueries.delete(workId, provider)
        }
    }

    /** The stored binding ref — the restore path retracts the remove
     *  signal with it. Null when the row predates the column. */
    suspend fun providerRefFor(workId: String, provider: String): String? =
        handler.awaitList { carrier_exclusionsQueries.providerRefFor(workId, provider) }
            .firstOrNull()?.provider_ref

    suspend fun providersForWork(workId: String): Set<String> =
        handler.awaitList { carrier_exclusionsQueries.providersForWork(workId) }.toSet()

    /** Full table snapshot for the backup creator. */
    suspend fun all(): List<data.Carrier_exclusions> =
        handler.awaitList { carrier_exclusionsQueries.selectAll() }

    /** Restore merge — OR IGNORE so rows created AFTER the backup
     *  (post-restore removals) are never clobbered by an older file. */
    suspend fun insertAll(rows: List<data.Carrier_exclusions>) {
        handler.await(true) {
            rows.forEach { r ->
                carrier_exclusionsQueries.insertAll(r.work_id, r.provider, r.provider_ref, r.created_at)
            }
        }
    }
}
