package miko.ui.download

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** Seeded from composition — filesDir is the downloads tree root on Android. */
private var filesRootPath: String? = null

@Composable
actual fun downloadsInitHook() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        filesRootPath = context.filesDir.absolutePath
    }
}

actual fun downloadsRoot(): String =
    filesRootPath ?: error("downloadsRoot accessed before DownloadsInit ran")
