package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object HomeHubTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            return TabOptions(
                index = 0u,
                title = stringResource(AYMR.strings.label_home),
                icon = rememberVectorPainter(Icons.Outlined.Home),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel { HomeHubScreenModel() }
        val state by screenModel.state.collectAsState()

        HomeHubContent(state = state)

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

@Composable
private fun HomeHubContent(state: HomeHubState) {
    if (state.isEmpty) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(AYMR.strings.information_no_home_history),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.recentAnime.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(AYMR.strings.label_recent_anime))
            }
            item {
                HistoryRow(items = state.recentAnime.map { it.title })
            }
        }

        if (state.recentManga.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(MR.strings.label_recent_manga))
            }
            item {
                HistoryRow(items = state.recentManga.map { it.title })
            }
        }

        if (state.recentNovels.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(AYMR.strings.label_recent_novels))
            }
            item {
                HistoryRow(items = state.recentNovels.map { it.title })
            }
        }

        if (state.recentlyAddedAnime.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(AYMR.strings.label_recently_added_anime))
            }
            item {
                HistoryRow(items = state.recentlyAddedAnime.map { it.title })
            }
        }

        if (state.recentlyAddedManga.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(AYMR.strings.label_recently_added_manga))
            }
            item {
                HistoryRow(items = state.recentlyAddedManga.map { it.title })
            }
        }

        if (state.recentlyAddedNovels.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(AYMR.strings.label_recently_added_novels))
            }
            item {
                HistoryRow(items = state.recentlyAddedNovels.map { it.title })
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun HistoryRow(items: List<String>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items) { title ->
            Card(
                modifier = Modifier
                    .size(width = 120.dp, height = 160.dp)
                    .clip(RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}
