package eu.kanade.tachiyomi.data.mikodb

import android.database.sqlite.SQLiteDatabase
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import uy.kohesive.injekt.injectLazy
import java.io.File

/**
 * Canonical work identity lookup for manga/novel/comic entries (NCM-001).
 *
 * Read order:
 *  1. `works_cache` (SQLDelight) — the per-work delta store filled by the
 *     comments Worker's /v1/works/resolve responses and by local mints.
 *  2. Optional full `miko-manga-map.db` artifact — only present when the
 *     user enabled the offline-canonicalization download.
 *
 * See `.planning/phases/07-manga-novel-canonical-id-pipeline/07-CONTEXT.md`.
 */
class MangaNovelIdBridge(
    private val mapFile: () -> File,
) {

    data class WorkRow(
        val mikoWorkId: String,
        /** The worker-assigned kind — the wire `type` column IS kind
         *  (Phase 15 taxonomy: no column rename). */
        val type: String,
        val title: String,
        val romanizedTitle: String? = null,
        val nativeTitle: String? = null,
        val year: Long? = null,
        val totalChapters: String? = null,
        val author: String? = null,
        val status: String? = null,
        /** Orthogonal NSFW flag (Phase 15); absent in old map artifacts →
         *  treated as false (schema-version tolerance). */
        val nsfw: Boolean = false,
        val providerLinks: String = "[]",
        val resolutionSource: String = "",
        val externalIds: Map<String, String> = emptyMap(),
        /** Carry-only catalog metadata (Phase 15-07 browse rows):
         *  populated by callers, never persisted to works_cache — the
         *  materialize path writes them onto the entry row directly. */
        val genres: List<String>? = null,
        val coverUrl: String? = null,
        val description: String? = null,
        /** Non-primary catalog titles (scanlation vs publisher names
         *  diverge — "SSS-Class Suicide Hunter" vs "SSS-Class Revival
         *  Hunter"). Feeds SeriesMatcher's title set so a carrier feed
         *  named by the alt title still verifies. */
        val altTitles: List<String> = emptyList(),
    ) {
        /** Kind vocabulary alias for [type] — same column, new name. */
        val kind: String get() = type
    }

    private val handler: MangaDatabaseHandler by injectLazy()
    private val networkHelper: NetworkHelper by injectLazy()
    private val json: Json by injectLazy()
    private val identityApi: WorkIdentityApi by injectLazy()

    @Volatile private var mapDb: SQLiteDatabase? = null

    private fun fullDb(): SQLiteDatabase? {
        mapDb?.let { return it }
        synchronized(this) {
            mapDb?.let { return it }
            // Retry-on-null: the artifact may not exist on the first call
            // (pre-sync) and appears later — latching a miss would starve
            // altTitlesFor + the works fallback for the whole session.
            mapDb = try {
                val f = mapFile()
                if (f.exists()) {
                    SQLiteDatabase.openDatabase(f.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            return mapDb
        }
    }

    /** Close + forget the full-db handle (called when the artifact is swapped). */
    fun invalidateFullDb() {
        synchronized(this) {
            mapDb?.close()
            mapDb = null
        }
    }

    // ---------- reads ----------

    suspend fun byWorkId(workId: String): WorkRow? {
        handler.awaitOneOrNull { works_cacheQueries.selectByWorkId(workId) }
            ?.let { row ->
                return row.toWorkRow(externalIdsFor(workId))
                    .copy(altTitles = altTitlesFor(workId))
            }
        return fullDb()?.let { db ->
            db.rawQuery("SELECT * FROM works WHERE miko_work_id = ?", arrayOf(workId))
                .use { c -> if (c.moveToNext()) c.toWorkRow(db) else null }
                ?.copy(altTitles = altTitlesFor(workId))
        }
    }

    /**
     * The work's non-primary catalog titles from the map artifact — the
     * `titles` table carries no miko_work_id index, so this is a scan
     * (~250ms on a 2M-row artifact): single-row callers only, never
     * per-row inside list results.
     */
    private fun altTitlesFor(workId: String): List<String> {
        val db = fullDb() ?: return emptyList()
        return try {
            db.rawQuery(
                "SELECT title FROM titles WHERE miko_work_id = ? AND is_primary = 0",
                arrayOf(workId),
            ).use { c ->
                buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun byExternalId(provider: String, externalId: String): WorkRow? {
        handler.awaitOneOrNull { works_cacheQueries.selectByExternalId(provider, externalId) }
            ?.let { row ->
                return row.toWorkRow(externalIdsFor(row.miko_work_id))
            }
        return fullDb()?.let { db ->
            db.rawQuery(
                "SELECT w.* FROM works w JOIN source_ids s ON s.miko_work_id = w.miko_work_id " +
                    "WHERE s.provider = ? AND s.external_id = ? " +
                    "UNION SELECT w.* FROM works w JOIN provider_links l ON l.miko_work_id = w.miko_work_id " +
                    "WHERE l.provider = ? AND l.external_id = ?",
                arrayOf(provider, externalId, provider, externalId),
            ).use { c -> if (c.moveToNext()) c.toWorkRow(db) else null }
        }
    }

    suspend fun byNormalizedTitle(normalizedTitle: String, type: String? = null): List<WorkRow> {
        val cached = handler.awaitList { works_cacheQueries.selectByNormalizedTitle(normalizedTitle, type) }
        if (cached.isNotEmpty()) {
            return cached.map { it.toWorkRow(externalIdsFor(it.miko_work_id)) }
        }
        return fullDb()?.let { db ->
            val args = if (type != null) arrayOf(normalizedTitle, type) else arrayOf(normalizedTitle)
            val typeFilter = if (type != null) "AND w.type = ?" else ""
            db.rawQuery(
                "SELECT w.* FROM works w JOIN titles t ON t.miko_work_id = w.miko_work_id " +
                    "WHERE t.normalized = ? $typeFilter GROUP BY w.miko_work_id",
                args,
            ).use { c ->
                buildList { while (c.moveToNext()) add(c.toWorkRow(db)) }
            }
        }.orEmpty()
    }

    /**
     * Catalog search over normalized titles (NCM-007 / Phase 12-01).
     *
     * Matches whole-title prefixes (`q%`) and word-boundary substrings
     * (`% q%`) — the prefix arm rides `idx_titles_norm`; the word arm is a
     * bounded scan capped by [limit]. Delta rows are merged in so
     * worker-minted works (`mk:ol:`, `mk:local:`) stay searchable even
     * without the map artifact.
     *
     * Ranking: exact normalized > title-prefix > word match, then shortest
     * title, then newest year.
     */
    suspend fun searchWorks(
        query: String,
        types: Set<String>,
        limit: Int = 60,
    ): List<WorkRow> {
        val norm = TitleNormalizer.normalize(query)
        if (norm.isBlank() || types.isEmpty()) return emptyList()
        val prefix = "$norm%"
        val word = "% $norm%"

        val delta = handler.awaitList {
            works_cacheQueries.searchByNormalized(prefix, word, limit.toLong())
        }
            .filter { it.type in types }
            .map { it.toWorkRow() to titleRank(it.normalized_title, norm) }

        val map = fullDb()?.let { db ->
            val placeholders = types.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT w.*, MIN(CASE WHEN t.normalized = ? THEN 0 " +
                    "WHEN t.normalized LIKE ? THEN 1 ELSE 2 END) AS r " +
                    "FROM titles t JOIN works w ON w.miko_work_id = t.miko_work_id " +
                    "WHERE (t.normalized LIKE ? OR t.normalized LIKE ?) " +
                    "AND w.type IN ($placeholders) " +
                    "GROUP BY w.miko_work_id " +
                    "ORDER BY r, LENGTH(w.title), w.year DESC LIMIT ?",
                (listOf(norm, prefix, prefix, word) + types + limit.toString()).toTypedArray(),
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val r = c.getColumnIndex("r")
                            .takeIf { it >= 0 }?.let { c.getInt(it) } ?: 2
                        add(c.toWorkRow(db) to r)
                    }
                }
            }
        }.orEmpty()

        val merged = LinkedHashMap<String, Pair<WorkRow, Int>>()
        delta.forEach { (row, r) -> merged[row.mikoWorkId] = row to r }
        map.forEach { (row, r) -> merged.putIfAbsent(row.mikoWorkId, row to r) }
        return hydrateRemote(
            dedupe(
                merged.values
                    .sortedWith(compareBy({ it.second }, { it.first.title.length }))
                    .map { it.first },
            ),
        )
            .take(limit)
    }

    @Serializable
    private data class HydrateDto(
        val miko_work_id: String,
        val author: String? = null,
        val cover_url: String? = null,
        val year: Long? = null,
        val total_chapters: String? = null,
        val genres: List<String> = emptyList(),
    )

    @Serializable
    private data class HydrateResponse(
        val works: List<HydrateDto> = emptyList(),
    )

    /**
     * The offline map artifact has no covers/authors — ask the worker
     * for display fields per winning id and merge them where the local
     * row is blank. Best-effort: offline or HTTP failure returns the
     * local rows untouched.
     */
    private suspend fun hydrateRemote(rows: List<WorkRow>): List<WorkRow> {
        if (rows.isEmpty()) return rows
        return try {
            val url = "${identityApi.baseUrl()}/v1/works/hydrate".toHttpUrl().newBuilder()
                .addQueryParameter("ids", rows.joinToString(",") { it.mikoWorkId })
                .build()
            val response = networkHelper.client.newCall(
                GET(url, headers = Headers.headersOf("User-Agent", WorkIdentityApi.USER_AGENT)),
            ).awaitSuccess()
            val hydrated = with(json) { response.parseAs<HydrateResponse>() }.works
                .associateBy { it.miko_work_id }
            rows.map { row ->
                val h = hydrated[row.mikoWorkId] ?: return@map row
                row.copy(
                    coverUrl = row.coverUrl ?: h.cover_url,
                    author = row.author ?: h.author,
                    year = row.year ?: h.year,
                    totalChapters = row.totalChapters ?: h.total_chapters,
                    genres = row.genres ?: h.genres.takeIf { it.isNotEmpty() },
                )
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN) { "search hydrate failed: ${e.message}" }
            rows
        }
    }

    /**
     * Collapse catalog duplicate rows — the dump carries several sparse
     * rows per title (variants, re-ingested editions) that share a
     * normalized title. Search shows one card per normalized title: the
     * row carrying the most identity evidence (a cover beats rank,
     * provider links beat bare title) so the card renders art and taps
     * materialize the well-linked work.
     */
    private fun dedupe(rows: List<WorkRow>): List<WorkRow> {
        val groups = LinkedHashMap<String, MutableList<WorkRow>>()
        for (row in rows) {
            groups.getOrPut(TitleNormalizer.normalize(row.title)) { mutableListOf() }
                .add(row)
        }
        return groups.values.map { group ->
            if (group.size == 1) return@map group.first()
            // The winner must be the bindable row — provider links and
            // external ids ARE the work identity, they can't be borrowed
            // from a sibling's miko_work_id. Display fields can: sparse
            // dump variants often carry the only cover/description, so a
            // well-linked-but-bare winner still renders art.
            val best = group.maxWithOrNull(
                compareBy<WorkRow> { it.providerLinks.isNotBlank() && it.providerLinks != "[]" }
                    .thenBy { it.externalIds.isNotEmpty() }
                    .thenBy { it.richness() },
            )!!
            best.copy(
                coverUrl = best.coverUrl ?: group.firstNotNullOfOrNull { it.coverUrl },
                author = best.author ?: group.firstNotNullOfOrNull { it.author },
                year = best.year ?: group.firstNotNullOfOrNull { it.year },
                totalChapters = best.totalChapters ?: group.firstNotNullOfOrNull { it.totalChapters },
                description = best.description ?: group.firstNotNullOfOrNull { it.description },
            )
        }
    }

    private fun WorkRow.richness(): Int = listOf(
        coverUrl != null,
        providerLinks != "[]",
        author != null,
        year != null,
        totalChapters != null,
    ).count { it }

    suspend fun externalIdsFor(workId: String): Map<String, String> =
        handler.awaitList { works_cacheQueries.selectExternalIdsByWork(workId) }
            .associate { it.provider to it.external_id }
            .ifEmpty {
                fullDb()?.let { db ->
                    db.rawQuery(
                        "SELECT provider, external_id FROM source_ids WHERE miko_work_id = ?",
                        arrayOf(workId),
                    ).use { c ->
                        buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
                    }
                }.orEmpty()
            }

    // ---------- writes (the delta) ----------

    suspend fun upsert(row: WorkRow, externalIds: Map<String, String> = row.externalIds) {
        handler.await(true) {
            works_cacheQueries.upsertWork(
                workId = row.mikoWorkId,
                type = row.type,
                title = row.title,
                romanizedTitle = row.romanizedTitle,
                nativeTitle = row.nativeTitle,
                year = row.year,
                totalChapters = row.totalChapters,
                author = row.author,
                providerLinks = row.providerLinks,
                normalizedTitle = TitleNormalizer.normalize(row.title),
                resolutionSource = row.resolutionSource,
                resolvedAt = System.currentTimeMillis(),
                nsfw = if (row.nsfw) 1L else 0L,
                coverUrl = row.coverUrl,
            )
            externalIds.forEach { (provider, id) ->
                works_cacheQueries.upsertExternalId(provider, id, row.mikoWorkId)
            }
        }
    }

    suspend fun count(): Long = handler.awaitOne { works_cacheQueries.count() }

    // ---------- row mapping ----------

    private fun data.Works_cache.toWorkRow(
        externalIds: Map<String, String> = emptyMap(),
    ) = WorkRow(
        mikoWorkId = miko_work_id,
        type = type,
        title = title,
        romanizedTitle = romanized_title,
        nativeTitle = native_title,
        year = year,
        totalChapters = total_chapters,
        author = author,
        nsfw = nsfw != 0L,
        providerLinks = provider_links,
        resolutionSource = resolution_source,
        coverUrl = cover_url,
        externalIds = externalIds,
    )

    private fun android.database.Cursor.toWorkRow(db: SQLiteDatabase): WorkRow {
        fun s(name: String) = getColumnIndex(name).takeIf { it >= 0 }
            ?.let { if (isNull(it)) null else getString(it) }
        fun l(name: String) = getColumnIndex(name).takeIf { it >= 0 }
            ?.let { if (isNull(it)) null else getLong(it) }
        val workId = s("miko_work_id").orEmpty()
        val ext = mutableMapOf<String, String>()
        db.rawQuery(
            "SELECT provider, external_id FROM source_ids WHERE miko_work_id = ?",
            arrayOf(workId),
        ).use { e -> while (e.moveToNext()) ext[e.getString(0)] = e.getString(1) }
        val items = try {
            db.rawQuery(
                "SELECT provider, external_id, url FROM provider_links WHERE miko_work_id = ?",
                arrayOf(workId),
            ).use { e ->
                buildList {
                    while (e.moveToNext()) {
                        add(
                            buildString {
                                append("{\"provider\":\"").append(e.getString(0).escapeJson())
                                append("\",\"external_id\":\"").append(e.getString(1)?.escapeJson().orEmpty())
                                append("\",\"url\":\"").append(e.getString(2).escapeJson()).append("\"}")
                            },
                        )
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        return WorkRow(
            mikoWorkId = workId,
            type = s("type").orEmpty(),
            title = s("title").orEmpty(),
            romanizedTitle = s("romanized_title"),
            nativeTitle = s("native_title"),
            year = l("year"),
            totalChapters = s("total_chapters"),
            author = s("author"),
            status = s("status"),
            // Older map artifacts have no nsfw column → treat as false.
            nsfw = (l("nsfw") ?: 0L) != 0L,
            providerLinks = "[${items.joinToString(",")}]",
            resolutionSource = "full_db",
            coverUrl = s("cover_url"),
            externalIds = ext,
        )
    }

    private fun String.escapeJson() = replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        /** 0 = exact normalized match, 1 = title-prefix, 2 = word-boundary hit. */
        internal fun titleRank(normalized: String, query: String) = when {
            normalized == query -> 0
            normalized.startsWith(query) -> 1
            else -> 2
        }
    }
}
