package eu.kanade.tachiyomi.data.mikodb

import eu.kanade.tachiyomi.data.comments.ChapterBinder
import logcat.logcat
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Canonical episode hydration (19-04, D-02/D-03) — an overlay on the
 * [LoadCinemetaEpisodes] materialization, never a replacement. Once a
 * shows work canonizes, each canonical row's metadata is written into
 * the already-existing local `episodes` row it binds to: name, air
 * date, overview, still, the `"Season N"` scanlator carrier,
 * `episode_type`, and `canonical_filler`.
 *
 * Locked invariant: user-owned state is never part of an update —
 * `seen`, `bookmark`, playback progress and the user `fillermark`
 * survive verbatim. Canonical filler lands in its own column because
 * `fillermark` is a non-nullable Boolean and a user toggle is
 * indistinguishable from the default; the UI reads
 * `fillermark || canonicalFiller`.
 *
 * Rows canon can't bind and fully-identical updates are skipped, so
 * the pass never deletes or blanks rows and doesn't churn
 * `last_modified_at`.
 */
class LoadCanonicalEpisodes(
    private val episodeRepository: EpisodeRepository = Injekt.get(),
    private val episodeMap: CanonicalEpisodeMap = Injekt.get(),
) {

    /**
     * Hydrate [episodes] (the anime's existing local rows) from
     * [canon]. Returns the number of rows updated. Empty canon is an
     * immediate no-op (backstop: nothing to overlay is not an error —
     * Cinemeta rows stand).
     */
    suspend fun apply(
        anime: Anime,
        episodes: List<Episode>,
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
    ): Int {
        if (canon.isEmpty() || episodes.isEmpty()) return 0
        val updates = episodes.mapNotNull { ep -> updateFor(ep, canon) }
        if (updates.isEmpty()) return 0
        episodeRepository.updateAllEpisodes(updates)
        logcat {
            "canonicalHydration ${anime.title}: ${updates.size}/${episodes.size} episodes hydrated"
        }
        return updates.size
    }

    /** Build the canonical-metadata-only update for one local row, or
     *  null when no canon row binds or every field already matches. */
    private fun updateFor(
        ep: Episode,
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
    ): EpisodeUpdate? {
        val c = bindCanon(ep, canon) ?: return null

        val name = c.label.takeIf { it.isNotBlank() && it != ep.name }
        val dateUpload = c.dateUpload?.takeIf { it != ep.dateUpload }
        val summary = c.overview?.takeIf { it.isNotBlank() && it != ep.summary }
        val previewUrl = c.image?.takeIf { it.isNotBlank() && it != ep.previewUrl }
        val episodeType = kindToType(c.kind).takeIf { it != ep.episodeType }
        // Canonical data, not user state — written on every matched
        // row; `fillermark` itself is never in this update.
        val canonicalFiller = c.filler.takeIf { it != ep.canonicalFiller }
        val scanlator = canonicalScanlator(c.season, ep.scanlator)
            ?.takeIf { it != ep.scanlator }

        if (listOf(name, dateUpload, summary, previewUrl, episodeType, canonicalFiller, scanlator)
                .all { it == null }
        ) {
            return null
        }
        return EpisodeUpdate(
            id = ep.id,
            name = name,
            dateUpload = dateUpload,
            summary = summary,
            previewUrl = previewUrl,
            scanlator = scanlator,
            episodeType = episodeType,
            canonicalFiller = canonicalFiller,
        )
    }

    /**
     * Match a local row to its canonical row. Season-tagged rows
     * ("Season N" scanlator — every Cinemeta video) bind strictly on
     * (season, ep_in_season): the absolute fallback must not fire for
     * them, because a season-2 local numbered 5 is canon abs ~29, not
     * 5 — and a season-0 special must never land in the main keyspace.
     * Seasonless rows (movies, flat lists) take [CanonicalEpisodeMap.bind]'s
     * (1, ep) → abs path, which is the right semantics for absolute
     * numbering.
     */
    private fun bindCanon(
        ep: Episode,
        canon: List<CanonicalEpisodeMap.CanonEpisode>,
    ): CanonicalEpisodeMap.CanonEpisode? {
        val season = ChapterBinder.parseSeason(ep.scanlator)
        return if (season != null) {
            canon.firstOrNull { it.season == season && it.epInSeason?.toDouble() == ep.episodeNumber }
        } else {
            episodeMap.bind(1, ep.episodeNumber, canon)
        }
    }

    /** `"Season <canon>"` + the existing `|★…` rating suffix, or null
     *  when neither canon nor the row carries a season. */
    private fun canonicalScanlator(canonSeason: Int?, scanlator: String?): String? {
        val season = canonSeason ?: ChapterBinder.parseSeason(scanlator) ?: return null
        val suffix = scanlator
            ?.substringAfter('|', "")
            ?.let { if (it.isBlank()) "" else "|$it" }
            .orEmpty()
        return "Season $season$suffix"
    }

    /** Worker kind vocabulary → the `episodes.episode_type` column. */
    private fun kindToType(kind: String): String = when (kind) {
        "episode" -> "main"
        "nc" -> "nc"
        else -> "special"
    }
}
