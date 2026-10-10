package mihon.domain.extensionrepo.service

import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.system.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * iOS extension-repo transport. Stub until the iOS network/index path is
 * wired (Phase 28) — returns empty results so callers degrade to their
 * "invalid/empty repo" branches instead of crashing.
 */
class IosExtensionRepoService : ExtensionRepoService {

    override suspend fun fetchRepoDetails(repo: String): ExtensionRepo? {
        logcat(LogPriority.WARN) { "IosExtensionRepoService.fetchRepoDetails not implemented" }
        return null
    }

    override suspend fun fetchExtensionIndex(repo: String): List<ExtensionIndexEntry>? {
        logcat(LogPriority.WARN) { "IosExtensionRepoService.fetchExtensionIndex not implemented" }
        return null
    }

    override suspend fun probeRepoTypes(repo: String): Set<String> {
        logcat(LogPriority.WARN) { "IosExtensionRepoService.probeRepoTypes not implemented" }
        return emptySet()
    }
}
