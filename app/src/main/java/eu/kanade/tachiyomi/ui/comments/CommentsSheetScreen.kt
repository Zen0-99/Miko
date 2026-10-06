package eu.kanade.tachiyomi.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.domain.ui.model.Carrier
import eu.kanade.presentation.comments.CommentSection
import eu.kanade.presentation.entries.components.accentTintedSurface
import eu.kanade.presentation.entries.components.rememberEntryAccentColor
import eu.kanade.tachiyomi.data.comments.ChapterThreadResolver
import eu.kanade.tachiyomi.data.comments.ThreadIdResolver
import eu.kanade.tachiyomi.util.EntryCoverMetadata
import tachiyomi.domain.entries.shows.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Sheet host for a comment thread. Resolves the canonical `threadId`
 * lazily — work-level when [chapter] is null, chapter/episode-level
 * otherwise — then mounts a [CommentsScreenModel] keyed by it.
 */
class CommentsSheetScreen(
    private val carrier: Carrier,
    private val entryId: Long,
    private val chapter: ChapterRef? = null,
    /**
     * Sheet hosts leave this false so the sheet wraps its content; the
     * player's docked side panel passes true so the composer anchors to the
     * bottom of the full-height panel.
     */
    private val fillHeight: Boolean = false,
) : Screen {

    /**
     * Chapter/episode-scope thread input. [allChapters] is the source's
     * full (name, number) list — used for the alignment fingerprint.
     * [season] is the Cinemeta "Season N" carrier for shows episodes
     * (0 = specials; null for flat-numbered episodes and manga/books).
     */
    data class ChapterRef(
        val name: String,
        val number: Float,
        val sourceKey: String,
        val season: Int? = null,
        val allChapters: List<Pair<String, Float>> = emptyList(),
    )

    @Composable
    override fun Content() {
        val resolver: ThreadIdResolver = Injekt.get()
        val chapterResolver: ChapterThreadResolver = Injekt.get()
        val chapter = this.chapter
        val resolved by produceState<ResolvedThread?>(initialValue = null, entryId, chapter) {
            value = resolveThread(resolver, chapterResolver, chapter) ?: ResolvedThread(UNRESOLVED, null)
        }

        // Re-derive the cover accent the detail view uses — the shared
        // EntryCoverMetadata cache means this is the same color the user saw
        // when they opened comments, including inside the video player.
        val accent = rememberEntryAccentColor(
            entryId = entryId,
            cover = resolved?.cover,
            type = when (carrier) {
                Carrier.SHOWS -> EntryCoverMetadata.EntryType.SHOWS
                Carrier.MANGA -> EntryCoverMetadata.EntryType.MANGA
                Carrier.BOOKS -> EntryCoverMetadata.EntryType.NOVEL
            },
        )
        // The sheet interior carries the ENTRY accent darkened +
        // desaturated into a surface — same recipe as the chapter cards.
        // Falls back to the app primary while the accent is unresolved.
        val sheetBackground = accentTintedSurface(
            accent = accent ?: MaterialTheme.colorScheme.primary,
            background = MaterialTheme.colorScheme.background,
            dimmed = false,
        )
        val scheme = if (accent != null) {
            MaterialTheme.colorScheme.copy(primary = accent)
        } else {
            MaterialTheme.colorScheme
        }

        MaterialTheme(colorScheme = scheme) {
            Box(
                modifier = Modifier
                    .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .background(sheetBackground),
            ) {
                CommentsContent(
                    threadId = resolved?.threadId,
                    fillHeight = fillHeight,
                    accentColor = accent,
                )
            }
        }
    }

    @Composable
    private fun CommentsContent(threadId: String?, fillHeight: Boolean, accentColor: androidx.compose.ui.graphics.Color?) {
        when {
            threadId == null -> Box(
                modifier = if (fillHeight) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            threadId == UNRESOLVED -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(MR.strings.comments_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Text(
                    text = stringResource(MR.strings.comments_unavailable_entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = SECONDARY_ALPHA),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            else -> {
                val screenModel = rememberScreenModel(tag = threadId) {
                    CommentsScreenModel(threadId)
                }
                val state by screenModel.state.collectAsState()
                val contentModifier = if (fillHeight) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(modifier = contentModifier) {
                    CommentSection(
                        state = state,
                        onPost = screenModel::post,
                        onSubmitName = screenModel::submitName,
                        onDismissNameDialog = screenModel::dismissNameDialog,
                        onVote = screenModel::vote,
                        onReply = screenModel::setReplyTarget,
                        onReport = screenModel::report,
                        onDelete = screenModel::delete,
                        onAppeal = screenModel::appeal,
                        onLoadMore = screenModel::loadMore,
                        modifier = contentModifier,
                        accentColor = accentColor,
                    )
                }
            }
        }
    }

    private data class ResolvedThread(val threadId: String?, val cover: Any?)

    private suspend fun resolveThread(
        resolver: ThreadIdResolver,
        chapterResolver: ChapterThreadResolver,
        chapter: ChapterRef?,
    ): ResolvedThread? {
        val (workThread, title, cover) = when (carrier) {
            Carrier.SHOWS -> Injekt.get<GetAnime>().await(entryId)
                ?.let { Triple(resolver.forShows(it), it.title, it) }
            Carrier.MANGA -> Injekt.get<GetManga>().await(entryId)
                ?.let { Triple(resolver.forManga(it), it.title, it) }
            Carrier.BOOKS -> Injekt.get<GetNovel>().await(entryId)
                ?.let { Triple(resolver.forNovel(it), it.title, it) }
        } ?: return null
        if (workThread == null || chapter == null) return ResolvedThread(workThread, cover)

        // Thread IDs are "<medium>:<workId>"; workIds themselves contain
        // colons (mk:mb:1), so strip only the first segment.
        val workId = workThread.substringAfter(':')
        val threadId = when (carrier) {
            Carrier.SHOWS -> chapterResolver.episode(
                workId = workId,
                season = chapter.season,
                episodeNumber = chapter.number,
                sourceKey = chapter.sourceKey,
                title = title,
                episodes = chapter.allChapters,
            )
            Carrier.MANGA -> chapterResolver.chapter(
                carrier = Carrier.MANGA,
                workId = workId,
                sourceKey = chapter.sourceKey,
                title = title,
                chapterName = chapter.name,
                chapterNumber = chapter.number,
                chapters = chapter.allChapters,
            )
            Carrier.BOOKS -> chapterResolver.chapter(
                carrier = Carrier.BOOKS,
                workId = workId,
                sourceKey = chapter.sourceKey,
                title = title,
                chapterName = chapter.name,
                chapterNumber = chapter.number,
                chapters = chapter.allChapters,
            )
        }
        return ResolvedThread(threadId, cover)
    }

    private companion object {
        const val UNRESOLVED = ""
    }
}
