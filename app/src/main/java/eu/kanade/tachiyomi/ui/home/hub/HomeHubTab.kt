package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryTab
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

// --- Main Content ---

@Composable
private fun HomeHubContent(
    state: HomeHubState,
    screenModel: HomeHubScreenModel,
) {
    val listState = rememberLazyListState()
    val tabNavigator = LocalTabNavigator.current

    // --- Scroll-based header collapse ---
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
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(AYMR.strings.home_welcome),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(AYMR.strings.home_welcome_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { tabNavigator.current = BrowseTab }) {
                Text(stringResource(AYMR.strings.home_browse_sources))
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
        item {
            state.hero?.let { hero ->
                HomeHubHeroCard(hero = hero)
            } ?: HomeHubHeroPlaceholder()
        }

        // --- Recommendations section ---
        if (state.recommendations.isNotEmpty()) {
            item {
                SectionHeader(
                    title = stringResource(AYMR.strings.home_recommendations),
                    topPadding = 32.dp,
                )
            }
            item {
                HistoryRow(items = state.recommendations, onItemClick = { })
            }
        }

        // --- Mode-aware section filtering ---
        when (state.currentMode) {
            ContentMode.ANIME -> {
                if (state.recentAnime.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(AYMR.strings.label_recent_anime),
                            onViewAll = { tabNavigator.current = HistoriesTab },
                        )
                    }
                    item {
                        HistoryRow(items = state.recentAnimeCards, onItemClick = { })
                    }
                }
                if (state.recentlyAddedAnime.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(AYMR.strings.label_recently_added_anime),
                            onViewAll = { tabNavigator.current = AnimeLibraryTab },
                        )
                    }
                    item {
                        HistoryRow(items = state.recentlyAddedAnimeCards, onItemClick = { })
                    }
                }
            }
            ContentMode.MANGA -> {
                if (state.recentManga.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(MR.strings.label_recent_manga),
                            onViewAll = { tabNavigator.current = HistoriesTab },
                        )
                    }
                    item {
                        HistoryRow(items = state.recentMangaCards, onItemClick = { })
                    }
                }
                if (state.recentlyAddedManga.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(AYMR.strings.label_recently_added_manga),
                            onViewAll = { tabNavigator.current = MangaLibraryTab },
                        )
                    }
                    item {
                        HistoryRow(items = state.recentlyAddedMangaCards, onItemClick = { })
                    }
                }
            }
            ContentMode.NOVEL -> {
                if (state.recentNovels.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(AYMR.strings.label_recent_novels),
                            onViewAll = { tabNavigator.current = HistoriesTab },
                        )
                    }
                    item {
                        HistoryRow(items = state.recentNovelCards, onItemClick = { })
                    }
                }
                if (state.recentlyAddedNovels.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = stringResource(AYMR.strings.label_recently_added_novels),
                            onViewAll = { tabNavigator.current = NovelLibraryTab },
                        )
                    }
                    item {
                        HistoryRow(items = state.recentlyAddedNovelCards, onItemClick = { })
                    }
                }
            }
        }

        // Bottom spacer
        item {
            Spacer(Modifier.height(24.dp))
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
    val statsAlpha = 1f - collapseFraction

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Greeting (12sp, Medium, 60% alpha)
        Text(
            text = greeting,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f * statsAlpha.coerceIn(0f, 1f)),
        )

        // Nickname (24sp, Black weight)
        Text(
            text = displayName,
            fontSize = (24f - (24f - 18f) * collapseFraction).sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Streak counter (pill badge)
        if (currentStreak > 0 && collapseFraction < 0.8f) {
            Spacer(Modifier.height(8.dp))
            StreakCounter(streak = currentStreak, alpha = statsAlpha)
        }

        // Stats row
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.28f * alpha))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f * alpha),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = streak.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
        )
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
            item { StatChip(stringResource(AYMR.strings.home_stats_episodes), monthStats.episodesWatched.toString(), alpha) }
            item { StatChip(stringResource(AYMR.strings.home_stats_chapters), monthStats.chaptersRead.toString(), alpha) }
        }
        item { StatChip(stringResource(AYMR.strings.home_stats_library), librarySize.toString(), alpha) }
        item {
            StatChip(
                stringResource(AYMR.strings.home_stats_achievements),
                "$achievementCount/$achievementTotal",
                alpha,
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, alpha: Float = 1f) {
    Card(
        modifier = Modifier
            .size(width = 80.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
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

    val ctaLabel = if (hero.progressNumber > 0.0) {
        stringResource(MR.strings.action_resume)
    } else {
        stringResource(MR.strings.action_start)
    }

    val heroCardShape = RoundedCornerShape(20.dp)
    val heroTextShadow = Shadow(
        color = Color.Black.copy(alpha = 0.86f),
        offset = androidx.compose.ui.geometry.Offset(0f, 2.5f),
        blurRadius = 10f,
    )

    // Overlay gradient
    val overlayGradient = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.Transparent,
            0.40f to Color.Transparent,
            0.72f to Color.Black.copy(alpha = 0.12f),
            0.88f to Color.Black.copy(alpha = 0.38f),
            1.00f to Color.Black.copy(alpha = 0.58f),
        ),
    )

    // Readability scrim
    val readabilityScrim = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.Transparent,
            0.58f to Color.Transparent,
            0.78f to Color.Black.copy(alpha = 0.46f),
            1.00f to Color.Black.copy(alpha = 0.88f),
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(heroCardShape)
            .clickable {
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
        // Cover image fills the card
        if (hero.coverData != null) {
            AsyncImage(
                model = hero.coverData,
                contentDescription = hero.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Overlay gradient
        Box(Modifier.fillMaxSize().background(overlayGradient))

        // Readability scrim
        Box(Modifier.fillMaxSize().background(readabilityScrim))

        // Content at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Hero title: 28sp, bold, white, centered, with shadow
            Text(
                text = hero.title,
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp,
                style = TextStyle(shadow = heroTextShadow),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(14.dp))

            // Progress label with accent dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = progressLabel,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    style = TextStyle(shadow = heroTextShadow),
                )
            }

            Spacer(Modifier.height(24.dp))

            // CTA button: pill-shaped, accent-colored
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .height(52.dp)
                    .clickable {
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
                Row(
                    modifier = Modifier.padding(
                        start = 22.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ctaLabel,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHubHeroPlaceholder() {
    val placeholderShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .padding(16.dp)
            .clip(placeholderShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, placeholderShape),
    )
}

// --- Section Header ---

@Composable
private fun SectionHeader(
    title: String,
    onViewAll: (() -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 0.dp)
                .padding(top = topPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (onViewAll != null) {
                Text(
                    text = stringResource(AYMR.strings.action_view_all),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onViewAll() },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// --- History Row (AuroraPoster style cards) ---

@Composable
private fun HistoryRow(items: List<HomeHubCardItem>, onItemClick: (HomeHubCardItem) -> Unit) {
    val cardShape = RoundedCornerShape(18.dp)
    val posterShape = RoundedCornerShape(16.dp)

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier
                    .width(128.dp)
                    .clip(cardShape)
                    .clickable { onItemClick(item) },
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    // Poster image: aspect ratio 0.9, RoundedCornerShape(16.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.9f)
                            .clip(posterShape)
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        AsyncImage(
                            model = item.coverData,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Text block: min height 58.dp
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp)
                            .padding(horizontal = 2.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Title: 14sp, SemiBold, lineHeight=17sp, maxLines=2
                        Text(
                            text = item.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp,
                        )

                        // Subtitle: 11sp, onSurfaceVariant, maxLines=1
                        if (!item.progressText.isNullOrEmpty()) {
                            Text(
                                text = item.progressText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
