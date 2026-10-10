package mihon.domain.extensionrepo.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
) {
    val client = networkHelper.client

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            // First try the legacy repo.json metadata file
            try {
                with(json) {
                    client.newCall(GET("$repo/repo.json"))
                        .awaitSuccess()
                        .parseAs<ExtensionRepoMetaDto>()
                        .toExtensionRepo(baseUrl = repo)
                }
            } catch (e: Exception) {
                // Fall back to the protobuf index.pb which contains the same metadata
                // fields (name, signingKey, contact.website) used by modern repos.
                fetchRepoDetailsFromProto(repo)
            }
        }
    }

    private suspend fun fetchRepoDetailsFromProto(repo: String): ExtensionRepo? {
        return try {
            val response = client.newCall(GET("$repo/index.pb")).awaitSuccess()
            val bytes = response.body.bytes()
            val decompressed = maybeGunzip(bytes)
            val index = ProtoBuf.decodeFromByteArray(IndexProto.serializer(), decompressed)
            ExtensionRepo(
                baseUrl = repo,
                name = index.name.ifBlank { repo },
                shortName = null,
                website = index.contact?.website ?: "",
                signingKeyFingerprint = index.signingKey,
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch repo details from $repo (repo.json and index.pb)" }
            null
        }
    }

    /**
     * Fetches the extension index from a repo. Tries the protobuf index.pb
     * format first (used by modern repos like keiyoushi), then falls back to
     * the legacy JSON index.min.json.
     *
     * Returns a list of normalized [ExtensionIndexEntry] regardless of format.
     */
    suspend fun fetchExtensionIndex(repo: String): List<ExtensionIndexEntry>? {
        return withIOContext {
            logcat(LogPriority.DEBUG) { "fetchExtensionIndex for repo: $repo" }
            // Try protobuf first
            val protoEntries = tryFetchProtoIndex(repo)
            if (protoEntries != null) {
                logcat(LogPriority.DEBUG) { "Using proto index for $repo with ${protoEntries.size} entries" }
                return@withIOContext protoEntries
            }

            // Fall back to JSON
            logcat(LogPriority.DEBUG) { "Proto failed, falling back to JSON for $repo" }
            tryFetchJsonIndex(repo)
        }
    }

    private suspend fun tryFetchProtoIndex(repo: String): List<ExtensionIndexEntry>? {
        return try {
            val response = client.newCall(GET("$repo/index.pb")).awaitSuccess()
            val bytes = response.body.bytes()
            logcat(LogPriority.DEBUG) {
                "Proto index from $repo: ${bytes.size} bytes, first bytes: ${bytes.take(2).map { b ->
                    String.format("%02X", b)
                }.joinToString(" ")}"
            }
            val decompressed = maybeGunzip(bytes)
            logcat(LogPriority.DEBUG) { "After gunzip: ${decompressed.size} bytes" }
            val index = ProtoBuf.decodeFromByteArray(IndexProto.serializer(), decompressed)
            val entries = index.toEntries(repo)
            logcat(LogPriority.DEBUG) { "Parsed ${entries.size} extensions from proto index" }
            entries
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch/parse proto index from $repo" }
            null
        }
    }

    /**
     * Detects gzip magic bytes (0x1F 0x8B) and decompresses if present.
     * Some repos (e.g. Keiyoushi) serve a gzip-compressed index.pb file
     * without a `Content-Encoding: gzip` header, so OkHttp won't auto-
     * decompress. We detect and handle it manually.
     */
    private fun maybeGunzip(bytes: ByteArray): ByteArray {
        if (bytes.size < 2 || (bytes[0] != 0x1F.toByte() || bytes[1] != 0x8B.toByte())) {
            return bytes
        }
        return java.util.zip.GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    }

    private suspend fun tryFetchJsonIndex(repo: String): List<ExtensionIndexEntry>? {
        return try {
            with(json) {
                val entries = client.newCall(GET("$repo/index.min.json"))
                    .awaitSuccess()
                    .parseAs<List<ExtensionRepoExtensionJsonDto>>()
                    .toEntries(repo)
                logcat(LogPriority.DEBUG) { "Parsed ${entries.size} extensions from JSON index at $repo" }
                entries
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch extension index from $repo" }
            null
        }
    }

    /**
     * Probes a repo URL and determines its content type based on the package
     * names of the extensions in its index. Returns a set containing one or
     * more of "anime", "manga", "novel". A single repo (e.g. Keiyoushi) can
     * serve multiple types simultaneously.
     *
     * Also checks for LNReader plugin repos (plugins.min.json / plugins.json)
     * and routes them to "novel" since LNReader plugins are novel plugins.
     */
    suspend fun probeRepoTypes(repo: String): Set<String> {
        // Normalize: strip known index file suffixes so probing works
        // regardless of whether the user pasted a bare repo URL or a
        // full path to an index file (e.g. .../v3.0.0/.dist/plugins.min.json).
        val baseUrl = repo.trim().trimEnd('/')
            .removeSuffix("/index.min.json")
            .removeSuffix("/index.pb")
            .removeSuffix("/index.pb.gz")
            .removeSuffix("/repo.json")
            .removeSuffix("/plugins.min.json")
            .removeSuffix("/plugins.json")
            .removeSuffix("/index.json")

        val types = mutableSetOf<String>()

        // LNReader plugin index (plugins.min.json/plugins.json) marks a
        // novel repo — but don't short-circuit: a combined repo like MikoDB
        // ships both a plugin index AND an extension index.pb, and must
        // register in every matching tab.
        if (hasLnReaderPluginIndex(baseUrl)) {
            types.add("novel")
        }

        val entries = fetchExtensionIndex(baseUrl)
        if (entries == null || entries.isEmpty()) {
            return if (types.isEmpty()) setOf("manga") else types
        }

        for (entry in entries) {
            val pkg = entry.pkgName.lowercase()
            val name = entry.name.lowercase()
            // A single extension can match multiple categories — check each
            // independently so repos serving multiple types (e.g. Keiyoushi
            // serving both manga and anime) are detected for all types.
            if (pkg.contains(".novel.") ||
                pkg.contains("yokai.extension.novel") ||
                pkg.contains("tachiyomi.novel") ||
                name.contains("novel")
            ) {
                types.add("novel")
            }
            if (pkg.contains(".anime.") ||
                pkg.contains("animeextension") ||
                pkg.contains("aniyomi") ||
                pkg.contains("jmiri.anime") ||
                name.contains("anime")
            ) {
                types.add("anime")
            }
            if (pkg.contains("tachiyomi.extension") ||
                pkg.contains("tachiyomi.manga") ||
                pkg.contains("keiyoushi")
            ) {
                types.add("manga")
            }
        }
        return if (types.isEmpty()) setOf("manga") else types
    }

    /**
     * Legacy single-type probe. Returns the primary type for a repo.
     * Kept for backward compatibility; prefer [probeRepoTypes] for repos
     * that may serve multiple content types.
     */
    suspend fun probeRepoType(repo: String): String {
        val types = probeRepoTypes(repo)
        return when {
            "novel" in types && types.size == 1 -> "novel"
            "anime" in types && "manga" in types -> "manga" // multi-type: prefer manga
            "anime" in types -> "anime"
            "novel" in types -> "novel"
            else -> "manga"
        }
    }

    /**
     * Checks whether the repo has an LNReader plugin index file
     * (plugins.min.json or plugins.json, including inside a .dist directory).
     * Returns true if found.
     */
    private suspend fun hasLnReaderPluginIndex(repo: String): Boolean {
        val baseUrl = repo.trim().trimEnd('/')
        val candidates = listOf(
            "$baseUrl/plugins.min.json",
            "$baseUrl/plugins.json",
            "$baseUrl/.dist/plugins.min.json",
            "$baseUrl/.dist/plugins.json",
        )
        return withIOContext {
            for (candidate in candidates) {
                try {
                    client.newCall(GET(candidate)).awaitSuccess()
                    return@withIOContext true
                } catch (_: Exception) {
                    // try next candidate
                }
            }
            false
        }
    }
}
