package eu.kanade.tachiyomi.data.comments

import eu.kanade.domain.ui.model.Carrier
import eu.kanade.tachiyomi.data.mikodb.MikoDbManager
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps a library entry to its canonical `threadId`
 * (`shows|manga|books:<workId>` — Phase 15 carrier vocabulary).
 *
 * Shows identity prefers an imdb-derived `mk:imdb:` mint (deterministic
 * across devices regardless of which provider id they know), falls back
 * to the 15-02 resolve path (`mk:hs:`/provisional `mk:m:`), then to
 * tracker proto-ids (`shows:al:`/`shows:mal:` — the shim that keeps the
 * migrated `anime:al:`/`anime:mal:` threads alive). Manga/books go
 * through [WorkIdResolver]. Results are cached per entry id — work
 * identity is stable for the lifetime of a row.
 */
class ThreadIdResolver(
    private val workIdResolver: WorkIdResolver = Injekt.get(),
    private val mikoDbManager: MikoDbManager = Injekt.get(),
    private val getAnimeTracks: GetAnimeTracks = Injekt.get(),
) {

    /** entryId-keyed cache; [UNRESOLVED] marks "checked, no identity". */
    private val cache = ConcurrentHashMap<String, String>()

    suspend fun forManga(manga: Manga): String? = cached("m:${manga.id}") {
        workIdResolver.resolve(
            WorkIdResolver.Candidate(
                medium = Carrier.MANGA.medium,
                title = manga.title,
                url = manga.url,
                author = manga.author,
            ),
        )?.let { "manga:${it.value}" }
    }

    suspend fun forNovel(novel: Novel): String? = cached("n:${novel.id}") {
        workIdResolver.resolve(
            WorkIdResolver.Candidate(
                medium = Carrier.BOOKS.medium,
                title = novel.title,
                url = novel.url,
                author = novel.author,
                isbn = WorkIdResolver.isbnFromGenre(novel.genre),
                isBook = novel.source == ANNAS_ARCHIVE_SOURCE_ID,
            ),
        )?.let { "books:${it.value}" }
    }

    /**
     * Emission order (15-03): an imdb-bound show emits `shows:mk:imdb:…`
     * with no Worker round-trip — the canonical mint for an imdb id IS
     * `mk:imdb:<tt>` and the migrated threads already use that shape
     * (0006_taxonomy.sql). Everything else resolves through the 15-02
     * shows path (`mk:hs:`, provisional `mk:m:`); a bare `mk:m:` mint
     * yields to `shows:al:`/`shows:mal:` tracker proto-ids — deterministic
     * cross-device and reconnects migrated threads. `null` only when
     * nothing resolves AND no tracker id exists.
     */
    suspend fun forShows(anime: Anime): String? = cached("s:${anime.id}") {
        // Cinemeta entries carry the imdb id in the url.
        cinemetaImdb(anime.url)?.let { return@cached "shows:mk:imdb:$it" }

        val bridge = mikoDbManager.bridge()
        val tracks = runCatching { getAnimeTracks.await(anime.id) }.getOrDefault(emptyList())
        val anilistId = tracks.firstOrNull { it.trackerId == TrackerManager.ANILIST }?.remoteId
        val malId = tracks.firstOrNull { it.trackerId == MAL_TRACKER }?.remoteId

        // Strongest form first: the anime map bridges provider ids up to
        // imdb — the mk:imdb: mint is identical on every device.
        anilistId?.let { id -> bridge?.byAnilist(id)?.imdbId }
            ?.let { return@cached "shows:mk:imdb:$it" }
        malId?.let { id -> bridge?.byMal(id)?.imdbId }
            ?.let { return@cached "shows:mk:imdb:$it" }
        bridge?.byTitle(anime.title)?.imdbId
            ?.let { return@cached "shows:mk:imdb:$it" }

        // Unmapped shows resolve through the 15-02 path — hentaistream
        // urls mint mk:hs:, the rest get a shows-kind mk:m: provisional.
        val resolved = workIdResolver.resolve(
            WorkIdResolver.Candidate(
                medium = Carrier.SHOWS.medium,
                title = anime.title,
                url = anime.url,
                kindHint = WorkIdResolver.cinemetaRef(anime.url)?.first,
            ),
        )
        if (resolved != null && !resolved.value.startsWith("mk:m:")) {
            return@cached "shows:${resolved.value}"
        }

        // Provider-level proto-id fallback when only a per-device mk:m:
        // mint exists — deterministic and keeps the migrated
        // shows:al:/shows:mal: threads reachable.
        anilistId?.let { return@cached "shows:al:$it" }
        malId?.let { return@cached "shows:mal:$it" }
        resolved?.let { return@cached "shows:${it.value}" }
        null
    }

    private suspend fun cached(key: String, resolve: suspend () -> String?): String? {
        when (val hit = cache[key]) {
            UNRESOLVED -> return null
            null -> Unit
            else -> return hit
        }
        val value = resolve()
        cache[key] = value ?: UNRESOLVED
        return value
    }

    /** "cinemeta:series:tt0903747" / "cinemeta:episode:tt0903747:1:1" → "tt0903747" */
    private fun cinemetaImdb(url: String): String? =
        url.takeIf { it.startsWith("cinemeta:") }
            ?.split(":")
            ?.getOrNull(2)
            ?.takeIf { it.startsWith("tt") }

    private companion object {
        const val MAL_TRACKER = 1L
        const val UNRESOLVED = ""

        /** Anna's Archive (MikoNovelSources) — published-book source. */
        const val ANNAS_ARCHIVE_SOURCE_ID = 6400L
    }
}
