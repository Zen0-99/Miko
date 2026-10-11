package miko.ui.download

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** App Documents dir — user-visible in Files, correct home for downloads. */
@OptIn(ExperimentalForeignApi::class)
actual fun downloadsRoot(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .firstOrNull()
        ?.toString()
        ?: error("NSDocumentDirectory unavailable")

/** No-op — iOS resolves Documents without a composition context. */
@androidx.compose.runtime.Composable
actual fun downloadsInitHook() = Unit
