package mihon.data.extensionrepo

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import mihon.domain.extensionrepo.service.RepoHttpFetcher
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat

class KtorRepoHttpFetcher(
    private val client: HttpClient,
) : RepoHttpFetcher {
    override suspend fun get(url: String): String? {
        return try {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                response.bodyAsText()
            } else {
                logcat(LogPriority.DEBUG) { "RepoHttpFetcher: ${response.status.value} for $url" }
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "RepoHttpFetcher: failed $url — ${e.message}" }
            null
        }
    }

    override suspend fun getBytes(url: String): ByteArray? {
        return try {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                response.body<ByteArray>()
            } else {
                logcat(LogPriority.DEBUG) { "RepoHttpFetcher: ${response.status.value} for $url" }
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "RepoHttpFetcher: failed $url — ${e.message}" }
            null
        }
    }
}
