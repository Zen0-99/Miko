package eu.kanade.tachiyomi.data.catalogrows

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.mikodb.MangaNovelIdBridge.WorkRow
import eu.kanade.tachiyomi.data.mikodb.TitleNormalizer
import eu.kanade.tachiyomi.data.mikodb.WorkIdResolver
import eu.kanade.tachiyomi.data.mikodb.WorksBrowseApi
import eu.kanade.tachiyomi.metadata.openlibrary.OpenLibraryTrending

/**
 * One row of the idle catalog grid — [work] materializes on tap;
 * [coverUrl] renders the poster. [mintCandidate] is non-null only for
 * non-canonical rows (live OpenLibrary trending fallback):
 * browsing resolves them via the Worker before reporting `markSeen`.
 * Canonical browse rows were minted by the Worker already — seeded rows
 * skip reporting entirely (no coverage signal).
 */
@Immutable
data class CatalogItem(
    val work: WorkRow,
    val coverUrl: String? = null,
    val mintCandidate: WorkIdResolver.Candidate? = null,
)

/** The discovery "Webtoons" pill's kind filter — served by the
 *  worker's `/v1/works/browse` like every other pill (platform-linked
 *  rows carry the derived `webtoon` kind; the rest of the csv keeps
 *  non-platform manhwa/manhua visible alongside). */
const val WEBTOON_KINDS_CSV = "manhwa,manhua,webtoon,oel,other"

/** Provisional work-id prefix for extension-sourced rows that
 *  haven't resolved to a canonical `mk:` id yet. */
const val EXT_ID_PREFIX = "ext:"

/** WorkRow.externalIds keys carrying the extension fallback
 *  coordinates for provisional rows. */
const val EXT_SOURCE_KEY = "ext_source"
const val EXT_URL_KEY = "ext_url"

internal fun WorksBrowseApi.WorkDto.toCatalogItem() = CatalogItem(
    work = WorkRow(
        mikoWorkId = miko_work_id,
        type = kind ?: "",
        title = TitleNormalizer.displayTitle(title),
        author = author,
        year = year?.toLong(),
        nsfw = nsfw,
        resolutionSource = "works_browse",
        genres = genres.takeIf { it.isNotEmpty() },
        coverUrl = cover_url,
    ),
    coverUrl = cover_url,
)

internal fun OpenLibraryTrending.Item.toCatalogItem(): CatalogItem {
    val workUrl = "https://openlibrary.org/works/$workKey"
    return CatalogItem(
        work = WorkRow(
            // The Worker mints `mk:ol:{workKey}` for OL works — the
            // row materializes deterministically under that id.
            mikoWorkId = "mk:ol:$workKey",
            type = "book",
            title = TitleNormalizer.displayTitle(title),
            author = author,
            year = year?.toLong(),
            resolutionSource = "openlibrary",
            coverUrl = coverUrl,
            externalIds = mapOf("openlibrary" to workKey),
        ),
        coverUrl = coverUrl,
        mintCandidate = WorkIdResolver.Candidate(
            medium = WorkIdResolver.MEDIUM_BOOKS,
            title = title,
            url = workUrl,
            author = author,
            isbn = isbn,
            isBook = true,
            kindHint = "book",
        ),
    )
}

