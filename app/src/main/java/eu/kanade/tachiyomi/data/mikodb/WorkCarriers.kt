package eu.kanade.tachiyomi.data.mikodb

import android.graphics.drawable.Drawable
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import kotlinx.coroutines.flow.first
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Carrier matrix (Phase 16-04) — a work's durable server-side bindings
 * (`GET /v1/works/:id/bindings`, 16-02) joined against what this device
 * has installed and enabled. It answers three questions:
 *
 *  - Can this chapter be read right now? (readable carriers)
 *  - Which extension would unlock it? (bound-but-uninstalled carriers,
 *    joined with repo metadata for the install sheet)
 *  - Which installed sources may serve chapters despite lacking canon
 *    refs? (router expansion input — `providerRef` reopens the entry)
 *
 * Only extension-package bindings are carriers — `mangadex`/`mangabaka`
 * bindings are provenance records, not something the app can drive.
 */
class WorkCarriers(
    private val api: WorkIdentityApi = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
) {

    // Lazily resolved: extension-manager init is expensive (async APK scan), so keep it
    // off whichever thread first builds this singleton
    // (see startup ANR deadlock — registerStubSource -> downloadManager -> WorkCarriers).
    private val mangaExtensions: MangaExtensionManager by injectLazy()
    private val novelExtensions: NovelExtensionManager by injectLazy()
    private val animeExtensions: AnimeExtensionManager by injectLazy()

    data class Carrier(
        val pkgName: String,
        val displayName: String,
        val tier: String,
        /** Source-internal entry url — reopening the work on the carrier
         *  (`SManga.url`/`SNovel.url`). */
        val providerRef: String,
        val provenance: String?,
        val lastSeenAt: Long?,
        val installed: Boolean,
        /** Installed AND not user-hidden (disabled catalogues). */
        val enabled: Boolean,
        val sourceIds: List<Long>,
        /** Repo icon + language for the install sheet. */
        val iconUrl: String?,
        val lang: String?,
        /** Repo offers this pkg — the install sheet can act on it. */
        val installable: Boolean,
        /** Installed extension's icon (Drawable) — the picker rows
         *  render real logos, not repo URLs. */
        val icon: Drawable? = null,
    )

    data class Matrix(
        val workId: String,
        val carriers: List<Carrier>,
        /** False when the bindings fetch failed — an empty [carriers]
         *  then means "unknown", not "no carriers exist". */
        val fetched: Boolean,
    ) {
        /** Installed + enabled carriers the router may drive. */
        val readable: List<Carrier> get() = carriers.filter { it.enabled }

        /** Bound-but-uninstalled carriers the repo can install. */
        val installable: List<Carrier> get() =
            carriers.filter { !it.installed && it.installable }
    }

    /** Per-chapter UI state on a canonical entry. */
    enum class ChapterCarrierState {
        /** An enabled carrier can serve (or attempt) this chapter. */
        READY,

        /** No enabled carrier — tapping should offer the install sheet. */
        INSTALL_REQUIRED,

        /** No carrier is known for this work at all. */
        UNAVAILABLE,
    }

    private class Cached(val fetchedAt: Long, val matrix: Matrix)
    private val cache = ConcurrentHashMap<String, Cached>()

    /** Fresh matrix, or a cached one younger than [TTL_MS]. */
    suspend fun forWork(workId: String, medium: String = "manga"): Matrix {
        cache[workId]?.let { c ->
            if (System.currentTimeMillis() - c.fetchedAt < TTL_MS) return c.matrix
        }
        val matrix = build(workId, medium)
        if (matrix.fetched) cache[workId] = Cached(System.currentTimeMillis(), matrix)
        return matrix
    }

    fun invalidate(workId: String) {
        cache.remove(workId)
    }

    /**
     * Per-row state for a canonical chapter: READY when an enabled
     * carrier plausibly serves it (ref-listed, or bound so the router's
     * align expansion can try), INSTALL_REQUIRED when only uninstalled
     * carriers exist, UNAVAILABLE when nothing is bound at all.
     */
    suspend fun chapterState(
        workId: String,
        seq: Int,
        medium: String = "manga",
    ): ChapterCarrierState {
        val matrix = forWork(workId, medium)
        if (matrix.readable.isNotEmpty()) {
            // A ref to an enabled carrier is a direct serve; a bound
            // enabled carrier without refs is the router's
            // align-expansion path. Either way: readable.
            return ChapterCarrierState.READY
        }
        return if (matrix.installable.isNotEmpty()) {
            ChapterCarrierState.INSTALL_REQUIRED
        } else {
            ChapterCarrierState.UNAVAILABLE
        }
    }

    private suspend fun build(workId: String, medium: String): Matrix {
        val resp = api.getBindings(workId)
            ?: return Matrix(workId, emptyList(), fetched = false)
        val meta = mikoDbManager.allSourceMeta()
        val isBooks = medium == WorkIdResolver.MEDIUM_BOOKS
        val isShows = medium == WorkIdResolver.MEDIUM_SHOWS

        // installedExtensionsFlow starts empty and fills async — reading
        // .value before init yields a 0-carrier matrix that then caches
        // for TTL_MS. Wait for the medium's manager (bounded) first; a
        // timeout reports unfetched so forWork retries instead of
        // caching an empty read.
        val extensionsReady = kotlinx.coroutines.withTimeoutOrNull(10_000) {
            when {
                isShows -> animeExtensions.isInitialized.first { it }
                isBooks -> novelExtensions.isInitialized.first { it }
                else -> mangaExtensions.isInitialized.first { it }
            }
        } != null

        val installed: Map<String, InstalledInfo>
        val available: Map<String, AvailableInfo>
        val disabled: Set<String>
        if (isShows) {
            installed = animeExtensions.installedExtensionsFlow.value
                .filterIsInstance<AnimeExtension.Installed>()
                .associate { e ->
                    e.pkgName to InstalledInfo(e.pkgName, e.name, e.sources.map { it.id }, e.icon)
                }
            available = animeExtensions.availableExtensionsFlow.value
                .associate { e -> e.pkgName to AvailableInfo(e.pkgName, e.name, e.iconUrl, e.lang) }
            disabled = sourcePreferences.disabledAnimeSources().get()
        } else if (isBooks) {
            installed = novelExtensions.installedExtensionsFlow.value
                .filterIsInstance<NovelExtension.Installed>()
                .associate { e ->
                    e.pkgName to InstalledInfo(e.pkgName, e.name, e.sources.map { it.id }, e.icon)
                }
            available = novelExtensions.availableExtensionsFlow.value
                .associate { e -> e.pkgName to AvailableInfo(e.pkgName, e.name, e.iconUrl, e.lang) }
            disabled = sourcePreferences.disabledNovelSources().get()
        } else {
            installed = mangaExtensions.installedExtensionsFlow.value
                .filterIsInstance<MangaExtension.Installed>()
                .associate { e ->
                    e.pkgName to InstalledInfo(e.pkgName, e.name, e.sources.map { it.id }, e.icon)
                }
            available = mangaExtensions.availableExtensionsFlow.value
                .associate { e -> e.pkgName to AvailableInfo(e.pkgName, e.name, e.iconUrl, e.lang) }
            disabled = sourcePreferences.disabledMangaSources().get()
        }

        val carriers = resp.bindings.mapNotNull { b ->
            val inst = installed[b.source_key]
            val avail = available[b.source_key]
            // Not an app-carrier binding (mangadex/mangabaka/etc.) or an
            // extension this device could never obtain — not actionable.
            if (inst == null && avail == null) return@mapNotNull null
            val m = meta[b.source_key]
            Carrier(
                pkgName = b.source_key,
                displayName = m?.name?.takeIf { it.isNotBlank() }
                    ?: inst?.name
                    ?: avail?.name
                    ?: b.source_key.substringAfterLast('.'),
                tier = m?.tier ?: "",
                providerRef = b.provider_ref,
                provenance = b.provenance,
                lastSeenAt = b.last_seen_at,
                installed = inst != null,
                enabled = inst != null &&
                    inst.sourceIds.any { it.toString() !in disabled },
                sourceIds = inst?.sourceIds.orEmpty(),
                iconUrl = avail?.iconUrl,
                lang = avail?.lang,
                installable = avail != null,
                icon = inst?.icon,
            )
        }
        return Matrix(workId, carriers, fetched = extensionsReady)
    }

    private data class InstalledInfo(
        val pkgName: String,
        val name: String,
        val sourceIds: List<Long>,
        val icon: Drawable? = null,
    )

    private data class AvailableInfo(
        val pkgName: String,
        val name: String,
        val iconUrl: String?,
        val lang: String?,
    )

    private companion object {
        /** Bindings change slowly (crawler/canonize); the install sheet
         *  also invalidates on successful install. */
        const val TTL_MS = 6L * 60 * 60 * 1000
    }
}
