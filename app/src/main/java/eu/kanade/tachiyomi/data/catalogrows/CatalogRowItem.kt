package eu.kanade.tachiyomi.data.catalogrows

import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMetaShort
import eu.kanade.tachiyomi.metadata.tmdb.TmdbGenres.MediaType

/**
 * One rendered card inside a catalog row — the feed-agnostic shape the
 * home UI binds directly.
 *
 * [coverUrl] is passed straight to `AsyncImage` (a String URL for
 * metadata feeds, a cover model for library items).
 * [releaseInfo]/[imdbRating] carry the TMDB fields needed to rebuild a
 * `MikoMetaShort` at tap time (the discovery tap-path precedent).
 * [mintKey] dedupes the mint-on-browse resolve per session; when the
 * item can express its resolve statically, [mintCandidate] carries the
 * candidate — TMDB items can't (the Cinemeta URL needs the async
 * `external_ids` lookup first), so the screen model owns that arm.
 */
data class RowItem(
    val title: String,
    val coverUrl: Any?,
    val subtitle: String? = null,
    val target: RowTarget,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val mintCandidate: WorkIdResolver.Candidate? = null,
    val mintKey: String? = null,
)

/**
 * Where tapping a [RowItem] lands — resolved to a Voyager screen by the
 * home screen model.
 *
 * - [CanonicalShows] — a TMDB-discovered show/movie; the tap does the
 *   `external_ids` → IMDB → Cinemeta meta → `networkToLocalAnime` hop.
 * - [CinemetaEntry] — a Cinemeta catalog meta; materializes like the
 *   TMDB path minus the `external_ids` lookup.
 * - [CanonicalWork] — a MikoDB work row; carries the full `WorkRow`
 *   (browse responses already have it and `materializeMangaId`/
 *   `materializeNovelId` require it — `byWorkId` alone dead-ends on
 *   `mk:ol:` works not yet in `works_cache`).
 * - [ExtensionEntry] — an extension-source entry (`sourceId` + `url`
 *   materialize to the local entry at tap).
 * - [LibraryEntry] — a library entry id in its medium's table.
 */
sealed interface RowTarget {
    data class CanonicalShows(val tmdbId: Long, val mediaType: MediaType) : RowTarget
    data class CinemetaEntry(val meta: MikoMetaShort) : RowTarget
    /** Carries the spec's medium — tap materializes via
     *  `materializeMangaId`/`materializeNovelId` on it. */
    data class CanonicalWork(val work: MangaNovelIdBridge.WorkRow, val medium: String) : RowTarget
    data class ExtensionEntry(val sourceId: Long, val url: String, val medium: String) : RowTarget
    data class LibraryEntry(val entryId: Long, val medium: String) : RowTarget
}

/**
 * Sort vocabulary for `Feed.Library` rows — backed by the library row
 * fields (`dateAdded`, `lastRead`/`lastSeen`, `title`) with no repo
 * changes needed.
 */
enum class LibrarySort { TITLE, RECENTLY_ADDED, LAST_READ }
