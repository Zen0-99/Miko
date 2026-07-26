package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.novelDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val screenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
    val downloadListRaw by screenModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf<String?>(null) }
    val downloadList by remember(downloadListRaw, searchQuery) {
        derivedStateOf {
            val q = searchQuery
            if (q.isNullOrBlank()) downloadListRaw
            else downloadListRaw.filter { it.name.contains(q, ignoreCase = true) }
        }
    }
    val downloadCount by remember {
        derivedStateOf { downloadList.sumOf { it.subItems.size } }
    }

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchAvailable = true,
        searchQuery = searchQuery,
        onSearchQueryChange = { searchQuery = it },
        searchPlaceholderText = MR.strings.search_hint_downloads,
        content = { contentPadding, _ ->
            NovelDownloadQueueScreen(
                contentPadding = contentPadding,
                scope = scope,
                screenModel = screenModel,
                downloadList = downloadList,
                nestedScrollConnection = nestedScrollConnection,
            )
        },
        numberTitle = downloadCount,
        navigateUp = navigator::pop,
    )
}
