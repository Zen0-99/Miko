package eu.kanade.tachiyomi.metadata.tmdb

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.NetworkPreferences

/**
 * Resolves the TMDB API key for Discovery facet queries.
 *
 * Order: the user's BYOK preference (`pref_tmdb_api_key`, Settings →
 * Advanced → Network) wins when set; otherwise the build-time
 * `BuildConfig.TMDB_API_KEY` (empty by default — real keys are injected
 * via the `TMDB_API_KEY` gradle property/env at build time, never
 * committed). When neither resolves the caller gets a blank key and
 * [TmdbDiscover] falls back to its stub path (no request, unfiltered
 * catalog stays up) rather than firing a doomed unauthenticated call.
 */
class TmdbKeyProvider(
    private val networkPreferences: NetworkPreferences,
) {
    fun apiKey(): String =
        networkPreferences.tmdbApiKey().get().trim()
            .ifBlank { BuildConfig.TMDB_API_KEY.trim() }
}
