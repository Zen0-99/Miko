package tachiyomi.domain.extension.novel.api

/**
 * Fetches a repo's JS plugin index payload. Implementations probe the known
 * candidate paths and throw when none succeed.
 */
interface JsPluginIndexFetcher {
    suspend fun fetch(repoUrl: String): String
}
