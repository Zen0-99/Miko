package eu.kanade.tachiyomi.data.mikodb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import logcat.logcat
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Community-verified provider tiers (Phase 20-04 / NCM-014) — the
 * worker's `provider_stats` overlay on the manifest's static
 * `SourceMeta.tier`.
 *
 * Composition lives in ONE seam — [effectiveTier]:
 * `best(dynamic, manifest)` ceiling'd by `tier_cap`. A dynamic tier
 * only ever PROMOTES (the worker ladder tops at 'A' — 'S' stays
 * curator-only); `tier_cap` only ever DEMOTES; the manifest tier is
 * the floor when the overlay is cold, stale, or unreachable.
 *
 * Freshness: reads never block on the network. When the overlay is
 * older than [TTL_MS], a read kicks ONE in-flight background refresh
 * ([ensureFresh]); the last good payload persists under
 * `miko_provider_tiers` and serves stale forever, so a dead worker or
 * cold/offline device degrades to manifest tiers with zero behavior
 * change. Tier fetches are periodic only — never per render, mint, or
 * serve event.
 */
class ProviderTiers(
    private val api: WorkIdentityApi = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    /** pkg → worker-published overlay (dynamic_tier + tier_cap). */
    private val overlay = ConcurrentHashMap<String, Overlay>()

    /** Last fetch ATTEMPT — failures hold the line for [TTL_MS] too;
     *  a dead worker must not be retried once per effectiveTier call. */
    @Volatile
    private var lastFetchAt = 0L

    @Volatile
    private var loaded = false
    private val refreshQueued = AtomicBoolean(false)
    private val refreshMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cachePref by lazy { preferenceStore.getString(PREF_KEY, "") }

    internal data class Overlay(
        val dynamicTier: String?,
        val tierCap: String?,
        /** Global confirm/upvote count — null when the worker predates
         *  the field (unknown beats a wrong zero). */
        val confirmCount: Int?,
    )

    /** Persisted last-good payload — pkg-keyed, carries its own stamp. */
    @Serializable
    internal data class TiersCache(
        val fetched_at: Long,
        val providers: Map<String, WorkIdentityApi.TierDto>,
    )

    /** The provider's community-earned tier, or null when the overlay
     *  never promoted it — the Find Provider sheet badges on this. */
    fun dynamic(pkgName: String): String? {
        loadCache()
        return overlay[pkgName]?.dynamicTier
    }

    /** Report-derank ceiling for [pkgName], or null. */
    fun tierCap(pkgName: String): String? {
        loadCache()
        return overlay[pkgName]?.tierCap
    }

    /** The provider's global upvote total ("N pts" in the Find
     *  Provider sheet) — null when the overlay has no row for it or
     *  the worker predates the field. */
    fun confirmCount(pkgName: String): Int? {
        loadCache()
        requestRefresh()
        return overlay[pkgName]?.confirmCount
    }

    /**
     * The single effective-tier seam (SD-26): `best(dynamic, manifest)`
     * ceiling'd by `tier_cap`. Pure read — kicks a TTL-gated background
     * refresh when stale, but never suspends on it.
     */
    fun effectiveTier(pkgName: String): String {
        requestRefresh()
        val manifest = mikoDbManager.sourceMeta(pkgName)?.tier ?: ""
        val o = overlay[pkgName]
        return compose(manifest, o?.dynamicTier, o?.tierCap)
    }

    /** SD-26 in one expression — pure; the unit goldens drive this. */
    internal fun compose(manifest: String, dynamicTier: String?, tierCap: String?): String {
        var best = manifest
        if (dynamicTier != null && tierRank(dynamicTier) < tierRank(best)) best = dynamicTier
        if (tierCap != null && tierRank(best) < tierRank(tierCap)) best = tierCap
        return best
    }

    /** Kick a background refresh iff stale — at most one in flight; the
     *  TTL check keeps the per-call cost of a hot read at a compare. */
    private fun requestRefresh() {
        loadCache()
        if (System.currentTimeMillis() - lastFetchAt < TTL_MS) return
        if (!refreshQueued.compareAndSet(false, true)) return
        scope.launch {
            try {
                ensureFresh()
            } finally {
                refreshQueued.set(false)
            }
        }
    }

    /**
     * Refresh the overlay when stale. The attempt stamps [lastFetchAt]
     * BEFORE the request so a failure still bounds retries to the TTL;
     * the persisted `fetched_at` keeps the successful payload's stamp.
     */
    suspend fun ensureFresh() {
        loadCache()
        if (System.currentTimeMillis() - lastFetchAt < TTL_MS) return
        refreshMutex.withLock {
            if (System.currentTimeMillis() - lastFetchAt < TTL_MS) return
            lastFetchAt = System.currentTimeMillis()
            val resp = api.getProviderTiers() ?: return
            overlay.clear()
            for ((pkg, t) in resp.providers) {
                overlay[pkg] = toOverlay(t)
            }
            runCatching {
                cachePref.set(json.encodeToString(TiersCache(lastFetchAt, resp.providers)))
            }.onFailure { logcat { "providerTiers: cache write failed (${it.message})" } }
        }
    }

    /** Restore the persisted last-good payload once per process. */
    private fun loadCache() {
        if (loaded) return
        loaded = true
        val raw = cachePref.get()
        if (raw.isEmpty()) return
        runCatching {
            val cache = json.decodeFromString<TiersCache>(raw)
            lastFetchAt = cache.fetched_at
            for ((pkg, t) in cache.providers) {
                overlay[pkg] = toOverlay(t)
            }
        }.onFailure { logcat { "providerTiers: bad cache pref (${it.message})" } }
    }

    /** 'S' is curator-only — the worker's ladder tops at 'A' and the
     *  client holds that line even if a response regresses. */
    private fun toOverlay(t: WorkIdentityApi.TierDto) = Overlay(
        t.dynamic_tier?.takeUnless { it.equals("S", ignoreCase = true) },
        t.tier_cap,
        t.confirm_count,
    )

    private companion object {
        const val PREF_KEY = "miko_provider_tiers"

        /** Hard floor between worker fetches — the overlay is a
         *  periodic read, never a per-event request. */
        const val TTL_MS = 60L * 60 * 1000
    }
}
