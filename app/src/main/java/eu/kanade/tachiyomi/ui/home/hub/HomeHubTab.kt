package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

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

        HomeHubContent(state = state, screenModel = screenModel)

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

@Composable
private fun HomeHubContent(
    state: HomeHubState,
    screenModel: HomeHubScreenModel,
) {
    val listState = rememberLazyListState()

    // --- Scroll-based header collapse ---
    // As the user scrolls down, the greeting header collapses (fades/shrinks).
    // The collapse fraction goes from 0 (fully expanded) to 1 (fully collapsed).
    val headerCollapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / 300f).coerceIn(0f, 1f)
            }
        }
    }

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
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- Greeting + Stats Header (collapsible) ---
        item {
            HomeHubGreetingHeader(
                greeting = stringResource(state.greeting),
                userName = state.userName,
                currentStreak = state.currentStreak,
                monthStats = state.monthStats,
                librarySize = state.librarySize,
                achievementCount = state.achievementCount,
                achievementTotal = state.achievementTotal,
                collapseFraction = headerCollapseFraction,
            )
        }

        // --- Hero Card ---
        state.hero?.let { hero ->
            item {
                HomeHubHeroCard(hero = hero)
            }
        }

        // --- Existing: Recent sections ---
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

// --- Greeting Header ---

@Composable
private fun HomeHubGreetingHeader(
    greeting: String,
    userName: String,
    currentStreak: Int,
    monthStats: tachiyomi.domain.achievement.model.MonthStats?,
    librarySize: Int,
    achievementCount: Int,
    achievementTotal: Int,
    collapseFraction: Float = 0f,
) {
    val displayName = userName.ifBlank { stringResource(AYMR.strings.home_user_default_name) }

    // Collapse: fade out stats and streak as user scrolls; shrink greeting
    val statsAlpha = 1f - collapseFraction
    val greetingFontSize = 20f + (16f - 20f) * collapseFraction

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Greeting (always visible, shrinks on scroll)
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontSize = greetingFontSize.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (collapseFraction < 0.5f) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Streak counter (fades on scroll)
        if (currentStreak > 0) {
            Spacer(Modifier.height(8.dp))
            StreakCounter(streak = currentStreak, alpha = statsAlpha)
        }

        // Stats row (fades on scroll)
        if (collapseFraction < 0.9f) {
            Spacer(Modifier.height(12.dp))
            StatsRow(
                monthStats = monthStats,
                librarySize = librarySize,
                achievementCount = achievementCount,
                achievementTotal = achievementTotal,
                alpha = statsAlpha,
            )
        }
    }
}

@Composable
private fun StreakCounter(streak: Int, alpha: Float = 1f) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha),
        modifier = Modifier.clip(RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(AYMR.strings.home_streak_days, streak),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = alpha),
            )
        }
    }
}

@Composable
private fun StatsRow(
    monthStats: tachiyomi.domain.achievement.model.MonthStats?,
    librarySize: Int,
    achievementCount: Int,
    achievementTotal: Int,
    alpha: Float = 1f,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (monthStats != null) {
            item { StatChip(stringResource(AYMR.strings.home_stats_episodes), monthStats.episodesWatched.toString()) }
            item { StatChip(stringResource(AYMR.strings.home_stats_chapters), monthStats.chaptersRead.toString()) }
        }
        item { StatChip(stringResource(AYMR.strings.home_stats_library), librarySize.toString()) }
        item {
            StatChip(
                stringResource(AYMR.strings.home_stats_achievements),
                "$achievementCount/$achievementTotal",
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Card(
        modifier = Modifier
            .size(width = 80.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Hero Card ---

@Composable
private fun HomeHubHeroCard(hero: HomeHubHero) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    val playerPreferences: PlayerPreferences by injectLazy()

    val progressLabel = when (hero.mediaType) {
        HomeHubMediaType.ANIME -> stringResource(AYMR.strings.home_hero_episode_progress, hero.progressNumber.toFloat())
        HomeHubMediaType.MANGA, HomeHubMediaType.NOVEL -> stringResource(AYMR.strings.home_hero_chapter_progress, hero.progressNumber.toFloat())
    }

    val sectionTitle = when (hero.mediaType) {
        HomeHubMediaType.ANIME -> stringResource(AYMR.strings.home_hero_continue_watching)
        HomeHubMediaType.MANGA, HomeHubMediaType.NOVEL -> stringResource(AYMR.strings.home_hero_continue_reading)
    }

    val ctaLabel = if (hero.progressNumber > 0.0) {
        stringResource(MR.strings.action_resume)
    } else {
        stringResource(MR.strings.action_start)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Cover image as background
            if (hero.coverData != null) {
                AsyncImage(
                    model = hero.coverData,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Gradient overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f),
                            ),
                        ),
                    ),
            )

            // Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = hero.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = progressLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(12.dp))

                // CTA Button
                Button(
                    onClick = {
                        when (hero.mediaType) {
                            HomeHubMediaType.ANIME -> {
                                val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
                                scope.launch {
                                    MainActivity.startPlayerActivity(
                                        context = context,
                                        animeId = hero.entryId,
                                        episodeId = hero.subId,
                                        extPlayer = extPlayer,
                                    )
                                }
                            }
                            HomeHubMediaType.MANGA -> {
                                context.startActivity(
                                    ReaderActivity.newIntent(context, hero.entryId, hero.subId),
                                )
                            }
                            HomeHubMediaType.NOVEL -> {
                                navigator.push(NovelReaderScreen(hero.entryId, hero.subId))
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(text = ctaLabel)
                }
            }
        }
    }
}

// --- Existing helpers (preserved) ---

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
