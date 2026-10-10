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
 * iOS extension-repo transport. Only the JS plugin index is installable on
 * iOS — APK indexes (`index.pb`, `index.min.json` extension arrays) are never
 * consumed. `index.pb` is still parsed for repo *metadata* when `repo.json`
 * is absent, mirroring Android.
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
        val bytes = fetcher.getBytes("$repo/index.pb") ?: return null
        if (bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            logcat(LogPriority.WARN) { "index.pb for $repo is gzipped — unsupported on iOS" }
            return null
        }
        return try {
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

    // iOS has no APK consumer — the extensions list reads the JS plugin index
    // via JsPluginApi instead.
    override suspend fun fetchExtensionIndex(repo: String): List<ExtensionIndexEntry>? = null

    /**
     * Probes the JS plugin index and maps each entry's `kind` to a repo type
     * ("novel"/"manga"/"anime"). Kotlin-APK entries are ignored — nothing on
     * iOS can install them. Entries without `kind` normalize to "novel" at
     * parse, so a plain LNReader index probes {novel}.
     */
    override suspend fun probeRepoTypes(repo: String): Set<String> {
        return try {
            val payload = indexFetcher.fetch(repo)
            parser.parse(payload, repo)
                .filterNot { it.isKotlinExtension }
                .map { it.kind }
                .toSet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "probeRepoTypes failed for $repo" }
            emptySet()
        }
    }
}
