package eu.kanade.presentation.entries.shows.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.request.ImageRequest
import eu.kanade.presentation.components.AchievementStyledSnackbarHost
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.entries.EditCoverAction
import eu.kanade.presentation.entries.components.CoverStripSlot
import eu.kanade.presentation.entries.components.EdgeToEdgeDialogWindow
import eu.kanade.presentation.entries.components.RoundedCoverImage
import eu.kanade.tachiyomi.data.coil.useBackground
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.clickableNoIndication

@Composable
fun ShowsImagesDialog(
    anime: Anime,
    coverData: Any? = null,
    /** Artwork candidates for the pin strip — season posters, provider
     *  art. Empty while they're still resolving. */
    coverOptions: List<eu.kanade.presentation.entries.components.CoverOption> = emptyList(),
    /** The pinned artwork's option key, or null for automatic. */
    pinnedCoverKey: String? = null,
    isCustomCover: Boolean,
    isCustomBackground: Boolean,
    snackbarHostState: SnackbarHostState,
    pagerState: PagerState,
    onShareClick: () -> Unit,
    onSaveClick: () -> Unit,
    onEditClick: ((EditCoverAction) -> Unit)?,
    /** [option] = pin that artwork entry-wide; null = back to Auto. */
    onSelectCover: ((eu.kanade.presentation.entries.components.CoverOption?) -> Unit)? = null,
    onDismissRequest: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isCover = pagerState.currentPage != 1
    // Selection previews only — nothing applies until the check button
    // commits it via onSelectCover (X always dismisses untouched).
    // `pinnedCoverKey` arrives asynchronously after open, so the baseline
    // latches from it until the user first taps a tile — otherwise a
    // late-landing pin would make V appear untouched.
    var selectedKey by remember(anime.id) { mutableStateOf(pinnedCoverKey) }
    var baseKey by remember(anime.id) { mutableStateOf(pinnedCoverKey) }
    var touched by remember(anime.id) { mutableStateOf(false) }
    LaunchedEffect(pinnedCoverKey) {
        if (!touched) {
            selectedKey = pinnedCoverKey
            baseKey = pinnedCoverKey
        }
    }
    val shownData = remember(coverData, selectedKey, coverOptions) {
        coverOptions.firstOrNull { it.key == selectedKey }?.data ?: coverData
    }
    val selectionDirty = isCover && touched && selectedKey != baseKey
    val applySelection: () -> Unit = {
        val option = coverOptions.firstOrNull { it.key == selectedKey }
        onSelectCover?.invoke(option)
        onDismissRequest()
    }

    val arrowIcon = if (isCover) {
        Icons.AutoMirrored.Outlined.KeyboardArrowRight
    } else {
        Icons.AutoMirrored.Outlined.KeyboardArrowLeft
    }

    val (editImageStringResource, alternateImageStringResource) = if (isCover) {
        MR.strings.action_edit_cover to AYMR.strings.action_edit_background
    } else {
        AYMR.strings.action_edit_background to MR.strings.action_edit_cover
    }

    val onImageSwitchClicked: () -> Unit = {
        scope.launchUI {
            pagerState.animateScrollToPage(1 - pagerState.currentPage)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false, // Doesn't work https://issuetracker.google.com/issues/246909281
        ),
    ) {
        EdgeToEdgeDialogWindow()
        Scaffold(
            snackbarHost = { AchievementStyledSnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent,
            bottomBar = {
                // X + page-switch left, share/save/edit centered, V
                // right — Box alignment keeps the center pill truly
                // centered when V is absent.
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
                        IconButton(onClick = onImageSwitchClicked) {
                            Icon(
                                imageVector = arrowIcon,
                                contentDescription = stringResource(alternateImageStringResource),
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
                                        if ((isCover && isCustomCover) || (!isCover && isCustomBackground)) {
                                            expanded = true
                                        } else {
                                            onEditClick(EditCoverAction.EDIT)
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(editImageStringResource),
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
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                    ) { page ->
                        // The cover page shows the strip selection (or
                        // the artwork the hero displayed), falling back
                        // to the persisted entry cover; page 1 stays the
                        // background.
                        val context = LocalContext.current
                        val backgroundRequest = remember(anime) {
                            ImageRequest.Builder(context)
                                .data(anime)
                                .useBackground(true)
                                .build()
                        }
                        val model = if (page == 0) shownData ?: anime else backgroundRequest
                        RoundedCoverImage(
                            model = model,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp),
                        )
                    }
                    if (pagerState.currentPage == 0 &&
                        (coverOptions.isNotEmpty() || onSelectCover != null)
                    ) {
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
