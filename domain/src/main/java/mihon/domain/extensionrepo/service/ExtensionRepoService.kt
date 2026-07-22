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
            val index = ProtoBuf.decodeFromByteArray(IndexProto.serializer(), bytes)
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
            // Try protobuf first
            val protoEntries = tryFetchProtoIndex(repo)
            if (protoEntries != null) return@withIOContext protoEntries

            // Fall back to JSON
            tryFetchJsonIndex(repo)
        }
    }

    private suspend fun tryFetchProtoIndex(repo: String): List<ExtensionIndexEntry>? {
        return try {
            val response = client.newCall(GET("$repo/index.pb")).awaitSuccess()
            val bytes = response.body.bytes()
            val index = ProtoBuf.decodeFromByteArray(IndexProto.serializer(), bytes)
            index.toEntries(repo)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun tryFetchJsonIndex(repo: String): List<ExtensionIndexEntry>? {
        return try {
            with(json) {
                client.newCall(GET("$repo/index.min.json"))
                    .awaitSuccess()
                    .parseAs<List<ExtensionRepoExtensionJsonDto>>()
                    .toEntries(repo)
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch extension index from $repo" }
            null
        }
    }

    /**
     * Probes a repo URL and determines its content type based on the package
     * names of the extensions in its index. Returns one of "anime", "manga",
     * or "novel". Falls back to "manga" if the type cannot be determined.
     */
    suspend fun probeRepoType(repo: String): String {
        val entries = fetchExtensionIndex(repo) ?: return "manga"
        if (entries.isEmpty()) return "manga"

        var animeScore = 0
        var novelScore = 0
        var mangaScore = 0
        for (entry in entries) {
            val pkg = entry.pkgName.lowercase()
            val name = entry.name.lowercase()
            when {
                pkg.contains(".novel.") || pkg.contains("yokai.extension.novel") ||
                    pkg.contains("tachiyomi.novel") || name.contains("novel") -> novelScore++
                pkg.contains(".anime.") || pkg.contains("aniyomi") ||
                    pkg.contains("jmiri.anime") || name.contains("anime") -> animeScore++
                pkg.contains("tachiyomi.extension") || pkg.contains("tachiyomi.manga") ||
                    pkg.contains("keiyoushi") -> mangaScore++
            }
        }
        return when {
            novelScore > animeScore && novelScore > mangaScore -> "novel"
            animeScore > mangaScore && animeScore > novelScore -> "anime"
            else -> "manga"
        }
    }
}
