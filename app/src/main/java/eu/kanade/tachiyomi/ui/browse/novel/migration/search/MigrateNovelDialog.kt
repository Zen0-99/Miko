package eu.kanade.tachiyomi.ui.browse.novel.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.entries.novel.model.hasCustomCover
import eu.kanade.domain.entries.novel.model.toSNovel
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.ui.browse.novel.migration.NovelMigrationFlags
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCollections
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelUpdate
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter
import tachiyomi.domain.items.chapter.model.toNovelChapterUpdate
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
internal fun MigrateNovelDialog(
    oldNovel: Novel,
    newNovel: Novel,
    screenModel: MigrateNovelDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val flags = remember { NovelMigrationFlags.getFlags(oldNovel, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    flags.forEachIndexed { index, flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.titleId),
                            checked = selectedFlags[index],
                            onCheckedChange = { selectedFlags[index] = it },
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.action_show_manga))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateNovel(
                                    oldNovel,
                                    newNovel,
                                    false,
                                    NovelMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )
                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateNovel(
                                    oldNovel,
                                    newNovel,
                                    true,
                                    NovelMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )

                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            },
        )
    }
}

internal class MigrateNovelDialogScreenModel(
    private val sourceManager: NovelSourceManager = Injekt.get(),
    private val downloadManager: NovelDownloadManager = Injekt.get(),
    private val updateNovel: UpdateNovel = Injekt.get(),
    private val getChaptersByNovelId: GetNovelChaptersByNovelId = Injekt.get(),
    private val updateChapter: UpdateNovelChapter = Injekt.get(),
    private val getCollections: GetNovelCollections = Injekt.get(),
    private val setNovelCollections: SetNovelCollections = Injekt.get(),
    private val getTracks: GetNovelTracks = Injekt.get(),
    private val insertTrack: InsertNovelTrack = Injekt.get(),
    private val coverCache: NovelCoverCache = Injekt.get(),
    private val preferenceStore: PreferenceStore = Injekt.get(),
) : StateScreenModel<MigrateNovelDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_novel_flags", Int.MAX_VALUE)
    }

    suspend fun migrateNovel(
        oldNovel: Novel,
        newNovel: Novel,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)
        val source = sourceManager.get(newNovel.source) ?: return
        val prevSource = sourceManager.get(oldNovel.source)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            val chapters = source.getChapterList(newNovel.toSNovel())

            migrateNovelInternal(
                oldSource = prevSource,
                newSource = source,
                oldNovel = oldNovel,
                newNovel = newNovel,
                sourceChapters = chapters,
                replace = replace,
                flags = flags,
            )
        } catch (_: Throwable) {
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun migrateNovelInternal(
        oldSource: NovelSource?,
        newSource: NovelSource,
        oldNovel: Novel,
        newNovel: Novel,
        sourceChapters: List<SNovelChapter>,
        replace: Boolean,
        flags: Int,
    ) {
        val migrateChapters = NovelMigrationFlags.hasChapters(flags)
        val migrateCollections = NovelMigrationFlags.hasCollections(flags)
        val migrateCustomCover = NovelMigrationFlags.hasCustomCover(flags)
        val deleteDownloaded = NovelMigrationFlags.hasDeleteDownloaded(flags)

        // Update chapters read, bookmark and dateFetch
        if (migrateChapters) {
            val prevNovelChapters = getChaptersByNovelId.await(oldNovel.id)
            val novelChapters = getChaptersByNovelId.await(newNovel.id)

            val maxChapterRead = prevNovelChapters
                .filter { it.read }
                .maxOfOrNull { it.chapterNumber }

            val updatedNovelChapters = novelChapters.map { novelChapter ->
                var updatedChapter = novelChapter
                if (updatedChapter.isRecognizedNumber) {
                    val prevChapter = prevNovelChapters
                        .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                    if (prevChapter != null) {
                        updatedChapter = updatedChapter.copy(
                            dateFetch = prevChapter.dateFetch,
                            bookmark = prevChapter.bookmark,
                        )
                    }

                    if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                        updatedChapter = updatedChapter.copy(read = true)
                    }
                }

                updatedChapter
            }

            val chapterUpdates = updatedNovelChapters.map { it.toNovelChapterUpdate() }
            updateChapter.awaitAll(chapterUpdates)
        }

        // Update collections
        if (migrateCollections) {
            val collectionIds = getCollections.await(oldNovel.id).map { it.id }
            setNovelCollections.await(newNovel.id, collectionIds)
        }

        // Update track
        getTracks.await(oldNovel.id)
            .map { it.copy(novelId = newNovel.id) }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        // Delete downloaded
        if (deleteDownloaded) {
            if (oldSource != null) {
                downloadManager.deleteNovel(oldNovel, oldSource)
            }
        }

        if (replace) {
            updateNovel.awaitUpdateFavorite(oldNovel.id, favorite = false)
        }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldNovel.hasCustomCover()) {
            coverCache.setCustomCoverToCache(
                newNovel,
                coverCache.getCustomCoverFile(oldNovel.id).inputStream(),
            )
        }

        updateNovel.await(
            NovelUpdate(
                id = newNovel.id,
                favorite = true,
                chapterFlags = oldNovel.chapterFlags,
                viewerFlags = oldNovel.viewerFlags,
                dateAdded = if (replace) oldNovel.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
