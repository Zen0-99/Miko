package eu.kanade.tachiyomi.data.mikodb

import android.app.Application
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.manga.model.hasCustomCover
import eu.kanade.domain.entries.novel.model.hasCustomCover as hasCustomNovelCover
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.cover.HiResCoverResolver
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.library.EntryRef
import eu.kanade.tachiyomi.data.library.FailedEntry
import eu.kanade.tachiyomi.data.library.LibraryUpdateProgressBus
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.novel.NovelExtensionManager
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.SetMangaCollections
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCollections
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapterUpdate
import tachiyomi.domain.items.chapter.model.toChapterUpdate
import tachiyomi.domain.items.chapter.model.toNovelChapterUpdate
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.model.EntryKind
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import eu.kanade.tachiyomi.util.system.isOnline
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import kotlin.math.floor
import java.util.concurrent.ConcurrentHashMap

/**
 * Decouples library favorites from their extension rows (the "old
 * Extension depending generation" → canonical `miko:` entries).
 *
 * For each extension-source favorite:
 *   resolve → materialize the canonical row → enrich + canonize with
 *   the entry's own source injected as a feed candidate → sync canon
 *   chapters → transfer favorite/progress/collections/tracks/cover →
 *   unfavorite the extension row.
 *
 * Afterward the library card IS the canonical entry: chapters come from
 * the carrier matrix (automatic / per-carrier picks), so a dead source
 * can never strand the entry, and discovery bound-taps land on it via
 * `localLibraryEntryIds` path A with no extra wiring. The extension row
 * stays in the DB unfavorited — history rows still resolve and the
 * unifier redirects them to the canonical entry.
 *
 * Safety rules (same as [CanonicalEntryUnifier]): only favorites that
 * resolve to a catalog work migrate — ambiguous ids are guesses that
 * must not collapse distinct works onto one row, so those entries keep
 * their extension binding. Catalog-uncovered works mint a device-local
 * `mk:m:` id (online passes only) — a provisional identity the entry's
 * own cached chapters materialize under, retargeted onto a real work
 * id by [promoteLocalMint] once the catalog learns it. A canonical row
 * that can't produce chapters aborts the transfer rather than
 * stranding the user on an empty entry.
 */
class LibraryCanonizer(
    private val resolver: WorkIdResolver = Injekt.get(),
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    private val catalog: CanonicalCatalog = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val novelRepository: NovelRepository = Injekt.get(),
    private val novelHandler: NovelDatabaseHandler = Injekt.get(),
    private val mangaHandler: MangaDatabaseHandler = Injekt.get(),
    private val mangaBakaEnrich: MangaBakaEnrich = Injekt.get(),
    private val canonizeWork: CanonizeWork = Injekt.get(),
    private val chapterSync: CanonicalChapterSync = Injekt.get(),
    private val unifier: CanonicalEntryUnifier = Injekt.get(),
    private val mangaExtensions: MangaExtensionManager = Injekt.get(),
    private val novelExtensions: NovelExtensionManager = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateChapter: UpdateChapter = Injekt.get(),
    private val getMangaCollections: GetMangaCollections = Injekt.get(),
    private val setMangaCollections: SetMangaCollections = Injekt.get(),
    private val getMangaTracks: GetMangaTracks = Injekt.get(),
    private val insertMangaTrack: InsertMangaTrack = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val mangaCoverCache: MangaCoverCache = Injekt.get(),
    private val getChaptersByNovelId: GetNovelChaptersByNovelId = Injekt.get(),
    private val updateNovelChapter: UpdateNovelChapter = Injekt.get(),
    private val getNovelCollections: GetNovelCollections = Injekt.get(),
    private val setNovelCollections: SetNovelCollections = Injekt.get(),
    private val getNovelTracks: GetNovelTracks = Injekt.get(),
    private val insertNovelTrack: InsertNovelTrack = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val novelCoverCache: NovelCoverCache = Injekt.get(),
    private val api: WorkIdentityApi = Injekt.get(),
    private val matcher: SeriesMatcher = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val chapterMap: CanonicalChapterMap = Injekt.get(),
    private val app: Application = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val mintTelemetry: MintTelemetry = Injekt.get(),
    private val workCarriers: WorkCarriers = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap.newKeySet<Long>()

    /**
     * Why a migrate bailed — the library-update batch surfaces it in the
     * failure list instead of a bare "could not mint". [Deferred] is not
     * a failure: the canon row exists but the extension favorite stayed
     * because read progress couldn't map onto canon numbering.
     */
    private sealed interface MintOutcome<out T> {
        class Migrated<T>(val entry: T) : MintOutcome<T>
        class Deferred<T>(val reason: String) : MintOutcome<T>
        class Failed<T>(val reason: String) : MintOutcome<T>
    }

    private sealed interface ResolveOutcome {
        class Hit(val work: WorkIdResolver.CanonicalWorkId) : ResolveOutcome
        class Miss(val reason: String) : ResolveOutcome
    }

    /**
     * Full single-entry migration — resolves, materializes, canonizes
     * and transfers. Returns the canonical row on success so callers
     * (entry screens) can redirect to it.
     *
     * [heavy] controls the expensive part: enrich + canonize fans out to
     * installed extensions. Pass false for cheap passes (app start) —
     * the migration then only lands when a canon sequence already
     * exists server-side (worker GET only, no extension traffic).
     */
    suspend fun migrateManga(manga: Manga, heavy: Boolean = true): Manga? =
        (migrateMangaOutcome(manga, heavy) as? MintOutcome.Migrated)?.entry

    private suspend fun migrateMangaOutcome(manga: Manga, heavy: Boolean = true): MintOutcome<Manga> {
        if (!manga.favorite ||
            manga.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
            CanonicalCatalog.isCanonicalUrl(manga.url)
        ) {
            return MintOutcome.Failed("not a migration candidate")
        }
        if (!inFlight.add(manga.id)) {
            logcat { "canonize skip '${manga.title}': already in-flight" }
            return MintOutcome.Failed("already minting")
        }
        try {
            val resolved = when (val r = resolveWork(manga, heavy)) {
                is ResolveOutcome.Miss -> return MintOutcome.Failed(r.reason)
                is ResolveOutcome.Hit -> r.work
            }
            val workId = resolved.value
            val localMint = resolved.confidence == WorkIdResolver.Confidence.LOCAL_MINT
            logcat { "canonize '${manga.title}': resolved $workId ($localMint, ${resolved.confidence})" }
            val work = bridge.byWorkId(workId) ?: run {
                if (!localMint) {
                    logcat { "canonize skip '${manga.title}': work $workId not in bridge" }
                    return MintOutcome.Failed("work missing from catalog")
                }
                // Device-minted provisional work — identity is the
                // deterministic hash of this entry's own metadata, so
                // title/author/cover inherit straight across. Upserted
                // by materialize → resolvable for future promote runs.
                MangaNovelIdBridge.WorkRow(
                    mikoWorkId = workId,
                    type = resolved.kind
                        ?: WorkIdResolver.mediumToType(WorkIdResolver.MEDIUM_MANGA, false, null),
                    title = manga.title,
                    author = manga.author,
                    genres = manga.genre,
                    coverUrl = manga.thumbnailUrl,
                    description = manga.description,
                    resolutionSource = "local_mint",
                )
            }
            val canonical = mangaRepository.getMangaById(catalog.materializeMangaId(work))
                ?: run {
                    logcat { "canonize skip '${manga.title}': materialize failed for $workId" }
                    return MintOutcome.Failed("catalog materialize failed")
                }
            if (canonical.id == manga.id) {
                logcat { "canonize skip '${manga.title}': already canonical (id=${canonical.id})" }
                return MintOutcome.Failed("already canonical")
            }
            logcat { "canonize '${manga.title}': materialized ${canonical.id} for $workId" }

            // Local mints are device-owned — worker enrich/canonize
            // can't know the id yet, so the local feed + local canon
            // materialization below are the whole path.
            if (heavy && !localMint) {
                runCatching { mangaBakaEnrich.run(workId, canonical) }
                canonizeWork.run(
                    workId,
                    Carrier.MANGA,
                    extraSources = listOfNotNull(
                        goldenFor(manga.source, manga.url, WorkIdResolver.MEDIUM_MANGA),
                    ),
                )
            }
            chapterSync.sync(canonical, workId)
            // Row presence is the gate, not rows-added — a previously
            // materialized canon list (prefetch, an earlier screen
            // visit) returns 0 additions and would otherwise strand
            // the migration forever.
            var canonChapters = getChaptersByMangaId.await(canonical.id)
            if (canonChapters.isEmpty() && heavy &&
                postLocalChaptersFeed(
                    title = manga.title, author = manga.author, url = manga.url,
                    sourceId = manga.source, work = work, workId = workId, isBooks = false,
                    chapters = getChaptersByMangaId.await(manga.id)
                        .map { Triple(it.name, it.url, it.dateUpload) },
                )
            ) {
                chapterSync.sync(canonical, workId)
                canonChapters = getChaptersByMangaId.await(canonical.id)
            }
            if (canonChapters.isEmpty()) {
                // Device-side canon: the favorite's own cached chapters
                // become the local sequence — minting must not gate on
                // the worker accepting a feed (dead source's pkg may be
                // absent from every repo catalog). A real carrier later
                // realigns these rows onto server seqs.
                chapterSync.syncLocal(canonical, workId, getChaptersByMangaId.await(manga.id))
                canonChapters = getChaptersByMangaId.await(canonical.id)
                if (canonChapters.isNotEmpty()) {
                    logcat { "localCanon mint '${manga.title}' → $workId (${canonChapters.size} rows)" }
                }
            }
            if (canonChapters.isEmpty()) {
                logcat { "canonize skip '${manga.title}': no chapters to adopt" }
                return MintOutcome.Failed("no chapters to adopt")
            }
            logcat { "canonize '${manga.title}': ${canonChapters.size} canon rows, transferring" }

            if (!transferFavorite(manga, canonical, canonChapters)) {
                // Progress was on the extension row but nothing matched
                // the canon numbering — keeping the extension favorite
                // is the only way the user loses nothing.
                logcat { "canonize defer '${manga.title}': progress found no canon counterpart" }
                return MintOutcome.Deferred("read progress has no canon match")
            }
            recordDonorCarrier(manga.source, workId, isBooks = false)
            unifier.invalidate()
            logcat { "canonized '${manga.title}' → ${work.mikoWorkId} (entry ${canonical.id})" }
            return MintOutcome.Migrated(canonical)
        } catch (e: Exception) {
            logcat { "canonize '${manga.title}' failed: ${e.message}" }
            return MintOutcome.Failed("error: ${e.message?.take(80)}")
        } finally {
            inFlight.remove(manga.id)
        }
    }

    /** Same as [migrateManga] for novels and books (`mk:ol:` works). */
    suspend fun migrateNovel(novel: Novel, heavy: Boolean = true): Novel? =
        (migrateNovelOutcome(novel, heavy) as? MintOutcome.Migrated)?.entry

    private suspend fun migrateNovelOutcome(novel: Novel, heavy: Boolean = true): MintOutcome<Novel> {
        if (!novel.favorite ||
            novel.source == CanonicalCatalog.CANONICAL_SOURCE_ID ||
            CanonicalCatalog.isCanonicalUrl(novel.url)
        ) {
            return MintOutcome.Failed("not a migration candidate")
        }
        if (!inFlight.add(novel.id)) {
            logcat { "canonize skip '${novel.title}': already in-flight" }
            return MintOutcome.Failed("already minting")
        }
        try {
            val resolved = when (val r = resolveWork(novel, heavy)) {
                is ResolveOutcome.Miss -> return MintOutcome.Failed(r.reason)
                is ResolveOutcome.Hit -> r.work
            }
            val workId = resolved.value
            val localMint = resolved.confidence == WorkIdResolver.Confidence.LOCAL_MINT
            val work = bridge.byWorkId(workId) ?: run {
                if (!localMint) {
                    logcat { "canonize skip '${novel.title}': work $workId not in bridge" }
                    return MintOutcome.Failed("work missing from catalog")
                }
                MangaNovelIdBridge.WorkRow(
                    mikoWorkId = workId,
                    type = resolved.kind
                        ?: WorkIdResolver.mediumToType(WorkIdResolver.MEDIUM_BOOKS, false, null),
                    title = novel.title,
                    author = novel.author,
                    genres = novel.genre,
                    coverUrl = novel.thumbnailUrl,
                    description = novel.description,
                    resolutionSource = "local_mint",
                )
            }
            val canonical = novelRepository.getNovelById(catalog.materializeNovelId(work))
                ?: run {
                    logcat { "canonize skip '${novel.title}': materialize failed for $workId" }
                    return MintOutcome.Failed("catalog materialize failed")
                }
            if (canonical.id == novel.id) return MintOutcome.Failed("already canonical")

            if (heavy && !localMint) {
                runCatching { mangaBakaEnrich.run(workId, canonical) }
                canonizeWork.run(
                    workId,
                    Carrier.BOOKS,
                    extraSources = listOfNotNull(
                        goldenFor(novel.source, novel.url, WorkIdResolver.MEDIUM_BOOKS),
                    ),
                )
            }
            chapterSync.syncNovel(canonical, workId)
            var canonChapters = getChaptersByNovelId.await(canonical.id)
            if (canonChapters.isEmpty() && heavy &&
                postLocalChaptersFeed(
                    title = novel.title, author = novel.author, url = novel.url,
                    sourceId = novel.source, work = work, workId = workId, isBooks = true,
                    chapters = getChaptersByNovelId.await(novel.id)
                        .map { Triple(it.name, it.url, it.dateUpload) },
                )
            ) {
                chapterSync.syncNovel(canonical, workId)
                canonChapters = getChaptersByNovelId.await(canonical.id)
            }
            if (canonChapters.isEmpty()) {
                chapterSync.syncLocalNovel(canonical, workId, getChaptersByNovelId.await(novel.id))
                canonChapters = getChaptersByNovelId.await(canonical.id)
                if (canonChapters.isNotEmpty()) {
                    logcat { "localCanon mint '${novel.title}' → $workId (${canonChapters.size} rows)" }
                }
            }
            if (canonChapters.isEmpty()) {
                logcat { "canonize skip '${novel.title}': no chapters to adopt" }
                return MintOutcome.Failed("no chapters to adopt")
            }

            if (!transferFavorite(novel, canonical, canonChapters)) {
                logcat { "canonize defer '${novel.title}': progress found no canon counterpart" }
                return MintOutcome.Deferred("read progress has no canon match")
            }
            recordDonorCarrier(novel.source, workId, isBooks = true)
            unifier.invalidate()
            logcat { "canonized '${novel.title}' → ${work.mikoWorkId} (entry ${canonical.id})" }
            return MintOutcome.Migrated(canonical)
        } catch (e: Exception) {
            logcat { "canonize '${novel.title}' failed: ${e.message}" }
            return MintOutcome.Failed("error: ${e.message?.take(80)}")
        } finally {
            inFlight.remove(novel.id)
        }
    }

    /**
     * Entry-screen path: opening an extension-source favorite kicks the
     * migration off the render path; [onDone] fires with the canonical
     * row so the screen can redirect to it.
     */
    fun migrateMangaInBackground(manga: Manga, onDone: (Manga) -> Unit = {}) {
        if (manga.id in inFlight) return
        scope.launch { migrateManga(manga)?.let(onDone) }
    }

    fun migrateNovelInBackground(novel: Novel, onDone: (Novel) -> Unit = {}) {
        if (novel.id in inFlight) return
        scope.launch { migrateNovel(novel)?.let(onDone) }
    }

    /**
     * `mk:m:` → catalog-id upgrade. A local mint is provisional — when
     * the worker catalog later learns the work (new mirror data, or a
     * carrier feed minted it), the canonical row retargets onto the
     * real work id: the row url flips and every `canon:{old}:{seq}`
     * chapter url re-points in place, preserving ids/state; the normal
     * canon sync then realigns the rows onto server seqs by name.
     *
     * Returns the refreshed row + new work id, or null while the work
     * is still unresolved (ambiguous hits also stay local — guessing
     * risks collapsing two works).
     */
    suspend fun promoteLocalMint(manga: Manga): Pair<Manga, String>? {
        val oldId = CanonicalCatalog.workIdFromMangaUrl(manga.url) ?: return null
        if (!oldId.startsWith(WorkIdMint.PREFIX)) return null
        val resolved = try {
            resolver.resolve(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_MANGA,
                    title = manga.title,
                    author = manga.author,
                ),
                // The mint's own works_cache row must not short-circuit
                // the Worker lookup that could upgrade it.
                excludeWorkId = oldId,
            )
        } catch (_: Exception) {
            null
        } ?: return null
        if (resolved.ambiguous || resolved.confidence == WorkIdResolver.Confidence.LOCAL_MINT ||
            resolved.value == oldId
        ) {
            return null
        }
        val newId = resolved.value
        // A catalog row must exist — materialize needs real work
        // metadata, and a resolve hit without a bridge row means the
        // id can't be materialized into a usable entry anyway.
        bridge.byWorkId(newId) ?: return null
        updateManga.await(
            MangaUpdate(
                id = manga.id,
                url = CanonicalCatalog.MANGA_URL_PREFIX + newId,
            ),
        )
        val chapterUpdates = getChaptersByMangaId.await(manga.id).mapNotNull { ch ->
            val prefix = "canon:$oldId:"
            if (!ch.url.startsWith(prefix)) return@mapNotNull null
            val seq = ch.url.removePrefix(prefix).toIntOrNull() ?: return@mapNotNull null
            ChapterUpdate(id = ch.id, url = CanonicalChapterSync.canonUrl(newId, seq))
        }
        if (chapterUpdates.isNotEmpty()) {
            runCatching { updateChapter.awaitAll(chapterUpdates) }
                .onFailure { logcat { "localMint promote '${manga.title}': chapter rebind failed (${it.message})" } }
        }
        // The minted-donor carrier preference is keyed by work id —
        // carry it onto the real id or promotion strands it.
        sourcePreferences.preferredCarrierFor(oldId)?.let { pkg ->
            sourcePreferences.setPreferredCarrier(oldId, null)
            if (sourcePreferences.preferredCarrierFor(newId) == null) {
                sourcePreferences.setPreferredCarrier(newId, pkg)
            }
        }
        chapterMap.invalidate(newId)
        unifier.invalidate()
        // The superseded mint row keeps surfacing in discovery search
        // with its (often dead) donor cover — the real row covers the
        // work now, so the zombie goes.
        runCatching { bridge.deleteWork(oldId) }
        logcat { "localMint promote '${manga.title}': $oldId → $newId (${chapterUpdates.size} rows rebound)" }
        val refreshed = mangaRepository.getMangaById(manga.id) ?: return null
        return refreshed to newId
    }

    /** Same as [promoteLocalMint] for novels/books. */
    suspend fun promoteLocalMint(novel: Novel): Pair<Novel, String>? {
        val oldId = CanonicalCatalog.workIdFromNovelUrl(novel.url) ?: return null
        if (!oldId.startsWith(WorkIdMint.PREFIX)) return null
        val resolved = try {
            resolver.resolve(
                WorkIdResolver.Candidate(
                    medium = WorkIdResolver.MEDIUM_BOOKS,
                    title = novel.title,
                    author = novel.author,
                    isbn = WorkIdResolver.isbnFromGenre(novel.genre),
                ),
                excludeWorkId = oldId,
            )
        } catch (_: Exception) {
            null
        } ?: return null
        if (resolved.ambiguous || resolved.confidence == WorkIdResolver.Confidence.LOCAL_MINT ||
            resolved.value == oldId
        ) {
            return null
        }
        val newId = resolved.value
        bridge.byWorkId(newId) ?: return null
        updateNovel.await(
            NovelUpdate(
                id = novel.id,
                url = CanonicalCatalog.NOVEL_URL_PREFIX + newId,
            ),
        )
        val chapterUpdates = getChaptersByNovelId.await(novel.id).mapNotNull { ch ->
            val prefix = "canon:$oldId:"
            if (!ch.url.startsWith(prefix)) return@mapNotNull null
            val seq = ch.url.removePrefix(prefix).toIntOrNull() ?: return@mapNotNull null
            NovelChapterUpdate(id = ch.id, url = CanonicalChapterSync.canonUrl(newId, seq))
        }
        if (chapterUpdates.isNotEmpty()) {
            runCatching { updateNovelChapter.awaitAll(chapterUpdates) }
                .onFailure { logcat { "localMint promote '${novel.title}': chapter rebind failed (${it.message})" } }
        }
        sourcePreferences.preferredCarrierFor(oldId)?.let { pkg ->
            sourcePreferences.setPreferredCarrier(oldId, null)
            if (sourcePreferences.preferredCarrierFor(newId) == null) {
                sourcePreferences.setPreferredCarrier(newId, pkg)
            }
        }
        chapterMap.invalidate(newId)
        unifier.invalidate()
        runCatching { bridge.deleteWork(oldId) }
        logcat { "localMint promote '${novel.title}': $oldId → $newId (${chapterUpdates.size} rows rebound)" }
        val refreshed = novelRepository.getNovelById(novel.id) ?: return null
        return refreshed to newId
    }

    /**
     * Batch pass over both favorite lists — runs at the tail of
     * [LibraryWorkPrefetcher]'s pass. [networkHeavy]=true (library
     * refresh) canonizes through installed extensions; false (app
     * start) only migrates works whose canon sequence already exists.
     */
    suspend fun migrateAllFavorites(networkHeavy: Boolean) {
        val manga = try {
            mangaRepository.getMangaFavorites()
        } catch (e: Exception) {
            logcat { "canonize pass: manga favorites list failed: ${e.message}" }
            emptyList()
        }
        val novels = try {
            novelRepository.getNovelFavorites()
        } catch (e: Exception) {
            logcat { "canonize pass: novel favorites list failed: ${e.message}" }
            emptyList()
        }
        val extManga = manga.filter {
            it.source != CanonicalCatalog.CANONICAL_SOURCE_ID && !CanonicalCatalog.isCanonicalUrl(it.url)
        }
        val extNovels = novels.filter {
            it.source != CanonicalCatalog.CANONICAL_SOURCE_ID && !CanonicalCatalog.isCanonicalUrl(it.url)
        }
        val total = extManga.size + extNovels.size
        // Same bus the library-update overlay reads — minting becomes a
        // visible "MikoDB" section instead of a silent tail. Only the
        // heavy pass reports: the app-start pass (heavy=false) defers
        // dead-source entries by design — those aren't failures.
        val reportProgress = networkHeavy && total > 0
        if (reportProgress) {
            LibraryUpdateProgressBus.startRun(total, PROGRESS_SOURCE)
        }
        var processed = 0
        val failed = mutableListOf<FailedEntry>()
        val extMangaIds = extManga.mapTo(HashSet()) { it.id }
        val extNovelIds = extNovels.mapTo(HashSet()) { it.id }
        try {
            for (m in manga) {
                val outcome = migrateMangaOutcome(m, heavy = networkHeavy)
                if (m.id in extMangaIds) {
                    processed++
                    if (reportProgress) {
                        when (outcome) {
                            is MintOutcome.Migrated -> Unit
                            is MintOutcome.Deferred -> failed += FailedEntry(
                                EntryRef(m.id, m.title, m.source, EntryKind.MANGA),
                                "Minted — favorite kept (${outcome.reason})",
                                PROGRESS_SOURCE,
                            )
                            is MintOutcome.Failed -> failed += FailedEntry(
                                EntryRef(m.id, m.title, m.source, EntryKind.MANGA),
                                "Could not mint — ${outcome.reason}",
                                PROGRESS_SOURCE,
                            )
                        }
                        LibraryUpdateProgressBus.updateProgress(
                            processed,
                            listOf(EntryRef(m.id, m.title, m.source, EntryKind.MANGA)),
                            failed,
                            total,
                            PROGRESS_SOURCE,
                        )
                    }
                }
                delay(150)
            }
            for (n in novels) {
                val outcome = migrateNovelOutcome(n, heavy = networkHeavy)
                if (n.id in extNovelIds) {
                    processed++
                    if (reportProgress) {
                        when (outcome) {
                            is MintOutcome.Migrated -> Unit
                            is MintOutcome.Deferred -> failed += FailedEntry(
                                EntryRef(n.id, n.title, n.source, EntryKind.NOVEL),
                                "Minted — favorite kept (${outcome.reason})",
                                PROGRESS_SOURCE,
                            )
                            is MintOutcome.Failed -> failed += FailedEntry(
                                EntryRef(n.id, n.title, n.source, EntryKind.NOVEL),
                                "Could not mint — ${outcome.reason}",
                                PROGRESS_SOURCE,
                            )
                        }
                        LibraryUpdateProgressBus.updateProgress(
                            processed,
                            listOf(EntryRef(n.id, n.title, n.source, EntryKind.NOVEL)),
                            failed,
                            total,
                            PROGRESS_SOURCE,
                        )
                    }
                }
                delay(150)
            }
        } finally {
            if (reportProgress) {
                LibraryUpdateProgressBus.completeRun(failed, PROGRESS_SOURCE)
            }
            dedupeCanonicalNovels()
            repairCollidedCovers()
            repairPlaceholderTitles()
            restoreDonorCovers()
            reconcileLocalCanonNumbering()
        }
    }

    /**
     * Repair pass for canonical entries minted while the catalog carried
     * the "unknown title (please report on Discord)" placeholder: the
     * donor extension row that minted the work is still in the library
     * (unfavorited), reachable through a bound carrier's provider_ref —
     * retitle the canonical row from it. Only rows with a placeholder
     * title are touched; real titles are never rewritten.
     */
    private suspend fun repairPlaceholderTitles() {
        val mangaFavs = try {
            mangaRepository.getMangaFavorites()
        } catch (e: Exception) {
            logcat { "title repair: manga list failed: ${e.message}" }
            return
        }
        for (m in mangaFavs.filter {
            it.source == CanonicalCatalog.CANONICAL_SOURCE_ID && isPlaceholderTitle(it.title)
        }) {
            val workId = CanonicalCatalog.workIdFromMangaUrl(m.url) ?: continue
            val donor = carrierBoundEntry(
                workId, WorkIdResolver.MEDIUM_MANGA,
            ) { url, src ->
                mangaRepository.getMangaByUrlAndSourceId(url, src)
                    ?.let { DonorEntry(it.title, it.thumbnailUrl) }
            }
            if (donor != null) {
                runCatching {
                    updateManga.await(MangaUpdate(id = m.id, title = donor.title))
                }.onSuccess {
                    logcat { "title repair: manga ${m.id} → '${donor.title}'" }
                }
            }
        }
        val novelFavs = try {
            novelRepository.getNovelFavorites()
        } catch (e: Exception) {
            logcat { "title repair: novel list failed: ${e.message}" }
            return
        }
        for (n in novelFavs.filter {
            it.source == CanonicalCatalog.CANONICAL_SOURCE_ID && isPlaceholderTitle(it.title)
        }) {
            val workId = CanonicalCatalog.workIdFromNovelUrl(n.url) ?: continue
            val donor = carrierBoundEntry(
                workId, WorkIdResolver.MEDIUM_BOOKS,
            ) { url, src ->
                novelRepository.getNovelByUrlAndSourceId(url, src)
                    ?.let { DonorEntry(it.title, it.thumbnailUrl) }
            }
            if (donor != null) {
                runCatching {
                    updateNovel.await(NovelUpdate(id = n.id, title = donor.title))
                }.onSuccess {
                    logcat { "title repair: novel ${n.id} → '${donor.title}'" }
                }
            }
        }
    }

    /**
     * Companion repair for rows minted while the catalog carried
     * provisional metadata: a blank canonical cover takes the bound
     * donor extension row's stored cover — the art the user actually
     * had. Non-blank covers are left alone: whether they render is a
     * liveness question only the carrier's live `getMangaDetails` can
     * answer (CanonicalPipelineRunner), and the donor's stored URL may
     * be dead itself. One guard keeps this safe — a donor cover URL
     * shared by a differently-titled library row is poisoned provider
     * metadata (the Item/Overgeared collision) and is skipped.
     */
    private suspend fun restoreDonorCovers() {
        restoreDonorMangaCovers()
        restoreDonorNovelCovers()
    }

    private suspend fun restoreDonorMangaCovers() {
        val favs = try {
            mangaRepository.getMangaFavorites()
        } catch (e: Exception) {
            logcat { "donor cover: manga list failed: ${e.message}" }
            return
        }
        val collidedUrls = collidedCoverUrls(
            favs.map { Row(it.id, it.title, it.source, it.thumbnailUrl) },
        )
        for (m in favs) {
            if (m.source != CanonicalCatalog.CANONICAL_SOURCE_ID) continue
            // Blank fills only: a non-blank cover that isn't preferred
            // belongs to the live-carrier repair in CanonicalPipelineRunner
            // — the donor's stored URL can itself be dead art, and writing
            // it over a live cover would ping-pong.
            if (m.thumbnailUrl?.isNotBlank() == true) continue
            val workId = CanonicalCatalog.workIdFromMangaUrl(m.url) ?: continue
            val donor = carrierBoundEntry(
                workId, WorkIdResolver.MEDIUM_MANGA,
            ) { url, src ->
                mangaRepository.getMangaByUrlAndSourceId(url, src)
                    ?.let { DonorEntry(it.title, it.thumbnailUrl) }
            }
            val donorCover = donor?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: continue
            if (donorCover in collidedUrls) continue
            runCatching {
                updateManga.await(
                    MangaUpdate(
                        id = m.id,
                        thumbnailUrl = donorCover,
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
            }.onSuccess {
                logcat { "donor cover: manga ${m.id} → $donorCover" }
            }
        }
    }

    private suspend fun restoreDonorNovelCovers() {
        val favs = try {
            novelRepository.getNovelFavorites()
        } catch (e: Exception) {
            logcat { "donor cover: novel list failed: ${e.message}" }
            return
        }
        val collidedUrls = collidedCoverUrls(
            favs.map { Row(it.id, it.title, it.source, it.thumbnailUrl) },
        )
        for (n in favs) {
            if (n.source != CanonicalCatalog.CANONICAL_SOURCE_ID) continue
            if (n.thumbnailUrl?.isNotBlank() == true) continue
            val workId = CanonicalCatalog.workIdFromNovelUrl(n.url) ?: continue
            val donor = carrierBoundEntry(
                workId, WorkIdResolver.MEDIUM_BOOKS,
            ) { url, src ->
                novelRepository.getNovelByUrlAndSourceId(url, src)
                    ?.let { DonorEntry(it.title, it.thumbnailUrl) }
            }
            val donorCover = donor?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: continue
            if (donorCover in collidedUrls) continue
            runCatching {
                updateNovel.await(
                    NovelUpdate(
                        id = n.id,
                        thumbnailUrl = donorCover,
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
            }.onSuccess {
                logcat { "donor cover: novel ${n.id} → $donorCover" }
            }
        }
    }

    /** Cover URLs carried by more than one differently-titled row. */
    private fun collidedCoverUrls(rows: List<Row>): Set<String> {
        val holder = HashMap<String, String>()
        val collided = HashSet<String>()
        for (r in rows) {
            val url = r.cover?.takeIf { it.isNotEmpty() } ?: continue
            val t = TitleNormalizer.normalize(r.title).orEmpty()
            val prev = holder.put(url, t)
            if (prev != null && prev != t) collided += url
        }
        return collided
    }

    /**
     * The library entry a bound carrier's provider_ref points at — that
     * row minted the work, so its title/cover are the user's real
     * metadata. Null when no bound ref matches a local row (donor
     * uninstalled or bindings not fetched).
     */
    private data class DonorEntry(val title: String, val thumbnailUrl: String?)

    private suspend fun carrierBoundEntry(
        workId: String,
        medium: String,
        entryAt: suspend (String, Long) -> DonorEntry?,
    ): DonorEntry? {
        val matrix = runCatching {
            Injekt.get<WorkCarriers>().forWork(workId, medium)
        }.getOrNull() ?: return null
        for (carrier in matrix.carriers) {
            if (carrier.providerRef.isBlank()) continue
            for (sourceId in carrier.sourceIds) {
                val entry = runCatching { entryAt(carrier.providerRef, sourceId) }
                    .getOrNull()
                if (entry != null && !isPlaceholderTitle(entry.title)) return entry
            }
        }
        return null
    }

    /**
     * Repair pass for canonical rows minted before the cover-collision
     * guard in [transferFavorite]: a canonical entry holding a cover
     * URL that a differently-titled library row also carries almost
     * certainly inherited poisoned provider art (the provider filed
     * the cover under another work). Clearing the thumbnail breaks
     * HiResCoverResolver's "already hi-res" skip so a resolver pass
     * can fetch the work's real art. Blank beats wrong.
     */
    private suspend fun repairCollidedCovers() {
        repairCollidedMangaCovers()
        repairCollidedNovelCovers()
    }

    private suspend fun repairCollidedMangaCovers() {
        val favs = try {
            mangaRepository.getMangaFavorites()
        } catch (e: Exception) {
            logcat { "cover repair: manga list failed: ${e.message}" }
            return
        }
        for (m in collidedCanonicals(favs.map { Row(it.id, it.title, it.source, it.thumbnailUrl) })) {
            runCatching {
                updateManga.await(
                    MangaUpdate(
                        id = m,
                        thumbnailUrl = "",
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
            }.onSuccess {
                Injekt.get<PreferenceStore>()
                    .getLong("hires_cover_checked_manga_$m", 0L)
                    .delete()
                logcat { "cover repair: cleared collided cover on manga $m" }
            }
        }
    }

    private suspend fun repairCollidedNovelCovers() {
        val favs = try {
            novelRepository.getNovelFavorites()
        } catch (e: Exception) {
            logcat { "cover repair: novel list failed: ${e.message}" }
            return
        }
        for (n in collidedCanonicals(favs.map { Row(it.id, it.title, it.source, it.thumbnailUrl) })) {
            runCatching {
                updateNovel.await(
                    NovelUpdate(
                        id = n,
                        thumbnailUrl = "",
                        coverLastModified = Instant.now().toEpochMilli(),
                    ),
                )
            }.onSuccess {
                Injekt.get<PreferenceStore>()
                    .getLong("hires_cover_checked_novel_$n", 0L)
                    .delete()
                logcat { "cover repair: cleared collided cover on novel $n" }
            }
        }
    }

    private class Row(val id: Long, val title: String, val source: Long, val cover: String?)

    /** Canonical rows whose cover URL is shared with a differently-
     *  titled row — order-independent (any mismatched pair flags the
     *  URL). */
    private fun collidedCanonicals(rows: List<Row>): List<Long> {
        val holder = HashMap<String, Pair<Long, String>>()
        val collided = HashSet<String>()
        for (r in rows) {
            val url = r.cover?.takeIf { it.isNotEmpty() } ?: continue
            val t = TitleNormalizer.normalize(r.title).orEmpty()
            val prev = holder[url]
            if (prev == null) {
                holder[url] = r.id to t
            } else if (prev.first != r.id && prev.second != t) {
                collided += url
            }
        }
        return rows.filter {
            it.source == CanonicalCatalog.CANONICAL_SOURCE_ID && it.cover in collided
        }.map { it.id }
    }

    /**
     * Legacy `NetworkToLocalNovel.await` minted a fresh canonical row on
     * every materialize of a non-favorite entry (`novelsQueries.insert`
     * never bound the reused id), so a work could accrue dozens of
     * `miko:` stubs — and `LIMIT 1` url lookups could land on a bare
     * dupe instead of the favorited copy. Merge each url group into one
     * survivor: favorite wins, else the chapter-richest row; loser
     * chapters re-point at the survivor (history rides the chapter FK),
     * remaining dependents cascade with the delete.
     */
    private suspend fun dedupeCanonicalNovels() {
        val groups = try {
            novelHandler.awaitList {
                novelsQueries.getCanonicalNovelDupes(CanonicalCatalog.CANONICAL_SOURCE_ID)
            }.groupBy { it.url }.values.filter { it.size > 1 }
        } catch (e: Exception) {
            logcat { "canon dedupe: list failed: ${e.message}" }
            return
        }
        for (group in groups) {
            val survivor = group.firstOrNull { it.favorite }
                ?: group.maxByOrNull { it.chapter_count }
                ?: continue
            val losers = group.filter { it._id != survivor._id }
            runCatching {
                novelHandler.await(inTransaction = true) {
                    losers.forEach { loser ->
                        novelchaptersQueries.reassignNovelChapters(survivor._id, loser._id)
                        novelsQueries.deleteNovelById(loser._id)
                    }
                }
            }.onSuccess {
                logcat { "canon dedupe '${survivor.url}': kept ${survivor._id}, removed ${losers.size} dupes" }
            }.onFailure { e ->
                logcat { "canon dedupe '${survivor.url}' failed: ${e.message}" }
            }
        }
    }

    /**
     * Repair pass for entries minted before the `Book N M` compound
     * parse landed: those canons stored `chapter_number = 0` on every
     * row (and dropped `exN` interludes to dedupe), which broke list
     * ordering, next-up, and mark-previous scoping. A row counts as
     * stale when its stored name re-parses to a different number — the
     * chapter-richest same-titled extension row supplies the donor feed
     * for [CanonicalChapterSync.reconcileLocal]. When no donor row
     * survives, numbering alone is re-derived in place and row state is
     * left untouched.
     */
    private suspend fun reconcileLocalCanonNumbering() {
        reconcileLocalCanonNovelRows()
        reconcileLocalCanonMangaRows()
    }

    private fun isStaleLocalCanon(name: String, chapterNumber: Double): Boolean {
        val parsed = ChapterNumberParser.parse(name).number ?: return false
        return parsed != chapterNumber
    }

    private suspend fun reconcileLocalCanonNovelRows() {
        val all = try {
            novelHandler.awaitList { novelsQueries.getAllNovel(tachiyomi.data.entries.novel.NovelMapper::mapNovel) }
        } catch (e: Exception) {
            logcat { "canon reconcile: novel list failed: ${e.message}" }
            return
        }
        for (c in all) {
            if (c.source != CanonicalCatalog.CANONICAL_SOURCE_ID ||
                !c.url.startsWith(CanonicalCatalog.NOVEL_URL_PREFIX)
            ) {
                continue
            }
            val rows = runCatching { getChaptersByNovelId.await(c.id) }.getOrNull()
                ?.filter { it.url.startsWith("canon:") }
                ?.takeIf { it.isNotEmpty() } ?: continue
            if (rows.none { isStaleLocalCanon(it.name, it.chapterNumber) }) continue
            val workId = c.url.removePrefix(CanonicalCatalog.NOVEL_URL_PREFIX)
            val title = TitleNormalizer.normalize(c.title).orEmpty()
            val donor = all.asSequence()
                .filter { it.source != CanonicalCatalog.CANONICAL_SOURCE_ID }
                .filter { TitleNormalizer.normalize(it.title).orEmpty() == title }
                .map { it.id }
                .toList()
                // Sequence lambdas can't suspend — await outside it.
                .mapNotNull { id ->
                    runCatching { getChaptersByNovelId.await(id) }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                }
                .maxByOrNull { it.size }
            if (donor != null) {
                val novel = runCatching { novelRepository.getNovelById(c.id) }.getOrNull() ?: continue
                chapterSync.reconcileLocalNovel(novel, workId, donor)
            } else {
                val updates = rows.mapNotNull { r ->
                    ChapterNumberParser.parse(r.name).number
                        ?.takeIf { it != r.chapterNumber }
                        ?.let { NovelChapterUpdate(id = r.id, chapterNumber = it) }
                }
                if (updates.isNotEmpty()) {
                    runCatching { updateNovelChapter.awaitAll(updates) }
                        .onSuccess { logcat { "canon reconcile '${c.title}': renumbered ${updates.size} rows" } }
                }
            }
        }
    }

    private suspend fun reconcileLocalCanonMangaRows() {
        val all = try {
            mangaHandler.awaitList { mangasQueries.getAllManga(tachiyomi.data.entries.manga.MangaMapper::mapManga) }
        } catch (e: Exception) {
            logcat { "canon reconcile: manga list failed: ${e.message}" }
            return
        }
        for (c in all) {
            if (c.source != CanonicalCatalog.CANONICAL_SOURCE_ID ||
                !c.url.startsWith(CanonicalCatalog.MANGA_URL_PREFIX)
            ) {
                continue
            }
            val rows = runCatching { getChaptersByMangaId.await(c.id) }.getOrNull()
                ?.filter { it.url.startsWith("canon:") }
                ?.takeIf { it.isNotEmpty() } ?: continue
            if (rows.none { isStaleLocalCanon(it.name, it.chapterNumber) }) continue
            val workId = c.url.removePrefix(CanonicalCatalog.MANGA_URL_PREFIX)
            val title = TitleNormalizer.normalize(c.title).orEmpty()
            val donor = all.asSequence()
                .filter { it.source != CanonicalCatalog.CANONICAL_SOURCE_ID }
                .filter { TitleNormalizer.normalize(it.title).orEmpty() == title }
                .map { it.id }
                .toList()
                // Sequence lambdas can't suspend — await outside it.
                .mapNotNull { id ->
                    runCatching { getChaptersByMangaId.await(id) }
                        .getOrNull()?.takeIf { it.isNotEmpty() }
                }
                .maxByOrNull { it.size }
            if (donor != null) {
                chapterSync.reconcileLocal(c, workId, donor)
            } else {
                val updates = rows.mapNotNull { r ->
                    ChapterNumberParser.parse(r.name).number
                        ?.takeIf { it != r.chapterNumber }
                        ?.let { ChapterUpdate(id = r.id, chapterNumber = it) }
                }
                if (updates.isNotEmpty()) {
                    runCatching { updateChapter.awaitAll(updates) }
                        .onSuccess { logcat { "canon reconcile '${c.title}': renumbered ${updates.size} rows" } }
                }
            }
        }
    }

    /**
     * Dead-source fallback: when canonize produced no canon (extension
     * uninstalled, or every live collect failed), the entry's own
     * cached chapter list is still a valid feed — it was fetched by
     * that source when it worked. The repo index recovers the dead
     * source's pkgName (available sources carry the same deterministic
     * ids as installed), and the offline SeriesMatcher gates the feed
     * exactly like a live collect so a wrong resolve can't poison
     * shared canon. `provider_ref` mints the work↔carrier binding
     * server-side, so the picker can offer the dead extension for
     * reinstall — and any other bound carrier in its place.
     */
    private suspend fun postLocalChaptersFeed(
        title: String,
        author: String?,
        url: String,
        sourceId: Long,
        work: MangaNovelIdBridge.WorkRow,
        workId: String,
        isBooks: Boolean,
        chapters: List<Triple<String, String, Long>>,
    ): Boolean {
        if (chapters.isEmpty()) {
            logcat { "localFeed $workId: '$title' has no cached chapters" }
            return false
        }
        // An installed donor resolves its pkg locally — waiting on the
        // repo catalog would drop the feed (and the carrier binding it
        // creates) for sideloaded/fork extensions absent from every
        // repo. The repo path below is only for dead sources whose
        // extension is already uninstalled.
        installedPkgName(sourceId, isBooks)?.let { pkg ->
            return postFeed(title, author, url, pkg, work, workId, chapters)
        }
        // Trigger the catalog fetch when the dead source isn't already
        // visible — the available flow only fills when something calls
        // findAvailableExtensions; a pure wait on it can sit on an empty
        // flow forever on a fresh process. Skipped when the source is
        // already present so a sweep doesn't refetch per entry.
        val currentlyVisible = if (isBooks) {
            novelExtensions.availableExtensionsFlow.value
                .any { ext -> ext.sources.any { s -> s.id == sourceId } }
        } else {
            mangaExtensions.availableExtensionsFlow.value
                .any { ext -> ext.sources.any { s -> s.id == sourceId } }
        }
        if (!currentlyVisible) {
            if (isBooks) {
                runCatching { novelExtensions.findAvailableExtensions() }
            } else {
                runCatching { mangaExtensions.findAvailableExtensions() }
            }
        }
        // Then wait briefly for an emission that *contains* the dead
        // source's pkg — taking first-non-empty can read a partial
        // catalog (repos load in stages). Bounded short: the feed is
        // enrichment, not a gate — a missing pkg must not stall the
        // device-side local mint that follows.
        val pkgName = withTimeoutOrNull(8_000) {
            if (isBooks) {
                novelExtensions.availableExtensionsFlow
                    .first { list -> list.any { ext -> ext.sources.any { s -> s.id == sourceId } } }
                    .firstNotNullOf { ext ->
                        ext.pkgName.takeIf { _ -> ext.sources.any { s -> s.id == sourceId } }
                    }
            } else {
                mangaExtensions.availableExtensionsFlow
                    .first { list -> list.any { ext -> ext.sources.any { s -> s.id == sourceId } } }
                    .firstNotNullOf { ext ->
                        ext.pkgName.takeIf { _ -> ext.sources.any { s -> s.id == sourceId } }
                    }
            }
        } ?: run {
            logcat { "localFeed $workId: no repo pkg for dead source $sourceId" }
            return false
        }
        return postFeed(title, author, url, pkgName, work, workId, chapters)
    }

    /**
     * The donor extension becomes the work's preferred carrier when no
     * preference exists yet — the extension the user actually read the
     * content through is the first real carrier for the minted entry,
     * ahead of sticky/tier ordering in [CanonicalChapterRouter].
     */
    private suspend fun recordDonorCarrier(sourceId: Long, workId: String, isBooks: Boolean) {
        if (sourcePreferences.preferredCarrierFor(workId) != null) return
        val pkg = installedPkgName(sourceId, isBooks) ?: return
        sourcePreferences.setPreferredCarrier(workId, pkg)
        logcat { "canonize $workId: preferred carrier ← $pkg (minted donor)" }
    }

    /** The donor source's extension pkg when that extension is still
     *  installed — the "user has a working extension" fast path that
     *  must mint as the work's first carrier. Bounded-waits on
     *  extension init: the flow starts empty and fills async, so a
     *  mint during startup would otherwise miss an installed donor. */
    private suspend fun installedPkgName(sourceId: Long, isBooks: Boolean): String? {
        withTimeoutOrNull(10_000) {
            if (isBooks) {
                novelExtensions.isInitialized.first { it }
            } else {
                mangaExtensions.isInitialized.first { it }
            }
        }
        return if (isBooks) {
            novelExtensions.installedExtensionsFlow.value
                .filterIsInstance<NovelExtension.Installed>()
                .firstOrNull { e -> e.sources.any { s -> s.id == sourceId } }
                ?.pkgName
        } else {
            mangaExtensions.installedExtensionsFlow.value
                .filterIsInstance<MangaExtension.Installed>()
                .firstOrNull { e -> e.sources.any { s -> s.id == sourceId } }
                ?.pkgName
        }
    }

    private suspend fun postFeed(
        title: String,
        author: String?,
        url: String,
        pkgName: String,
        work: MangaNovelIdBridge.WorkRow,
        workId: String,
        chapters: List<Triple<String, String, Long>>,
    ): Boolean {
        val verdict = matcher.verify(
            SeriesMatcher.Query.of(work),
            SeriesMatcher.Candidate(title = title, author = author),
        )
        if (verdict.decision == SeriesMatcher.Decision.REJECT) {
            logcat { "localFeed $workId: '$title' fails identity gate (${verdict.score})" }
            return false
        }

        val feed = WorkIdentityApi.CanonizeFeed(
            source_key = pkgName,
            tier = mikoDbManager.allSourceMeta()[pkgName]?.tier,
            verified = verdict.decision == SeriesMatcher.Decision.ACCEPT,
            score = verdict.score,
            provider_ref = url.takeIf { it.isNotBlank() },
            provenance = "local_library",
            chapters = chapters.map { (name, u, date) ->
                WorkIdentityApi.FeedChapter(
                    name = name,
                    url = u,
                    date_upload = date.takeIf { it > 0 },
                )
            },
        )
        // Phase 20: bundled mint telemetry rides this canonize body —
        // commitFlush drops the queue only on POST success; a failure
        // marks this feed's pkg failed and keeps the rows.
        val resp = api.canonize(
            WorkIdentityApi.CanonizeRequest(
                work_id = workId,
                feeds = listOf(feed),
                telemetry = mintTelemetry.drain(),
            ),
        ) ?: run {
            mintTelemetry.recordFailed(pkgName)
            logcat { "localFeed $workId: canonize POST failed" }
            return false
        }
        mintTelemetry.commitFlush()
        // A solidify or this device's own provider_ref mint changed the
        // carrier matrix — don't let the 6h cache show stale rows.
        if (resp.solidified.isNotEmpty() || feed.provider_ref != null) {
            workCarriers.invalidate(workId)
        }
        chapterMap.invalidate(workId)
        logcat {
            "localFeed $workId → ${resp.count} canon chapters via $pkgName " +
                "(${verdict.decision} ${verdict.score})"
        }
        return true
    }

    /**
     * The entry's own source as a feed candidate — its URL is the
     * extension's internal shape, so the collector's direct-fetch path
     * verifies it and the feed's `provider_ref` keeps the source
     * reachable as a chapter carrier after migration.
     */
    suspend fun goldenFor(sourceId: Long, url: String, medium: String): GoldenSources.GoldenSource? {
        // Init-time migrate races extension loading — installedExtensionsFlow
        // starts empty and fills on Dispatchers.IO, so reading .value here
        // can find zero extensions and drop the favorite's own carrier from
        // the collect (it then can't mint: the canonize log shows only
        // provider-linked sources attempted).
        withTimeoutOrNull(10_000) {
            if (medium == WorkIdResolver.MEDIUM_BOOKS) {
                novelExtensions.isInitialized.first { it }
            } else {
                mangaExtensions.isInitialized.first { it }
            }
        }
        val found = when (medium) {
            WorkIdResolver.MEDIUM_BOOKS -> novelExtensions.installedExtensionsFlow.value
                .filterIsInstance<NovelExtension.Installed>()
                .firstOrNull { e -> e.sources.any { it.id == sourceId } }
                ?.let { Triple(it.pkgName, it.name, it.sources.map { s -> s.id }) }
            else -> mangaExtensions.installedExtensionsFlow.value
                .filterIsInstance<MangaExtension.Installed>()
                .firstOrNull { e -> e.sources.any { it.id == sourceId } }
                ?.let { Triple(it.pkgName, it.name, it.sources.map { s -> s.id }) }
        }
        if (found == null) {
            val extCount = if (medium == WorkIdResolver.MEDIUM_BOOKS) {
                novelExtensions.installedExtensionsFlow.value.size
            } else {
                mangaExtensions.installedExtensionsFlow.value.size
            }
            logcat { "goldenFor miss: sourceId=$sourceId medium=$medium installedExts=$extCount" }
            return null
        }
        val (pkgName, name, sourceIds) = found
        return GoldenSources.GoldenSource(
            pkgName = pkgName,
            sourceIds = sourceIds,
            name = name,
            tier = "",
            provider = "",
            externalId = null,
            url = url,
        )
    }

    private suspend fun resolveWork(manga: Manga, heavy: Boolean): ResolveOutcome {
        val candidate = WorkIdResolver.Candidate(
            medium = WorkIdResolver.MEDIUM_MANGA,
            title = manga.title,
            url = manga.url,
            author = manga.author,
        )
        val resolved = try {
            if (heavy) resolver.resolve(candidate) else resolver.resolveLocal(candidate)
        } catch (e: Exception) {
            logcat { "canonize skip '${manga.title}': resolve failed (${e.message})" }
            return ResolveOutcome.Miss("resolve error")
        } ?: run {
            logcat { "canonize skip '${manga.title}': no work resolved" }
            return ResolveOutcome.Miss("no matching work")
        }
        if (resolved.ambiguous) {
            logcat { "canonize skip '${manga.title}': ambiguous work ${resolved.value}" }
            return ResolveOutcome.Miss("ambiguous catalog match")
        }
        if (!localMintAllowed(resolved, manga.title)) {
            return ResolveOutcome.Miss("provisional mint blocked")
        }
        return ResolveOutcome.Hit(resolved)
    }

    private suspend fun resolveWork(novel: Novel, heavy: Boolean): ResolveOutcome {
        val candidate = WorkIdResolver.Candidate(
            medium = WorkIdResolver.MEDIUM_BOOKS,
            title = novel.title,
            url = novel.url,
            author = novel.author,
            isbn = WorkIdResolver.isbnFromGenre(novel.genre),
        )
        val resolved = try {
            if (heavy) resolver.resolve(candidate) else resolver.resolveLocal(candidate)
        } catch (e: Exception) {
            logcat { "canonize skip '${novel.title}': resolve failed (${e.message})" }
            return ResolveOutcome.Miss("resolve error")
        } ?: run {
            logcat { "canonize skip '${novel.title}': no work resolved" }
            return ResolveOutcome.Miss("no matching work")
        }
        if (resolved.ambiguous) {
            logcat { "canonize skip '${novel.title}': ambiguous work ${resolved.value}" }
            return ResolveOutcome.Miss("ambiguous catalog match")
        }
        if (!localMintAllowed(resolved, novel.title)) {
            return ResolveOutcome.Miss("provisional mint blocked")
        }
        return ResolveOutcome.Hit(resolved)
    }

    /**
     * `mk:m:` mints are provisional by design — they fork identity
     * whenever the catalog DOES know the work, so they may only be
     * taken when the worker actually answered (network reachable) and
     * still had no match. Offline resolve misses also produce
     * LOCAL_MINT — those must not mint.
     */
    private fun localMintAllowed(
        resolved: WorkIdResolver.CanonicalWorkId,
        title: String,
    ): Boolean {
        if (resolved.confidence != WorkIdResolver.Confidence.LOCAL_MINT) return true
        if (!app.isOnline()) {
            logcat { "canonize skip '$title': local mint while offline" }
            return false
        }
        return true
    }

    /**
     * Favorite transfer — same semantics as the source-migration dialog
     * (chapter-number progress waterline, bookmarks, collections,
     * tracks, custom cover, flags) minus the source chapter sync
     * (canon rows already exist at this point) and the track-service
     * rebind (a canonical row has no single source to rebind through;
     * the copied track row keeps its remote id so tracking continues).
     */
    /**
     * Favorite + progress transfer. Returns false when the extension
     * row carried read progress but no canon row could take it — the
     * caller then keeps the extension favorite rather than strand the
     * user's history on an all-unread entry.
     *
     * Extension rows and canon rows speak different number dialects:
     * the extension writes decimal `chapter_number` (30.5, 1.1) while
     * canon rows carry `number`+`part` from [ChapterNumberParser]. Both
     * name sets are parsed with that same parser so "Ch 30 Part 2",
     * "30b", and "30.5"-style labels converge before matching, and the
     * read waterline is computed in the canon's own numbering space —
     * never across two dialects.
     */
    private suspend fun transferFavorite(
        old: Manga,
        canonical: Manga,
        canonChapters: List<Chapter>,
    ): Boolean {
        val prevChapters = getChaptersByMangaId.await(old.id)
        val canonParsed = canonChapters.associate { it.id to ChapterNumberParser.parse(it.name) }
        val bound = bindCanonChapters(
            canonChapters.map { it to canonParsed.getValue(it.id) },
            prevChapters.map { it to ChapterNumberParser.parse(it.name) },
        )
        // Waterline in canon space: the furthest canon chapter bound
        // to a read extension chapter. Canon rows at or below it are
        // read even without a name-level match (renumbering gaps).
        val maxCanonRead = bound.entries
            .filter { (canon, prev) -> prev.read && canon.isRecognizedNumber }
            .maxOfOrNull { (canon, _) -> canon.chapterNumber }
        // Whole-number buckets consumed on the extension side — a
        // carrier that splits chapter 12 into 12.1/12.2 rows must not
        // resurface consumed content as unread; numbered extras are
        // kind-gated out and still land unread.
        val prevReadBuckets = prevChapters.asSequence()
            .filter { it.read }
            .mapTo(HashSet()) { floor(it.chapterNumber) }

        // Dialect guard for the bucket fallback below: it compares the
        // extension's stored chapter_number column against canon
        // parsed-name numbers — two different scales. A volume-numbered
        // donor (1..50) must not flood-mark a chapter-numbered canon
        // (1..247) read across its whole range; only apply the fallback
        // when both number ranges are commensurate.
        val prevMaxNum = prevChapters.maxOfOrNull { it.chapterNumber } ?: 0.0
        val canonMaxNum = canonChapters.maxOfOrNull { it.chapterNumber } ?: 0.0
        val bucketsCompatible = canonMaxNum <= 0.0 || prevMaxNum >= canonMaxNum * 0.5

        val chapterUpdates = canonChapters.map { chapter ->
            var updated = chapter
            bound[chapter]?.let { prev ->
                // Never write a weaker state — an existing read flag or
                // further page position on the canon row always wins.
                updated = updated.copy(
                    read = updated.read || prev.read,
                    bookmark = updated.bookmark || prev.bookmark,
                    lastPageRead = maxOf(updated.lastPageRead, prev.lastPageRead),
                    dateFetch = if (prev.dateFetch > 0) prev.dateFetch else updated.dateFetch,
                )
            }
            if (!updated.read &&
                updated.isRecognizedNumber &&
                maxCanonRead != null &&
                updated.chapterNumber <= maxCanonRead &&
                canonParsed[chapter.id]?.kind == ChapterNumberParser.Kind.CHAPTER
            ) {
                updated = updated.copy(read = true)
            }
            if (!updated.read &&
                bucketsCompatible &&
                floor(updated.chapterNumber) in prevReadBuckets &&
                canonParsed[chapter.id]?.kind == ChapterNumberParser.Kind.CHAPTER
            ) {
                updated = updated.copy(read = true)
            }
            updated.toChapterUpdate()
        }
        updateChapter.awaitAll(chapterUpdates)

        // Guard: progress existed upstream but nothing landed — a
        // bound row (even unread-but-positioned) or a waterline mark
        // counts as transferred; zero means the numbering spaces never
        // met.
        val progressTransferred = chapterUpdates.any {
            (it.read == true) || (it.lastPageRead ?: 0) > 0
        }
        if (prevChapters.any { it.read || it.lastPageRead > 0 } && !progressTransferred) {
            return false
        }

        val collectionIds = getMangaCollections.await(old.id).map { it.id }
        setMangaCollections.await(canonical.id, collectionIds)

        getMangaTracks.await(old.id)
            .map { it.copy(mangaId = canonical.id) }
            .takeIf { it.isNotEmpty() }
            ?.let { insertMangaTrack.awaitAll(it) }

        if (old.hasCustomCover()) {
            mangaCoverCache.setCustomCoverToCache(
                canonical,
                mangaCoverCache.getCustomCoverFile(old.id).inputStream(),
            )
        }

        // An ext cover already claimed by a differently-titled library
        // row is poisoned upstream (the provider filed the art under
        // another work's provider id — e.g. Item carrying Overgeared's
        // MangaDex cover path). Promoting it stamps the wrong art on
        // the canonical entry AND latches HiResCoverResolver's
        // "already hi-res" skip, so distrust it and let the seeded
        // catalog art (or a later resolver pass) stand.
        val extCoverTrusted = old.thumbnailUrl == null ||
            mangaRepository.getMangaFavorites().none {
                it.id != old.id && it.id != canonical.id &&
                    it.thumbnailUrl == old.thumbnailUrl &&
                    TitleNormalizer.normalize(it.title) != TitleNormalizer.normalize(old.title)
            }
        updateManga.awaitUpdateFavorite(old.id, favorite = false)
        updateManga.await(
            MangaUpdate(
                id = canonical.id,
                favorite = true,
                chapterFlags = old.chapterFlags,
                viewerFlags = old.viewerFlags,
                dateAdded = old.dateAdded,
                title = old.title.takeIf { isPlaceholderTitle(canonical.title) },
                thumbnailUrl = if (extCoverTrusted) {
                    preferredCover(old.thumbnailUrl, canonical.thumbnailUrl)
                } else {
                    null // no change
                },
                coverLastModified = Instant.now().toEpochMilli(),
            ),
        )
        return true
    }

    /**
     * Binds canon rows to extension rows by parsed name — the same
     * ladder [CanonicalChapterMap.bind] uses: kind+number+part, then
     * number+part, then a part-less number-only pair. Each canon row
     * claims its best single predecessor; each predecessor is only
     * claimed once.
     */
    private fun <T> bindCanonChapters(
        canonChapters: List<Pair<T, ChapterNumberParser.Parsed>>,
        prevChapters: List<Pair<T, ChapterNumberParser.Parsed>>,
    ): Map<T, T> {
        val claimed = HashSet<Int>()
        return canonChapters.mapNotNull { (canon, c) ->
            if (c.number == null) return@mapNotNull null
            val candidates = prevChapters.filter { (_, p) -> p.number == c.number }
            val prev = candidates
                .firstOrNull { (_, p) -> p.kind == c.kind && p.part == c.part }
                ?: candidates.firstOrNull { (_, p) -> p.part == c.part }
                ?: candidates.firstOrNull { (_, p) -> p.part == null && c.part == null }
                ?: return@mapNotNull null
            val key = System.identityHashCode(prev.first)
            if (claimed.add(key)) canon to prev.first else null
        }.toMap()
    }

    private suspend fun transferFavorite(
        old: Novel,
        canonical: Novel,
        canonChapters: List<NovelChapter>,
    ): Boolean {
        val prevChapters = getChaptersByNovelId.await(old.id)
        val canonParsed = canonChapters.associate { it.id to ChapterNumberParser.parse(it.name) }
        val bound = bindCanonChapters(
            canonChapters.map { it to canonParsed.getValue(it.id) },
            prevChapters.map { it to ChapterNumberParser.parse(it.name) },
        )
        val maxCanonRead = bound.entries
            .filter { (canon, prev) -> prev.read && canon.isRecognizedNumber }
            .maxOfOrNull { (canon, _) -> canon.chapterNumber }
        val prevReadBuckets = prevChapters.asSequence()
            .filter { it.read }
            .mapTo(HashSet()) { floor(it.chapterNumber) }

        // Dialect guard for the bucket fallback — see the manga
        // overload: extension chapter_number and canon parsed-name
        // numbers are different scales; only apply when commensurate.
        val prevMaxNum = prevChapters.maxOfOrNull { it.chapterNumber } ?: 0.0
        val canonMaxNum = canonChapters.maxOfOrNull { it.chapterNumber } ?: 0.0
        val bucketsCompatible = canonMaxNum <= 0.0 || prevMaxNum >= canonMaxNum * 0.5

        val chapterUpdates = canonChapters.map { chapter ->
            var updated = chapter
            bound[chapter]?.let { prev ->
                updated = updated.copy(
                    read = updated.read || prev.read,
                    bookmark = updated.bookmark || prev.bookmark,
                    lastCharRead = maxOf(updated.lastCharRead, prev.lastCharRead),
                    dateFetch = if (prev.dateFetch > 0) prev.dateFetch else updated.dateFetch,
                )
            }
            if (!updated.read &&
                updated.isRecognizedNumber &&
                maxCanonRead != null &&
                updated.chapterNumber <= maxCanonRead &&
                canonParsed[chapter.id]?.kind == ChapterNumberParser.Kind.CHAPTER
            ) {
                updated = updated.copy(read = true)
            }
            if (!updated.read &&
                bucketsCompatible &&
                floor(updated.chapterNumber) in prevReadBuckets &&
                canonParsed[chapter.id]?.kind == ChapterNumberParser.Kind.CHAPTER
            ) {
                updated = updated.copy(read = true)
            }
            updated.toNovelChapterUpdate()
        }
        updateNovelChapter.awaitAll(chapterUpdates)

        val progressTransferred = chapterUpdates.any {
            (it.read == true) || (it.lastCharRead ?: 0) > 0
        }
        if (prevChapters.any { it.read || it.lastCharRead > 0 } && !progressTransferred) {
            return false
        }

        val collectionIds = getNovelCollections.await(old.id).map { it.id }
        setNovelCollections.await(canonical.id, collectionIds)

        getNovelTracks.await(old.id)
            .map { it.copy(novelId = canonical.id) }
            .takeIf { it.isNotEmpty() }
            ?.let { insertNovelTrack.awaitAll(it) }

        if (old.hasCustomNovelCover()) {
            novelCoverCache.setCustomCoverToCache(
                canonical,
                novelCoverCache.getCustomCoverFile(old.id).inputStream(),
            )
        }

        val extCoverTrusted = old.thumbnailUrl == null ||
            novelRepository.getNovelFavorites().none {
                it.id != old.id && it.id != canonical.id &&
                    it.thumbnailUrl == old.thumbnailUrl &&
                    TitleNormalizer.normalize(it.title) != TitleNormalizer.normalize(old.title)
            }
        updateNovel.awaitUpdateFavorite(old.id, favorite = false)
        updateNovel.await(
            NovelUpdate(
                id = canonical.id,
                favorite = true,
                chapterFlags = old.chapterFlags,
                viewerFlags = old.viewerFlags,
                dateAdded = old.dateAdded,
                title = old.title.takeIf { isPlaceholderTitle(canonical.title) },
                thumbnailUrl = if (extCoverTrusted) {
                    preferredCover(old.thumbnailUrl, canonical.thumbnailUrl)
                } else {
                    null // no change
                },
                coverLastModified = Instant.now().toEpochMilli(),
            ),
        )
        return true
    }

    /**
     * The donor's extension cover wins over the canonical row's seeded
     * catalog art — it is the artwork the user actually browsed and
     * chose, and seeded catalog URLs are provisional until a hi-res
     * provider cover lands. An existing hi-res provider cover
     * (MangaDex/AniList/…) is never restomped: fetched art stays put
     * until the user picks a cover themselves.
     */
    private fun preferredCover(extCover: String?, canonCover: String?): String? =
        if (extCover.isNullOrBlank() || HiResCoverResolver.isPreferredCoverUrl(canonCover)) {
            null // no change
        } else {
            extCover
        }

    /**
     * Placeholder titles minted upstream (MangaBaka files unidentifiable
     * works as "unknown title (please report on Discord)") — a donor
     * entry that minted the work always carries its real title.
     */
    private fun isPlaceholderTitle(title: String?): Boolean {
        val normalized = TitleNormalizer.normalize(title)
        return normalized.isEmpty() || normalized.startsWith("unknown title")
    }

    private companion object {
        /** Multi-source progress-bus section name — renders as its own
         *  overlay row so the mint pass is visible like a library fetch. */
        const val PROGRESS_SOURCE = "MikoDB"
    }
}
