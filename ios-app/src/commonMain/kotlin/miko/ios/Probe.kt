package miko.ios

import eu.kanade.tachiyomi.core.network.platformHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

suspend fun probeNetwork(url: String): String =
    platformHttpClient().get(url).bodyAsText()

/**
 * Callback bridge for Swift XCTest — suspend functions export awkwardly;
 * a (body?, error?) closure is the robust shape. Result delivered on the
 * default dispatcher; XCTest waits on its own expectation.
 */
@Suppress("unused")
fun probeNetworkAsync(url: String, callback: (String?, String?) -> Unit) {
    probeScope.launch {
        try {
            callback(probeNetwork(url), null)
        } catch (e: Exception) {
            callback(null, e.message ?: e::class.simpleName)
        }
    }
}
