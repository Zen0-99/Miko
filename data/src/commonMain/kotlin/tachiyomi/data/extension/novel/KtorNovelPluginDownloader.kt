package tachiyomi.data.extension.novel

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/**
 * Ktor [NovelPluginDownloader] — shared across platforms; iOS uses it with the
 * Darwin client from `platformHttpClient()`.
 */
class KtorNovelPluginDownloader(
    private val client: HttpClient,
) : NovelPluginDownloader {
    override suspend fun download(url: String): ByteArray {
        val response = try {
            client.get(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw NovelPluginInstallException.DownloadFailed(url, e.message ?: "network error", e)
        }
        if (!response.status.isSuccess()) {
            throw NovelPluginInstallException.DownloadFailed(url, "HTTP ${response.status.value}")
        }
        return response.readBytes()
    }

    private suspend fun HttpResponse.readBytes(): ByteArray = body()
}
