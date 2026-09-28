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

    fun close() = db.close()

    companion object {
        fun open(file: File): AnimeIdBridge? = try {
            AnimeIdBridge(SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY))
        } catch (_: Exception) {
            null
        }
    }
}
