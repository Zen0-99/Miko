package eu.kanade.tachiyomi.util.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.getSystemService
import java.net.UnknownHostException

val Context.connectivityManager: ConnectivityManager
    get() = getSystemService()!!

val Context.wifiManager: WifiManager
    get() = getSystemService()!!

fun Context.isOnline(): Boolean {
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    val maxTransport = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> NetworkCapabilities.TRANSPORT_LOWPAN
        else -> NetworkCapabilities.TRANSPORT_WIFI_AWARE
    }
    return (NetworkCapabilities.TRANSPORT_CELLULAR..maxTransport).any(
        networkCapabilities::hasTransport,
    )
}

/**
 * Returns true if device is connected to Wifi.
 */
fun Context.isConnectedToWifi(): Boolean {
    if (!wifiManager.isWifiEnabled) return false

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } else {
        @Suppress("DEPRECATION")
        wifiManager.connectionInfo.bssid != null
    }
}

/**
 * Detects if a [Throwable] is a DNS resolution failure (host blocked or
 * unreachable). Returns true for [UnknownHostException] and IOExceptions
 * whose message contains "Unable to resolve host".
 */
fun Throwable.isDnsBlocked(): Boolean {
    if (this is UnknownHostException) return true
    val msg = message?.lowercase() ?: return false
    return msg.contains("unable to resolve host") ||
        msg.contains("unable to resolve hostname") ||
        msg.contains("no address associated with hostname")
}

/**
 * Extracts the hostname from a URL string, or returns the full string
 * if it doesn't look like a URL.
 */
private fun String.extractHost(): String {
    return try {
        val afterScheme = substringAfter("://", this)
        val host = afterScheme.substringBefore("/")
        host.substringBefore(":")
    } catch (_: Exception) {
        this
    }
}

/**
 * Shows a toast warning the user that a DNS resolution failure occurred,
 * suggesting they check their VPN. Call this from catch blocks that handle
 * network errors (library update jobs, cover fetchers, detail page fetches).
 *
 * @param urlOrHost the URL or hostname that failed to resolve
 */
fun Context.showDnsBlockedToast(urlOrHost: String) {
    val host = urlOrHost.extractHost()
    android.util.Log.w("DnsBlocked", "DNS blocked for host=$host")
    android.widget.Toast.makeText(
        this,
        "DNS blocked for $host — check if VPN is interfering",
        android.widget.Toast.LENGTH_LONG,
    ).show()
}

/**
 * Checks if a [Throwable] is a DNS resolution failure and, if so, shows a
 * toast on the given [Context]. Returns true if the error was DNS-related
 * and a toast was shown.
 */
fun Context.maybeShowDnsToast(error: Throwable, urlOrHost: String? = null): Boolean {
    if (!error.isDnsBlocked()) return false
    val host = urlOrHost ?: error.message?.let { msg ->
        // Try to extract host from "Unable to resolve host \"xxx\": No address..."
        val match = Regex("Unable to resolve host \"([^\"]+)\"").find(msg)
        match?.groupValues?.getOrNull(1) ?: msg
    } ?: "unknown host"
    showDnsBlockedToast(host)
    return true
}
