package eu.kanade.tachiyomi.data.mikodb

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Read-only bridge over the MikoDB anime mapping artifact
 * (miko-anime-map.db — see .planning/mikodb-anime-identity-design.md).
 *
 * Maps Cinemeta IMDB identities onto canonical anime IDs (AniList/MAL/Kitsu/…)
 * and resolves (season, episode) coordinates onto extension episode numbering.
 */
class AnimeIdBridge private constructor(
    private val db: SQLiteDatabase,
) {

    data class AnimeMapping(
        val malId: Long?,
        val malDubId: Long?,
        val anilistId: Long?,
        val kitsuId: Long?,
        val anidbId: Long?,
        val simklId: Long?,
        val tvdbId: Long?,
        val tmdbId: Long?,
        val imdbId: String?,
        val traktId: Long?,
        val title: String?,
        val tvdbSeason: String?,
        val tvdbPart: Int?,
        val duration: Int?,
        val score: Double?,
        val status: String?,
        val animeMediaType: String?,
        val animeMediaEpisodes: String?,
        val globalMediaEpisodes: String?,
        val dubStatus: String?,
    )

    /**
     * One `anime_episodes` row (19-01 artifact build, ani.zip-sourced —
     * D-01/D-02). `type` keeps the artifact vocabulary verbatim
     * (`main|special|nc|trailer|parody`); [epKey] is the AniDB doc key
     * ("1", "S3", "C2") so specials/NC keep their identity. [absNumber]
     * is the ani.zip absolute number verbatim — null on ~half of
     * upstream rows; [season]/[epInSeason] are the TVDB coordinates.
     */
    data class AnimeEpisode(
        val anidbId: Long,
        val epKey: String,
        val type: String,
        val number: Double?,
        val season: Int?,
        val epInSeason: Int?,
        val absNumber: Int?,
        val title: String?,
        val overview: String?,
        val image: String?,
        val airDate: String?,
        val anidbAirDate: String?,
        val runtime: Int?,
        val rating: Double?,
        val anidbEid: Long?,
        val tvdbEid: Long?,
        val tvdbShowId: Long?,
        val filler: Boolean,
    ) {
        /** ISO `airDate` ("1998-11-13") as epoch ms for `date_upload`
         *  semantics; null when the row carries no parseable date. */
        fun airDateAsEpochMs(): Long? = airDate?.let {
            runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .apply { isLenient = false }
                    .parse(it)?.time
            }.getOrNull()
        }
    }

    private fun rowToMapping(cursor: android.database.Cursor): AnimeMapping {
        fun c(name: String) = cursor.getColumnIndex(name)
        fun str(
            name: String,
        ) = c(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getString(it) }
        fun long(name: String) = c(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getLong(it) }
        fun dbl(
            name: String,
        ) = c(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
        return AnimeMapping(
            malId = long("mal_id"),
            malDubId = long("mal_dub_id"),
            anilistId = long("anilist_id"),
            kitsuId = long("kitsu_id"),
            anidbId = long("anidb_id"),
            simklId = long("simkl_id"),
            tvdbId = long("thetvdb_id"),
            tmdbId = long("themoviedb_id"),
            imdbId = str("imdb_id"),
            traktId = long("trakt_id"),
            title = str("mal_title"),
            tvdbSeason = str("thetvdb_season"),
            tvdbPart = long("thetvdb_part")?.toInt(),
            duration = long("duration")?.toInt(),
            score = dbl("score"),
            status = str("status"),
            animeMediaType = str("anime_media_type"),
            animeMediaEpisodes = str("anime_media_episodes"),
            globalMediaEpisodes = str("global_media_episodes"),
            dubStatus = str("dub_status"),
        )
    }

    private fun rowToEpisode(cursor: android.database.Cursor): AnimeEpisode {
        fun c(name: String) = cursor.getColumnIndex(name)
        fun str(
            name: String,
        ) = c(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getString(it) }
        fun long(name: String) = c(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getLong(it) }
        fun dbl(
            name: String,
        ) = c(name).takeIf { it >= 0 }?.let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
        return AnimeEpisode(
            anidbId = long("anidb_id") ?: 0L,
            epKey = str("ep_key").orEmpty(),
            type = str("type").orEmpty(),
            number = dbl("number"),
            season = long("season")?.toInt(),
            epInSeason = long("ep_in_season")?.toInt(),
            absNumber = long("abs_number")?.toInt(),
            title = str("title"),
            overview = str("overview"),
            image = str("image"),
            airDate = str("air_date"),
            anidbAirDate = str("anidb_air_date"),
            runtime = long("runtime")?.toInt(),
            rating = dbl("rating"),
            anidbEid = long("anidb_eid"),
            tvdbEid = long("tvdb_eid"),
            tvdbShowId = long("tvdb_show_id"),
            filler = (long("filler") ?: 0L) != 0L,
        )
    }

    private fun query(where: String, arg: String): List<AnimeMapping> =
        db.rawQuery("SELECT * FROM anime WHERE $where = ?", arrayOf(arg)).use { c ->
            buildList { while (c.moveToNext()) add(rowToMapping(c)) }
        }

    private fun queryLong(where: String, arg: Long): List<AnimeMapping> =
        db.rawQuery("SELECT * FROM anime WHERE $where = ?", arrayOf(arg.toString())).use { c ->
            buildList { while (c.moveToNext()) add(rowToMapping(c)) }
        }

    /** All rows sharing an IMDB id (series + specials/OVAs). Filter by mediaType/season before use. */
    fun byImdb(imdbId: String): List<AnimeMapping> = query("imdb_id", imdbId)

    fun byAnilist(anilistId: Long): AnimeMapping? = queryLong("anilist_id", anilistId).firstOrNull()

    fun byMal(malId: Long): AnimeMapping? = queryLong("mal_id", malId).firstOrNull()

    fun byKitsu(kitsuId: Long): AnimeMapping? = queryLong("kitsu_id", kitsuId).firstOrNull()

    /** The "main" entry for an IMDB id — the TV or MOVIE row, not specials/OVAs. */
    fun mainEntry(imdbId: String): AnimeMapping? =
        byImdb(imdbId).firstOrNull { it.animeMediaType == "TV" || it.animeMediaType == "MOVIE" }
            ?: byImdb(imdbId).firstOrNull()

    /**
     * Find the main entry by canonical title — exact case-insensitive match
     * first, then substring containment in either direction. Used to map
     * extension-source entries (which key on opaque local ids) onto a
     * canonical identity. Main entries (TV/MOVIE) preferred.
     */
    fun byTitle(title: String): AnimeMapping? {
        val exact = db.rawQuery(
            "SELECT * FROM anime WHERE mal_title = ? COLLATE NOCASE",
            arrayOf(title),
        ).use { c -> buildList { while (c.moveToNext()) add(rowToMapping(c)) } }
        val rows = exact.ifEmpty {
            val escaped = title.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            db.rawQuery(
                "SELECT * FROM anime WHERE mal_title LIKE ? ESCAPE '\\' COLLATE NOCASE OR ? LIKE '%' || mal_title || '%' ESCAPE '\\' COLLATE NOCASE",
                arrayOf("%$escaped%", title),
            ).use { c -> buildList { while (c.moveToNext()) add(rowToMapping(c)) } }
        }
        return rows.firstOrNull { it.animeMediaType == "TV" || it.animeMediaType == "MOVIE" }
            ?: rows.firstOrNull()
    }

    /**
     * Resolve a Cinemeta (season, episode) onto concrete episode coordinates.
     */
    fun bindEpisode(imdbId: String, season: Int, episode: Int): EpisodeMapper.EpisodeBinding? {
        val rows = byImdb(imdbId).map {
            EpisodeMapper.MappingRow(
                malId = it.malId,
                anilistId = it.anilistId,
                animeMediaType = it.animeMediaType,
                tvdbSeason = it.tvdbSeason,
                tvdbPart = it.tvdbPart,
                animeMediaEpisodes = it.animeMediaEpisodes,
                globalMediaEpisodes = it.globalMediaEpisodes,
                title = it.title,
            )
        }
        return EpisodeMapper.bind(rows, season, episode)
    }

    /** Older artifacts predate the 19-01 build — probe once, then every
     *  episode read degrades to an empty list rather than crashing. */
    private val hasEpisodesTable: Boolean by lazy {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='anime_episodes'",
            null,
        ).use { it.moveToFirst() }
    }

    /** Every episode row one AniDB doc contributes — mains, specials,
     *  NC, trailers/parodies, in stored order. */
    fun episodesForAnidb(anidbId: Long): List<AnimeEpisode> {
        if (!hasEpisodesTable) return emptyList()
        return db.rawQuery(
            "SELECT * FROM anime_episodes WHERE anidb_id = ? ORDER BY ep_key",
            arrayOf(anidbId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(rowToEpisode(c)) } }
    }

    /**
     * All episode rows across EVERY anime doc an imdb resolves to — a
     * work may span multiple AniDB docs (series + movies/OVAs), so the
     * artifact feed courier carries the full chain with `anidb:` doc
     * namespaces keeping same-coordinate specials distinct.
     */
    fun episodesForImdb(imdbId: String): List<AnimeEpisode> =
        byImdb(imdbId)
            .mapNotNull { it.anidbId }
            .distinct()
            .flatMap { episodesForAnidb(it) }

    fun close() = db.close()

    companion object {
        fun open(file: File): AnimeIdBridge? = try {
            AnimeIdBridge(SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY))
        } catch (_: Exception) {
            null
        }
    }
}
