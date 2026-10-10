package eu.kanade.tachiyomi.source.novel.ia

import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.novelsource.model.NovelsPage
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.source.novel.PoliteFetch
import kotlinx.serialization.decodeFromString
import java.net.URLEncoder

/**
 * Built-in Internet Archive source — IA's wider spoken-word catalog as a
 * first-class catalogue with zero configuration (22-03, SD-3/SD-4/SD-7).
 *
 * Fixed id [IA_SOURCE_ID] = -3L — reserved beside ABS (-1L) and LibriVox
 * (-2L); negative ids can never collide with extension `generateId`
 * MD5s. Seeded into AndroidNovelSourceManager via `builtInSources`.
 *
 * Items materialize as `ia://item/{identifier}` novels; audio files as
 * `ia://track/{identifier}/{fileName}` chapters (pure-parse →
 * archive.org/download stream). Chapters are audio — [getChapterText]
 * throws (22-01 SD-8 convention).
 *
 * **LibriVox dedup is two gates** (T-22-03-04): the browse query carries
 * `NOT collection:librivoxaudio`, AND any doc/item whose
 * `external-identifier`/`identifier-access` mentions librivox is dropped
 * — at result-map time AND again at chapter-list time. LibriVox owns
 * those recordings; nothing double-shelves.
 *
 * Every request funnels through [PoliteFetch]: ≤1 rps, descriptive UA,
 * Retry-After/backoff — no auth headers ever.
 */
class InternetArchiveSource : NovelCatalogueSource {

    override val id: Long = IA_SOURCE_ID
    override val name: String = "Internet Archive"
    override val lang: String = "all"
    override val supportsLatest: Boolean = true

    /**
     * Transport seam — swapped by tests, [PoliteFetch] in production.
     * Carries the full `https://archive.org/…` request URL.
     */
    internal var transport: suspend (url: String) -> String = { url ->
        PoliteFetch.get(url)
    }

    // ---- Browse ------------------------------------------------------------

    override suspend fun getPopularNovels(page: Int): NovelsPage =
        getPopularNovels(page, getFilterList())

    /** Spoken-word scope ordered by downloads (SD-3). */
    override suspend fun getPopularNovels(page: Int, filters: NovelFilterList): NovelsPage =
        search(SPOKEN_WORD_QUERY, page, sort = "downloads desc")

    /** `addeddate desc` — newest uploads to the spoken-word collections. */
    override suspend fun getLatestUpdates(page: Int): NovelsPage =
        getLatestUpdates(page, getFilterList())

    override suspend fun getLatestUpdates(page: Int, filters: NovelFilterList): NovelsPage =
        search(SPOKEN_WORD_QUERY, page, sort = "addeddate desc")

    /** Spoken-word scope ∧ title/creator text match (SD-3). */
    override suspend fun getSearchNovels(page: Int, query: String, filters: NovelFilterList): NovelsPage {
        val escaped = query.replace("\"", " ").trim()
        if (escaped.isBlank()) return getPopularNovels(page, filters)
        return search(
            "$SPOKEN_WORD_QUERY AND (title:\"$escaped\" OR creator:\"$escaped\")",
            page,
            sort = "downloads desc",
        )
    }

    override fun getFilterList(): NovelFilterList = NovelFilterList()

    private suspend fun search(q: String, page: Int, sort: String): NovelsPage {
        val body = iaJson.decodeFromString<IaSearchResponse>(transport(searchUrl(q, page, sort)))
            .response
        return NovelsPage(
            body.docs.mapNotNull(::docToSNovel),
            hasNextPage = body.start + PAGE_SIZE < body.numFound,
        )
    }

    private fun searchUrl(q: String, page: Int, sort: String): String = buildString {
        append("https://archive.org/advancedsearch.php")
        append("?q=").append(urlEncode(q))
        SEARCH_FIELDS.forEach { append("&fl[]=").append(urlEncode(it)) }
        append("&rows=$PAGE_SIZE")
        append("&page=${page.coerceAtLeast(1)}")
        append("&sort[]=").append(urlEncode(sort))
        append("&output=json")
    }

    // ---- Details / chapters --------------------------------------------------

    /** `metadata/{id}` → full SNovel; `creator`→author, no narrator field
     *  → artist stays null (SD-4). */
    override suspend fun getNovelDetails(novel: SNovel): SNovel {
        val identifier = itemIdFrom(novel.url) ?: return novel
        val meta = fetchMetadata(identifier).metadata
        return novel.copy().apply {
            title = meta.title.takeIf { it.isNotBlank() } ?: title
            author = meta.creator.asStringList()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            description = meta.description.asStringList()
                .joinToString("\n\n")
                .stripHtmlLite()
                .takeIf { it.isNotBlank() }
            genre = meta.subject.asStringList()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            thumbnail_url = "$IA_BASE/services/img/$identifier"
            status = SNovel.COMPLETED
            initialized = true
        }
    }

    /**
     * `files[]` filtered to audio formats, ordered by track/title/name
     * → `ia://track/{id}/{file}` chapters (SD-3).
     *
     * Dedup gate 2 (SD-7): if the item's metadata links back to
     * librivox.org the whole chapter list is empty — LibriVoxSource owns
     * that recording even when it slipped past the query exclusion.
     */
    override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
        val identifier = itemIdFrom(novel.url) ?: return emptyList()
        val resp = fetchMetadata(identifier)
        if (resp.metadata.hasLibrivoxBacklink()) return emptyList()
        return resp.files
            .filter { it.format in AUDIO_FORMATS && it.name.isNotBlank() }
            .sortedWith(
                compareBy(
                    { it.trackNumber() ?: Int.MAX_VALUE },
                    { (it.title ?: it.name).lowercase() },
                ),
            )
            .mapIndexed { index, file ->
                SNovelChapter.create().apply {
                    url = trackUrl(identifier, file.name)
                    name = file.title?.takeIf { it.isNotBlank() } ?: file.name
                    chapter_number = file.trackNumber()?.toFloat() ?: (index + 1).toFloat()
                }
            }
    }

    /** Audio chapters — no text (22-01 SD-8 convention). */
    override suspend fun getChapterText(chapter: SNovelChapter): String {
        throw UnsupportedOperationException("Internet Archive chapters are audio files — no text")
    }

    // ---- plumbing ------------------------------------------------------------

    private suspend fun fetchMetadata(identifier: String): IaMetadataResponse =
        iaJson.decodeFromString(transport("$IA_BASE/metadata/$identifier"))

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    // ---- mapping -------------------------------------------------------------

    /** Dedup gate 1 — docs carrying a librivox.org back-link are dropped
     *  from browse/search results before they ever shelf (SD-7). */
    private fun docToSNovel(doc: IaDoc): SNovel? {
        if (doc.identifier.isBlank() || doc.hasLibrivoxBacklink()) return null
        return SNovel.create().apply {
            url = itemUrl(doc.identifier)
            title = doc.title.takeIf { it.isNotBlank() } ?: doc.identifier
            author = doc.creator.asStringList()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            description = doc.description.asStringList()
                .joinToString("\n\n")
                .stripHtmlLite()
                .takeIf { it.isNotBlank() }
            genre = doc.subject.asStringList()
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            thumbnail_url = "$IA_BASE/services/img/${doc.identifier}"
        }
    }

    /** `track` may be "3", "3/12", or a number — leading int wins. */
    private fun IaFile.trackNumber(): Int? =
        track.firstString()?.substringBefore('/')?.trim()?.toIntOrNull()

    private fun String.stripHtmlLite(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        /** Reserved built-in id — ABS=-1L, LibriVox=-2L. */
        const val IA_SOURCE_ID = -3L

        private const val IA_BASE = "https://archive.org"
        private const val PAGE_SIZE = 50
        private const val IA_ITEM_PREFIX = "ia://item/"

        /**
         * Spoken-word scope minus everything LibriVoxSource owns (SD-3) —
         * the query-level dedup gate; `external-identifier` in [SEARCH_FIELDS]
         * feeds the doc-level gate.
         */
        private const val SPOKEN_WORD_QUERY =
            "(mediatype:audio) AND (subject:\"audiobook\" OR subject:\"Audiobooks\" OR " +
                "collection:audio_book OR collection:spoken_word) AND NOT collection:librivoxaudio"

        private val SEARCH_FIELDS = listOf(
            "identifier", "title", "creator", "date", "year", "description",
            "subject", "language", "downloads", "avg_rating",
            "external-identifier", "identifier-access",
        )

        private val AUDIO_FORMATS = setOf(
            "VBR MP3",
            "128Kbps MP3",
            "64Kbps MP3",
            "MP3",
            "MPEG4",
            "Ogg Vorbis",
        )

        fun itemUrl(identifier: String): String = "$IA_ITEM_PREFIX$identifier"

        fun trackUrl(identifier: String, fileName: String): String =
            "ia://track/$identifier/$fileName"

        /** `ia://item/{id}` → id, tolerating trailing segments. */
        fun itemIdFrom(url: String): String? =
            url.takeIf { it.startsWith(IA_ITEM_PREFIX) }
                ?.removePrefix(IA_ITEM_PREFIX)
                ?.split('/')
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
    }
}
