package eu.kanade.tachiyomi.ui.reader.novel

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import eu.kanade.presentation.util.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.asNovelCover
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NovelHighlightsScreen(
    private val novelTitle: String,
    private val novelId: Long,
) : Screen() {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val highlightManager = remember { NovelHighlightManager(context) }
        val novelKey = remember { NovelHighlightManager.NovelKey(title = novelTitle, novelId = novelId) }

        var highlightsData by remember { mutableStateOf<NovelHighlightManager.NovelHighlightsData?>(null) }
        var showActionsFor by remember { mutableStateOf<Pair<NovelHighlightManager.HighlightEntry, Double>?>(null) }
        var coverData by remember { mutableStateOf<tachiyomi.domain.entries.novel.model.NovelCover?>(null) }

        LaunchedEffect(novelKey) {
            withContext(Dispatchers.IO) {
                highlightsData = highlightManager.getAllHighlights(novelKey)
                // Load novel cover for background
                val novel = Injekt.get<GetNovel>().await(novelId)
                if (novel != null) {
                    coverData = novel.asNovelCover()
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(novelTitle) },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
        ) { paddingValues ->
            // Full-height background — extends behind the transparent top bar
            Box(modifier = Modifier.fillMaxSize()) {
                if (coverData != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverData)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .blur(10.dp)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                    )
                    // Darker gradient overlay — darker at top for app bar, fading to solid bg
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.6f),
                                    0.15f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    0.4f to MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                    0.7f to MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                                    1f to MaterialTheme.colorScheme.background,
                                ),
                            ),
                    )
                }

                // Content with padding applied here so background is full-height
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                    val data = highlightsData
                    if (data == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Loading...")
                        }
                        return@Box
                    }

                    val items = buildHighlightItems(data)

                    if (items.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No highlights yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Highlight text while reading to see it here",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                        return@Box
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        items(items) { item ->
                            when (item) {
                                is HighlightListItem.ChapterHeader -> {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                                    )
                                }
                                is HighlightListItem.Highlight -> {
                                    HighlightCard(
                                        entry = item.entry,
                                        chapterNumber = item.chapterNumber,
                                        onClick = { showActionsFor = item.entry to item.chapterNumber },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        showActionsFor?.let { (entry, chapterNumber) ->
            HighlightActionsBottomSheet(
                entry = entry,
                onDismiss = { showActionsFor = null },
                onCopy = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Highlight", entry.text))
                    showActionsFor = null
                },
                onShare = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "\"${entry.text}\"\n— From: $novelTitle")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Highlight"))
                    showActionsFor = null
                },
                onDelete = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            highlightManager.deleteHighlight(novelKey, chapterNumber, entry.text, entry.timestamp)
                        }
                        highlightsData = withContext(Dispatchers.IO) {
                            highlightManager.getAllHighlights(novelKey)
                        }
                    }
                    showActionsFor = null
                },
                onNavigateToHighlight = {
                    scope.launch {
                        val chapters = withContext(Dispatchers.IO) {
                            Injekt.get<GetNovelChaptersByNovelId>().await(novelId)
                        }
                        val chapter = chapters.find { it.chapterNumber == chapterNumber }
                        if (chapter != null) {
                            showActionsFor = null
                            navigator.push(NovelReaderScreen(novelId, chapter.id))
                        }
                    }
                },
            )
        }
    }

    @Composable
    private fun HighlightCard(
        entry: NovelHighlightManager.HighlightEntry,
        chapterNumber: Double,
        onClick: () -> Unit,
    ) {
        val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
        val color = remember(entry.color) {
            try {
                Color(android.graphics.Color.parseColor(entry.color ?: NovelHighlightManager.COLOR_YELLOW))
            } catch (_: Exception) {
                Color.Yellow
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "\"${entry.text}\"",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = dateFormat.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!entry.note.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = entry.note,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HighlightActionsBottomSheet(
        entry: NovelHighlightManager.HighlightEntry,
        onDismiss: () -> Unit,
        onCopy: () -> Unit,
        onShare: () -> Unit,
        onDelete: () -> Unit,
        onNavigateToHighlight: () -> Unit,
    ) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            ) {
                // Highlight text preview
                Text(
                    text = "\"${entry.text}\"",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // Navigate to highlight
                TextButton(
                    onClick = onNavigateToHighlight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Go to highlight")
                }

                // Copy
                TextButton(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Copy text")
                }

                // Share
                TextButton(
                    onClick = onShare,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Share")
                }

                // Delete
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Delete")
                }
            }
        }
    }

    private fun buildHighlightItems(data: NovelHighlightManager.NovelHighlightsData): List<HighlightListItem> {
        val items = mutableListOf<HighlightListItem>()
        val sortedChapters = data.chapters.filter { it.highlights.isNotEmpty() }.sortedBy { it.chapterNumber }
        for (ch in sortedChapters) {
            items.add(HighlightListItem.ChapterHeader(ch.chapterTitle.ifBlank { "Chapter ${ch.chapterNumber}" }))
            for (hl in ch.highlights.sortedBy { it.timestamp }) {
                items.add(HighlightListItem.Highlight(hl, ch.chapterNumber))
            }
        }
        return items
    }

    private sealed class HighlightListItem {
        data class ChapterHeader(val title: String) : HighlightListItem()
        data class Highlight(
            val entry: NovelHighlightManager.HighlightEntry,
            val chapterNumber: Double,
        ) : HighlightListItem()
    }
}
