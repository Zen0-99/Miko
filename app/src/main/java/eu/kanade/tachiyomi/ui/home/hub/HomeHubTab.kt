package eu.kanade.tachiyomi.ui.home.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.globalOverflowActions
import eu.kanade.presentation.components.useSharedTopBar
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import cafe.adriel.voyager.navigator.tab.TabOptions
import coil3.compose.AsyncImage
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.tachiyomi.R
import eu.kanade.presentation.components.AppBar
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
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.injectLazy

data object HomeHubTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_home_enter)
            return TabOptions(
                index = 0u,
                title = stringResource(AYMR.strings.label_home),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { HomeHubScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        HomeHubContent(state = state, screenModel = screenModel, navigator = navigator)

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
    navigator: cafe.adriel.voyager.navigator.Navigator,
) {
    val listState = rememberLazyListState()
    val tabNavigator = LocalTabNavigator.current

    // Register with shared top bar
    useSharedTopBar(
        title = stringResource(AYMR.strings.label_home),
        actions = globalOverflowActions(onClickSettings = { navigator.push(SettingsScreen()) }),
    )

    if (state.isEmpty) {
        Scaffold(
            topBar = {},
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
        }
        return
    }

    Scaffold(
        topBar = {},
    ) { padding ->
        val hostBottomPadding = eu.kanade.presentation.components.LocalHostScaffoldContentPadding.current
            ?.calculateBottomPadding() ?: 0.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + hostBottomPadding + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // --- Hero Card ---
            item(key = "hero") {
                state.hero?.let { hero ->
                    HomeHubHeroCard(hero = hero)
                } ?: HomeHubHeroPlaceholder()
            }

            // --- Recommendations section ---
            if (state.recommendations.isNotEmpty()) {
                item(key = "recommendations_header") {
                    SectionHeader(
                        title = stringResource(AYMR.strings.home_recommendations),
                        topPadding = 32.dp,
                    )
                }
                item(key = "recommendations_row") {
                    HistoryRow(items = state.recommendations, onItemClick = { })
                }
            }

            // --- Mode-aware section filtering ---
            when (state.currentMode) {
                ContentMode.ANIME -> {
                    if (state.recentAnime.isNotEmpty()) {
                        item(key = "recent_anime_header") {
                            SectionHeader(
                                title = stringResource(AYMR.strings.label_recent_anime),
                                onViewAll = { tabNavigator.current = HistoriesTab },
                            )
                        }
                        item(key = "recent_anime_row") {
                            HistoryRow(
                                items = state.recentAnimeCards,
                                onItemClick = { },
                                onLongClick = { screenModel.deleteHistoryItem(it) },
                            )
                        }
                    }
                    if (state.recentlyAddedAnime.isNotEmpty()) {
                        item(key = "recently_added_anime_header") {
                            SectionHeader(
                                title = stringResource(AYMR.strings.label_recently_added_anime),
                                onViewAll = { tabNavigator.current = AnimeLibraryTab },
                            )
                        }
                        item(key = "recently_added_anime_row") {
                            HistoryRow(
                                items = state.recentlyAddedAnimeCards,
                                onItemClick = { },
                                onLongClick = { screenModel.removeRecentlyAddedItem(it) },
                            )
                        }
                    }
                }
                ContentMode.MANGA -> {
                    if (state.recentManga.isNotEmpty()) {
                        item(key = "recent_manga_header") {
                            SectionHeader(
                                title = stringResource(MR.strings.label_recent_manga),
                                onViewAll = { tabNavigator.current = HistoriesTab },
                            )
                        }
                        item(key = "recent_manga_row") {
                            HistoryRow(
                                items = state.recentMangaCards,
                                onItemClick = { },
                                onLongClick = { screenModel.deleteHistoryItem(it) },
                            )
                        }
                    }
                    if (state.recentlyAddedManga.isNotEmpty()) {
                        item(key = "recently_added_manga_header") {
                            SectionHeader(
                                title = stringResource(AYMR.strings.label_recently_added_manga),
                                onViewAll = { tabNavigator.current = MangaLibraryTab },
                            )
                        }
                        item(key = "recently_added_manga_row") {
                            HistoryRow(
                                items = state.recentlyAddedMangaCards,
                                onItemClick = { },
                                onLongClick = { screenModel.removeRecentlyAddedItem(it) },
                            )
                        }
                    }
                }
                ContentMode.NOVEL -> {
                    if (state.recentNovels.isNotEmpty()) {
                        item(key = "recent_novels_header") {
                            SectionHeader(
                                title = stringResource(AYMR.strings.label_recent_novels),
                                onViewAll = { tabNavigator.current = HistoriesTab },
                            )
                        }
                        item(key = "recent_novels_row") {
                            HistoryRow(
                                items = state.recentNovelCards,
                                onItemClick = { },
                                onLongClick = { screenModel.deleteHistoryItem(it) },
                            )
                        }
                    }
                    if (state.recentlyAddedNovels.isNotEmpty()) {
                        item(key = "recently_added_novels_header") {
                            SectionHeader(
                                title = stringResource(AYMR.strings.label_recently_added_novels),
                                onViewAll = { tabNavigator.current = NovelLibraryTab },
                            )
                        }
                        item(key = "recently_added_novels_row") {
                            HistoryRow(
                                items = state.recentlyAddedNovelCards,
                                onItemClick = { },
                                onLongClick = { screenModel.removeRecentlyAddedItem(it) },
                            )
                        }
                    }
                }
            }
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

    val heroCardShape = remember { RoundedCornerShape(20.dp) }
    val heroTextShadow = remember {
        Shadow(
            color = Color.Black.copy(alpha = 0.86f),
            offset = androidx.compose.ui.geometry.Offset(0f, 2.5f),
            blurRadius = 10f,
        )
    }

    // Overlay gradient
    val overlayGradient = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.40f to Color.Transparent,
                0.72f to Color.Black.copy(alpha = 0.12f),
                0.88f to Color.Black.copy(alpha = 0.38f),
                1.00f to Color.Black.copy(alpha = 0.58f),
            ),
        )
    }

    // Readability scrim
    val readabilityScrim = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.58f to Color.Transparent,
                0.78f to Color.Black.copy(alpha = 0.46f),
                1.00f to Color.Black.copy(alpha = 0.88f),
            ),
        )
    }

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

        // Content at bottom — title + chapter on left, resume button on right
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: title (top) + chapter/episode number (below)
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = hero.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp,
                    style = TextStyle(shadow = heroTextShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(shadow = heroTextShadow),
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // Right: resume button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .height(48.dp)
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
                        start = 20.dp,
                        end = 22.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ctaLabel,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHubHeroPlaceholder() {
    val tabNavigator = LocalTabNavigator.current
    val placeholderShape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clip(placeholderShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, placeholderShape)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(AYMR.strings.home_welcome),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(AYMR.strings.home_welcome_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = { tabNavigator.current = BrowseTab },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(AYMR.strings.home_browse_sources),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
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

// --- History Row (full-height cover art cards) ---

@Composable
private fun HistoryRow(
    items: List<HomeHubCardItem>,
    onItemClick: (HomeHubCardItem) -> Unit,
    onLongClick: ((HomeHubCardItem) -> Unit)? = null,
) {
    val cardShape = remember { RoundedCornerShape(16.dp) }
    val gradientScrim = remember {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.35f to Color.Black.copy(alpha = 0.35f),
                1.00f to Color.Black.copy(alpha = 0.82f),
            ),
        )
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }, contentType = { "home_hub_card" }) { item ->
            var showRemoveOverlay by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(200.dp)
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .combinedClickable(
                        onClick = {
                            if (showRemoveOverlay) {
                                showRemoveOverlay = false
                            } else {
                                onItemClick(item)
                            }
                        },
                        onLongClick = {
                            if (onLongClick != null) {
                                showRemoveOverlay = true
                            }
                        },
                    ),
            ) {
                // Full-height cover image
                AsyncImage(
                    model = item.coverData,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                // Bottom gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.BottomCenter)
                        .background(gradientScrim),
                )

                // Title + subtitle overlaid at bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                    )
                    if (!item.progressText.isNullOrEmpty()) {
                        Text(
                            text = item.progressText,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Long-press remove overlay
                if (showRemoveOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { onLongClick?.invoke(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Remove",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

