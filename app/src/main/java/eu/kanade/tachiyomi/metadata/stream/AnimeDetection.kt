package eu.kanade.tachiyomi.metadata.stream

/**
 * Determines whether a Cinemeta entry is anime, to decide whether
 * to search Aniyomi extensions in addition to Stremio addons.
 *
 * Detection criteria (both must be true):
 * - Country contains "Japan"
 * - Genres includes "Animation"
 *
 * This distinguishes:
 * - Naruto (Japan + Animation) → anime → search Aniyomi extensions + Stremio addons
 * - Rick and Morty (United States + Animation) → not anime → Stremio addons only
 * - Breaking Bad (United States + Crime/Drama/Thriller) → not anime → Stremio addons only
 *
 * Cinemeta does NOT have an "anime" genre — "Animation" includes western animation.
 * The country field is the key distinguisher.
 */
object AnimeDetection {

    fun isAnime(genres: List<String>?, country: String?): Boolean {
        if (country.isNullOrBlank()) return false
        if (!country.contains("Japan", ignoreCase = true)) return false
        if (genres.isNullOrEmpty()) return false
        return genres.any { it.equals("Animation", ignoreCase = true) }
    }
}
