package eu.kanade.core.util

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ifNovelSourcesLoaded(): Boolean {
    val isInitialized = remember { Injekt.get<NovelSourceManager>().isInitialized }.collectAsState().value
    if (!isInitialized) {
        Log.w("NovelSearch", "[ifNovelSourcesLoaded] NovelSourceManager.isInitialized = false (novel sources not loaded yet!)")
    }
    return isInitialized
}
