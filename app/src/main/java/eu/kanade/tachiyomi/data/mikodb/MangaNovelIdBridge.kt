package eu.kanade.tachiyomi.data.mikodb

import android.database.sqlite.SQLiteDatabase
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
    ) {
        /** Kind vocabulary alias for [type] — same column, new name. */
        val kind: String get() = type
    }

    private val handler: MangaDatabaseHandler by injectLazy()

    @Volatile private var mapDb: SQLiteDatabase? = null

    @Volatile private var mapDbChecked = false

    private fun fullDb(): SQLiteDatabase? {
        if (mapDbChecked) return mapDb
        synchronized(this) {
            if (!mapDbChecked) {
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
                mapDbChecked = true
            }
            return mapDb
        }
    }

    /** Close + forget the full-db handle (called when the artifact is swapped). */
    fun invalidateFullDb() {
        synchronized(this) {
            mapDb?.close()
            mapDb = null
            mapDbChecked = false
        }
    }

    // ---------- reads ----------

    suspend fun byWorkId(workId: String): WorkRow? {
        handler.awaitOneOrNull { works_cacheQueries.selectByWorkId(workId) }
            ?.let { row ->
                return row.toWorkRow(externalIdsFor(workId))
            }
        return fullDb()?.let { db ->
            db.rawQuery("SELECT * FROM works WHERE miko_work_id = ?", arrayOf(workId))
                .use { c -> if (c.moveToNext()) c.toWorkRow(db) else null }
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
        return merged.values
            .sortedWith(compareBy({ it.second }, { it.first.title.length }))
            .take(limit)
            .map { it.first }
    }

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
