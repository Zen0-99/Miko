package mihon.domain.extensionrepo.service

/**
 * Minimal transport port for repo metadata/index fetches, so the domain layer
 * stays free of an HTTP client dependency. Implemented per-platform (Ktor on
 * iOS, OkHttp on Android).
 */
interface RepoHttpFetcher {
    /** GET [url]; body text on a 2xx response, null on any failure. */
    suspend fun get(url: String): String?

    /** GET [url]; body bytes on a 2xx response, null on any failure. */
    suspend fun getBytes(url: String): ByteArray?
}
