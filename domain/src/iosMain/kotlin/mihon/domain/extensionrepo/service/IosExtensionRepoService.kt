package mihon.domain.extensionrepo.service

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.extension.novel.api.JsPluginIndexFetcher
import tachiyomi.domain.extension.novel.api.JsPluginIndexParser

/**
 * iOS extension-repo transport. APK entries can't be installed on iOS — the
 * installable set comes from the JS plugin index — but the APK index is still
 * consulted for repo *metadata* (index.pb name/signingKey/contact) and for
 * [probeRepoTypes], so a repo registers under its real carrier tabs exactly
 * like Android.
 */
class IosExtensionRepoService(
    private val fetcher: RepoHttpFetcher,
    private val indexFetcher: JsPluginIndexFetcher,
    private val parser: JsPluginIndexParser,
    private val json: Json,
) : ExtensionRepoService {

    override suspend fun fetchRepoDetails(repo: String): ExtensionRepo? {
        val body = fetcher.get("$repo/repo.json")
        if (body != null) {
            try {
                return json.decodeFromString<ExtensionRepoMetaDto>(body)
                    .toExtensionRepo(baseUrl = repo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "repo.json parse failed for $repo — trying index.pb" }
            }
        }
        return fetchRepoDetailsFromProto(repo)
    }

    /**
     * index.pb carries name/signingKey/contact.website — same fields Android
     * extracts. iOS has no gzip decompressor on the common path, so a
     * gzip-magic payload logs and returns null (the interactor's synthetic
     * fingerprint fallback then applies).
     */
    private suspend fun fetchRepoDetailsFromProto(repo: String): ExtensionRepo? {
        val index = fetchProtoIndex(repo) ?: return null
        return ExtensionRepo(
            baseUrl = repo,
            name = index.name.ifBlank { repo },
            shortName = null,
            website = index.contact?.website ?: "",
            signingKeyFingerprint = index.signingKey,
        )
    }

    /**
     * Same lookup order as Android: protobuf index.pb first, then legacy
     * index.min.json. iOS has no APK consumer — the entries feed
     * [probeRepoTypes] and any future metadata use.
     */
    override suspend fun fetchExtensionIndex(repo: String): List<ExtensionIndexEntry>? {
        val proto = fetchProtoIndex(repo)?.toEntries(repo)
        if (proto != null) return proto
        return try {
            val body = fetcher.get("$repo/index.min.json") ?: return null
            json.decodeFromString<List<ExtensionRepoExtensionJsonDto>>(body)
                .toEntries(repo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to fetch extension index from $repo" }
            null
        }
    }

    private suspend fun fetchProtoIndex(repo: String): IndexProto? {
        val bytes = fetcher.getBytes("$repo/index.pb") ?: return null
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            logcat(LogPriority.WARN) { "index.pb for $repo is gzipped — unsupported on iOS" }
            return null
        }
        return try {
            ProtoBuf.decodeFromByteArray(IndexProto.serializer(), bytes)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to parse index.pb from $repo" }
            null
        }
    }

    /**
     * Mirrors Android's probe: JS plugin index kinds first (richer than
     * Android's presence check — `kind` maps straight to carrier types),
     * then APK-index pkg/name heuristics so APK-only repos like MikoDB still
     * register under their real tabs. Empty result falls back to {manga}
     * exactly like Android.
     */
    override suspend fun probeRepoTypes(repo: String): Set<String> {
        val baseUrl = repo.trim().trimEnd('/')
            .removeSuffix("/index.min.json")
            .removeSuffix("/index.pb")
            .removeSuffix("/index.pb.gz")
            .removeSuffix("/repo.json")
            .removeSuffix("/plugins.min.json")
            .removeSuffix("/plugins.json")
            .removeSuffix("/index.json")

        val types = mutableSetOf<String>()

        val jsIndexExists = try {
            val jsPlugins = parser.parse(indexFetcher.fetch(baseUrl), baseUrl)
                .filterNot { it.isKotlinExtension }
            jsPlugins.mapTo(types) { it.kind }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.DEBUG) { "no JS plugin index at $baseUrl" }
            false
        }
        // Android's presence check: a bare LNReader index means novel repo.
        if (jsIndexExists && types.isEmpty()) types += "novel"

        val entries = fetchExtensionIndex(baseUrl)
        if (!entries.isNullOrEmpty()) {
            for (entry in entries) {
                types += entry.detectedRepoTypes()
            }
        }

        return if (types.isEmpty()) setOf("manga") else types
    }
}
