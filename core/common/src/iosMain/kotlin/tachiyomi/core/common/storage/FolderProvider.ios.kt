package tachiyomi.core.common.storage

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

actual interface FolderProvider {

    actual fun path(): String
}

/**
 * iOS app-sandbox base directory (Documents — user-visible in Files,
 * matching Android's shared storage semantics).
 */
class IosFolderProvider : FolderProvider {
    override fun path(): String {
        return (
            NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true,
            ).firstOrNull()?.toString()
            ) ?: NSTemporaryDirectory()
    }
}
