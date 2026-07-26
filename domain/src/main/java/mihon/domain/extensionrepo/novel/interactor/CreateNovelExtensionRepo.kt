package mihon.domain.extensionrepo.novel.interactor

import eu.kanade.tachiyomi.util.lang.Hash
import logcat.LogPriority
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.system.logcat

class CreateNovelExtensionRepo(
    private val repository: NovelExtensionRepoRepository,
    private val service: ExtensionRepoService,
) {
    private val repoRegex = """^https://.*$""".toRegex()

    suspend fun await(indexUrl: String): Result {
        val formattedIndexUrl = indexUrl.toHttpUrlOrNull()
            ?.toString()
            ?.takeIf { it.matches(repoRegex) }
            ?: return Result.InvalidUrl

        val baseUrl = formattedIndexUrl
            .trim()
            .trimEnd('/')
            .removeSuffix("/index.min.json")
            .removeSuffix("/index.pb")
            .removeSuffix("/index.pb.gz")
            .removeSuffix("/repo.json")
            .removeSuffix("/plugins.min.json")
            .removeSuffix("/plugins.json")
            .removeSuffix("/index.json")
        // Try to fetch repo metadata (repo.json or index.pb). If neither exists
        // (e.g. LNReader plugin repos that only have plugins.min.json), create
        // a minimal repo entry with a unique synthetic fingerprint so multiple
        // metadata-less repos can coexist without violating the UNIQUE
        // constraint on signing_key_fingerprint.
        val repo = service.fetchRepoDetails(baseUrl)
            ?: ExtensionRepo(
                baseUrl = baseUrl,
                name = deriveRepoName(baseUrl),
                shortName = null,
                website = "",
                signingKeyFingerprint = "NOFINGERPRINT-${Hash.sha256(baseUrl)}",
            )
        return insert(repo)
    }

    private suspend fun insert(repo: ExtensionRepo): Result {
        return try {
            repository.insertRepo(
                repo.baseUrl,
                repo.name,
                repo.shortName,
                repo.website,
                repo.signingKeyFingerprint,
            )
            Result.Success
        } catch (e: SaveExtensionRepoException) {
            logcat(LogPriority.WARN, e) { "SQL Conflict attempting to add new novel repository ${repo.baseUrl}" }
            return handleInsertionError(repo)
        }
    }

    private suspend fun handleInsertionError(repo: ExtensionRepo): Result {
        val repoExists = repository.getRepo(repo.baseUrl)
        if (repoExists != null) {
            return Result.RepoAlreadyExists
        }
        val matchingFingerprintRepo = repository.getRepoBySigningKeyFingerprint(repo.signingKeyFingerprint)
        if (matchingFingerprintRepo != null) {
            return Result.DuplicateFingerprint(matchingFingerprintRepo, repo)
        }
        return Result.Error
    }

    /**
     * Derives a human-readable repo name from the base URL.
     *
     * For GitHub raw URLs (e.g. `.../lnreader/lnreader-plugins/plugins/v3.0.0`),
     * extracts the repository name (`lnreader-plugins`) rather than the version
     * segment. Falls back to the last path segment, then the full URL.
     */
    private fun deriveRepoName(baseUrl: String): String {
        val segments = baseUrl.trimEnd('/').split("/").filter { it.isNotBlank() }
        if (segments.isEmpty()) return baseUrl

        // For raw.githubusercontent.com URLs: /user/repo/branch/...path
        // The repo name is the 3rd-to-last meaningful segment from the host.
        val hostIndex = segments.indexOfFirst { it.contains(".") }
        if (hostIndex >= 0 && hostIndex + 2 < segments.size) {
            // segments[hostIndex+1] = user, segments[hostIndex+2] = repo
            return segments[hostIndex + 2]
        }

        // Fallback: last segment, but skip version-like segments
        for (segment in segments.reversed()) {
            // Skip segments that look like version numbers (e.g. "v3.0.0", "1.0")
            if (!segment.matches(Regex("^v?\\d+(\\.\\d+)*.*$", RegexOption.IGNORE_CASE))) {
                return segment
            }
        }

        return segments.last()
    }

    sealed interface Result {
        data class DuplicateFingerprint(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : Result
        data object InvalidUrl : Result
        data object RepoAlreadyExists : Result
        data object Success : Result
        data object Error : Result
    }
}
