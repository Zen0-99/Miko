package eu.kanade.tachiyomi.data.mikodb

/**
 * Pure episode-coordinate math for MikoDB anime mappings.
 *
 * The DB gives every entry two episode ranges:
 *  - animeMediaEpisodes: range in the entry's OWN numbering (e.g. AoT S3P2 = 1-10)
 *  - globalMediaEpisodes: range within the TVDB season (e.g. AoT S3P2 = 13-22)
 *
 * Cinemeta/TMDB season-episode numbers equal the TVDB-season absolute number,
 * so `globalMediaEpisodes` is the direct join key; `animeMediaEpisodes` gives the
 * number in the split AniList/MAL entry for sites that key on those.
 */
object EpisodeMapper {

    data class MappingRow(
        val malId: Long?,
        val anilistId: Long?,
        val animeMediaType: String?,
        val tvdbSeason: String?, // "a" = absolute ordering, "0" = specials, "1".."n" = season
        val tvdbPart: Int?,
        val animeMediaEpisodes: String?,
        val globalMediaEpisodes: String?,
        val title: String? = null,
    )

    data class EpisodeBinding(
        /** The anime row the episode belongs to (has anilist/mal ids for split-entry sites). */
        val row: MappingRow,
        /** Episode number inside the entry's own numbering (anime_media space). */
        val entryEpisode: Int,
        /** Episode number in season-absolute space (global space). */
        val seasonEpisode: Int,
        /** Episode number in whole-series absolute space (all seasons cumulative). */
        val absoluteEpisode: Int,
    )

    data class Range(val start: Int, val end: Int) {
        fun contains(v: Int) = v in start..end
        val size get() = end - start + 1
    }

    fun parseRange(s: String?): Range? {
        if (s.isNullOrBlank()) return null
        val parts = s.split('-').map { it.trim().toIntOrNull() ?: return null }
        return when (parts.size) {
            1 -> Range(parts[0], parts[0])
            2 -> Range(parts[0], parts[1])
            else -> null
        }
    }

    /**
     * Maps a Cinemeta (season, episode) pair onto the DB rows for an IMDB id.
     *
     * @param rows   all rows for the IMDB id (any media type)
     * @param season Cinemeta/TMDB season number (0 = specials)
     * @param episode episode number within that season
     */
    fun bind(rows: List<MappingRow>, season: Int, episode: Int): EpisodeBinding? {
        val seasonRows = rows
            .filter { it.tvdbSeason == season.toString() }
            .sortedBy { it.tvdbPart ?: 1 }
        if (seasonRows.isEmpty()) {
            // Single-absolute shows (e.g. One Piece, Naruto) use tvdbSeason "a"
            // with global range covering the whole run.
            val absoluteRows = rows.filter { it.tvdbSeason == "a" }
            val main = absoluteRows.firstOrNull { it.animeMediaType == "TV" } ?: absoluteRows.firstOrNull()
            val g = main?.let { parseRange(it.globalMediaEpisodes) } ?: return null
            if (season == 1 && g.contains(episode)) {
                val a = parseRange(main.animeMediaEpisodes)
                return EpisodeBinding(
                    row = main,
                    entryEpisode = (a?.start ?: 1) + episode - g.start,
                    seasonEpisode = episode,
                    absoluteEpisode = episode,
                )
            }
            return null
        }

        // Find the row whose global range contains the requested episode.
        val hit = seasonRows.firstOrNull { parseRange(it.globalMediaEpisodes)?.contains(episode) == true }
            ?: return null
        val g = parseRange(hit.globalMediaEpisodes)!!
        val a = parseRange(hit.animeMediaEpisodes)
        val entryEp = (a?.start ?: (g.start)) + (episode - g.start)

        // Whole-series absolute = sum of prior seasons' sizes + this episode's
        // position inside its season. Season sizes come from max global end per season.
        val seasonSizes = rows
            .filter { it.animeMediaType == "TV" || it.tvdbSeason?.toIntOrNull() != null }
            .mapNotNull { r ->
                r.tvdbSeason?.toIntOrNull()?.let { s -> s to (parseRange(r.globalMediaEpisodes)?.end ?: 0) }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.max() }
        val priorTotal = seasonSizes.filterKeys { it in 1 until season }.values.sum()
        val absolute = priorTotal + episode

        return EpisodeBinding(hit, entryEp, episode, absolute)
    }
}
