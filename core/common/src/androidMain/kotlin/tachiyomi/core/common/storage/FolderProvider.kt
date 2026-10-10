package tachiyomi.core.common.storage

import java.io.File

actual interface FolderProvider {

    fun directory(): File

    actual fun path(): String
}
