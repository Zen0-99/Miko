package mihon.domain.extensionrepo.service

import mihon.domain.extensionrepo.model.ExtensionRepo

/**
 * Fetches extension-repo metadata and indexes over HTTP. Platform
 * implementations live in androidMain/iosMain.
 */
interface ExtensionRepoService {

    suspend fun fetchRepoDetails(repo: String): ExtensionRepo?

    suspend fun fetchExtensionIndex(repo: String): List<ExtensionIndexEntry>?

    suspend fun probeRepoTypes(repo: String): Set<String>

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
}
