package eu.kanade.tachiyomi.data.mikodb

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.library.model.LibrarySubtype
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Local-only subtype classification behind the library's sub-category
 * filter (Manga/Webtoon, Book/Novel, Series/Movie) — mirrors the
 * sub-type pills in discovery. Reads `works_cache` kinds through
 * canonical `miko:` URLs or [WorkIdResolver.resolveLocal] title/external
 * hits; never touches the Worker. Memoized per entry id — a favorite's
 * subtype only moves when its canonical binding changes, which produces
 * a different entry row anyway.
 *
 * RESOLVED classifications persist across sessions ([subtypeStore]) —
 * the Nuvio lesson: a subtype is a property of the entry, not something
 * to recompute per cold start, and persisting it keeps both the library
 * load and the first subtype switch instant. Only resolved kinds are
 * persisted: an entry works_cache hasn't learned yet may upgrade later,
 * and persisting its fallback would freeze the wrong subtype forever.
 */
class LibrarySubtypeResolver(
    private val resolver: WorkIdResolver = Injekt.get(),
    private val bridge: MangaNovelIdBridge = Injekt.get(),
    preferenceStore: PreferenceStore = Injekt.get(),
) {

    private val mangaSubtypes = ConcurrentHashMap<Long, LibrarySubtype>()
    private val novelSubtypes = ConcurrentHashMap<Long, LibrarySubtype>()

    /** `m{id}=SUBTYPE` / `n{id}=SUBTYPE` — resolved classifications only. */
    private val subtypeStore = preferenceStore.getStringSet("library_subtype_map_v1", emptySet())
    private var persistedLoaded = false
    private val persistLock = Any()
    private val pendingPersist = ConcurrentHashMap.newKeySet<String>()

    /** Same set the discovery "Webtoons" pill feeds the worker
     *  (`WEBTOON_KINDS_CSV`); comics/unknown kinds stay on the manga side. */
    private val webtoonKinds = setOf("manhwa", "manhua", "webtoon", "oel", "other")

    /**
     * Whole-list classification, parallel across entries — serial
     * [subtype] calls stall the library combine for seconds on a
     * 300+ entry library (each miss runs the [WorkIdResolver.resolveLocal]
     * query chain). Memoized per id, so repeat calls are map reads.
     * Flushes resolved classifications to the persistent store at the end.
     */
    suspend fun classifyManga(manga: List<Manga>): Map<Long, LibrarySubtype> = coroutineScope {
        loadPersisted()
        val result = manga.distinctBy { it.id }
            .map { async { it.id to subtype(it) } }
            .awaitAll()
            .toMap()
        flushPersisted()
        result
    }

    suspend fun classifyNovels(novels: List<Novel>): Map<Long, LibrarySubtype> = coroutineScope {
        loadPersisted()
        val result = novels.distinctBy { it.id }
            .map { async { it.id to subtype(it) } }
            .awaitAll()
            .toMap()
        flushPersisted()
        result
    }

    suspend fun subtype(manga: Manga): LibrarySubtype {
        loadPersisted()
        return mangaSubtypes.getOrPut(manga.id) {
            val kind = mangaKind(manga)?.lowercase()
            val subtype = if (kind != null && kind in webtoonKinds) {
                LibrarySubtype.WEBTOON
            } else {
                LibrarySubtype.MANGA
            }
            if (kind != null) pendingPersist += "m${manga.id}=$subtype"
            subtype
        }
    }

    suspend fun subtype(novel: Novel): LibrarySubtype {
        loadPersisted()
        return novelSubtypes.getOrPut(novel.id) {
            // The audiobook flag is a local bit — it wins first, no
            // works_cache wait. Anna's Archive/OpenLibrary rows are
            // published books even before a canonical work resolves.
            val resolved = novel.source == ANNAS_ARCHIVE_SOURCE_ID
            val kind = novelKind(novel)
            val subtype = when {
                novel.isAudiobook -> LibrarySubtype.AUDIOBOOK
                resolved || kind == "book" -> LibrarySubtype.BOOK
                else -> LibrarySubtype.NOVEL
            }
            if (resolved || kind != null) pendingPersist += "n${novel.id}=$subtype"
            subtype
        }
    }

    fun subtype(anime: Anime): LibrarySubtype =
        if (WorkIdResolver.cinemetaRef(anime.url)?.first == "movie") {
            LibrarySubtype.MOVIE
        } else {
            LibrarySubtype.SERIES
        }

    private fun loadPersisted() {
        synchronized(persistLock) {
            if (persistedLoaded) return
            persistedLoaded = true
            subtypeStore.get().forEach { e ->
                val prefix = e.substringBefore('=').let { it.firstOrNull() to it.drop(1) }
                val subtype = runCatching {
                    LibrarySubtype.valueOf(e.substringAfter('='))
                }.getOrNull() ?: return@forEach
                val id = prefix.second.toLongOrNull() ?: return@forEach
                when (prefix.first) {
                    'm' -> mangaSubtypes.putIfAbsent(id, subtype)
                    'n' -> novelSubtypes.putIfAbsent(id, subtype)
                }
            }
        }
    }

    private fun flushPersisted() {
        val pending = pendingPersist.toList()
        if (pending.isEmpty()) return
        pendingPersist.removeAll(pending)
        synchronized(persistLock) {
            subtypeStore.set(subtypeStore.get() + pending)
        }
    }

    private suspend fun mangaKind(manga: Manga): String? {
        CanonicalCatalog.workIdFromMangaUrl(manga.url)
            ?.let { return bridge.byWorkId(it)?.type }
        return resolver.resolveLocal(
            WorkIdResolver.Candidate(
                medium = WorkIdResolver.MEDIUM_MANGA,
                title = manga.title,
                url = manga.url,
                author = manga.author,
            ),
        )?.kind
    }

    private suspend fun novelKind(novel: Novel): String? {
        CanonicalCatalog.workIdFromNovelUrl(novel.url)
            ?.let { return bridge.byWorkId(it)?.type }
        return resolver.resolveLocal(
            WorkIdResolver.Candidate(
                medium = WorkIdResolver.MEDIUM_BOOKS,
                title = novel.title,
                url = novel.url,
                author = novel.author,
                isbn = WorkIdResolver.isbnFromGenre(novel.genre),
            ),
        )?.kind
    }

    private companion object {
        const val ANNAS_ARCHIVE_SOURCE_ID = 6400L
    }
}
