package tachiyomi.data.extension.novel

import mihon.domain.extensionrepo.service.RepoHttpFetcher
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.api.JsPluginIndexFetcher

/**
 * Ktor port of the app's `NetworkNovelPluginIndexFetcher`: same candidate-path
 * order — `plugins.min.json` → `plugins.json` → `.dist/…` → `index.min.json`
 * → `index.json` — first successful response wins. A repo URL that already
 * points at a `.json` file is fetched directly.
 */
class KtorJsIndexFetcher(
    private val fetcher: RepoHttpFetcher,
) : JsPluginIndexFetcher {
    override suspend fun fetch(repoUrl: String): String {
        val baseUrl = repoUrl.trimEnd('/')
        val candidates = if (baseUrl.endsWith(".json", ignoreCase = true)) {
            listOf(baseUrl)
        } else {
            listOf(
                "$baseUrl/plugins.min.json",
                "$baseUrl/plugins.json",
                "$baseUrl/.dist/plugins.min.json",
                "$baseUrl/.dist/plugins.json",
                "$baseUrl/index.min.json",
                "$baseUrl/index.json",
            )
        }

        logcat(LogPriority.DEBUG) { "JsIndexFetcher: trying ${candidates.size} candidates for $baseUrl" }

        for (candidate in candidates) {
            val body = fetcher.get(candidate)
            if (body != null) {
                logcat(LogPriority.DEBUG) { "JsIndexFetcher: success from $candidate" }
                return body
            }
        }

        logcat(LogPriority.ERROR) { "JsIndexFetcher: all candidates failed for $baseUrl" }
        throw JsPluginIndexFetchException("No JS plugin index found at $baseUrl")
    }
}

class JsPluginIndexFetchException(
    message: String,
) : Exception(message)
