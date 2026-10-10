package eu.kanade.presentation.entries.novel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.CoverOption
import eu.kanade.presentation.entries.components.CoverStripSlot
import eu.kanade.presentation.entries.components.EdgeToEdgeDialogWindow
import eu.kanade.presentation.entries.components.RoundedCoverImage
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication

@Composable
fun NovelCoverDialog(
    novel: Novel,
    isCustomCover: Boolean,
    /** The artwork the hero was showing when tapped — a volume cover
     *  URL when the entry is grouped, else null → [novel]'s own cover. */
    coverData: Any? = null,
    /** Artwork candidates for the pin strip — volume covers, per-carrier
     *  art, provider art. Empty while they're still resolving. */
    coverOptions: List<CoverOption> = emptyList(),
    /** The pinned artwork's [CoverOption.key], or null when the entry
     *  resolves covers automatically. */
    pinnedCoverKey: String? = null,
    snackbarHostState: SnackbarHostState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    /** [option] = pin that artwork entry-wide; null = back to Auto. */
    onSelectCover: ((CoverOption?) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    // Selection previews only — nothing applies until the check button
    // commits it via onSelectCover (X always dismisses untouched).
    // `pinnedCoverKey` arrives asynchronously after open, so the baseline
    // latches from it until the user first taps a tile — otherwise a
    // late-landing pin would make V appear untouched.
    var selectedKey by remember(novel.id) { mutableStateOf(pinnedCoverKey) }
    var baseKey by remember(novel.id) { mutableStateOf(pinnedCoverKey) }
    var touched by remember(novel.id) { mutableStateOf(false) }
    LaunchedEffect(pinnedCoverKey) {
        if (!touched) {
            selectedKey = pinnedCoverKey
            baseKey = pinnedCoverKey
        }
    }
    val shownData = remember(coverData, selectedKey, coverOptions) {
        coverOptions.firstOrNull { it.key == selectedKey }?.data ?: coverData
    }
    val selectionDirty = touched && selectedKey != baseKey
    val applySelection: () -> Unit = {
        val option = coverOptions.firstOrNull { it.key == selectedKey }
        onSelectCover?.invoke(option)
        onDismissRequest()
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        EdgeToEdgeDialogWindow()
        Scaffold(
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent,
            bottomBar = {
                // X left, share/save/edit centered, V right — Box
                // alignment keeps the center pill truly centered when
                // V is absent.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .navigationBarsPadding(),
                ) {
                    ActionsPill(modifier = Modifier.align(Alignment.CenterStart)) {
                        IconButton(onClick = onDismissRequest) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(MR.strings.action_close),
                            )
                        }
                    }
                    ActionsPill(modifier = Modifier.align(Alignment.Center)) {
                        AppBarActions(
                            actions = persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_share),
                                    icon = Icons.Outlined.Share,
                                    onClick = onShareClick,
                                ),
                                AppBar.Action(
                                    title = stringResource(MR.strings.action_save),
                                    icon = Icons.Outlined.ArrowDownward,
                                    onClick = onSaveClick,
                                ),
                            ),
                        )
                        if (onEditClick != null) {
                            Box {
                                var expanded by remember { mutableStateOf(false) }
                                IconButton(
                                    onClick = {
                                        if (isCustomCover) {
                                            expanded = true
                                        } else {
                                            onEditClick(EditCoverAction.EDIT)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(
                                            MR.strings.action_edit_cover,
                                        ),
                                    )
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    offset = DpOffset(8.dp, 0.dp),
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(MR.strings.action_edit)) },
                                        onClick = {
                                            onEditClick(EditCoverAction.EDIT)
                                            expanded = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(MR.strings.action_delete)) },
                                        onClick = {
                                            onEditClick(EditCoverAction.DELETE)
                                            expanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (selectionDirty && onSelectCover != null) {
                        ActionsPill(modifier = Modifier.align(Alignment.CenterEnd)) {
                            IconButton(onClick = applySelection) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = stringResource(MR.strings.action_apply),
                                )
                            }
                        }
                    }
                }
            },
        ) { contentPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Scrim before inset padding — it must also cover
                    // the status/nav bar strips of the dialog window.
                    .background(Color.Black.copy(alpha = 0.70f))
                    .padding(contentPadding)
                    .clickableNoIndication(onClick = onDismissRequest),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    RoundedCoverImage(
                        model = shownData ?: novel,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                    )
                    if (coverOptions.isNotEmpty() || onSelectCover != null) {
                        CoverStripSlot(
                            options = coverOptions,
                            selectedKey = selectedKey,
                            onSelect = { option ->
                                touched = true
                                selectedKey = option?.key
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionsPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f)),
    ) {
        content()
    }
}
