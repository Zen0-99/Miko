package eu.kanade.tachiyomi.extension.novel.api

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import okhttp3.OkHttpClient
import tachiyomi.core.common.util.system.logcat

class NetworkNovelPluginIndexFetcher(
    private val client: OkHttpClient,
) : NovelPluginIndexFetcher {
    override suspend fun fetch(repoUrl: String): String {
        return withContext(Dispatchers.IO) {
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

            logcat(LogPriority.DEBUG) { "NovelPluginIndexFetcher: trying ${candidates.size} candidates for $baseUrl" }
            var lastError: Exception? = null

            for (candidate in candidates) {
                try {
                    client.newCall(GET(candidate)).awaitSuccess().use { response ->
                        logcat(LogPriority.DEBUG) { "NovelPluginIndexFetcher: success from $candidate" }
                        return@withContext response.body.string()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logcat(LogPriority.DEBUG) { "NovelPluginIndexFetcher: failed $candidate — ${error.message}" }
                    lastError = error
                }
            }

            logcat(LogPriority.ERROR) { "NovelPluginIndexFetcher: all candidates failed for $baseUrl" }
            throw checkNotNull(lastError)
        }
    }
}
