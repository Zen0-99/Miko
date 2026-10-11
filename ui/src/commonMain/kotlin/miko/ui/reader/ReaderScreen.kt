package miko.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import miko.ui.components.SettingDropdown
import miko.ui.components.SettingSlider
import miko.ui.components.SettingSwitch
import miko.ui.platform.KeepScreenOn
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Chapter reader — scrollable paragraph text with prev/next chapter
 * navigation at the bottom. Chapters mark themselves read on open.
 * Display prefs (text size, line height, spacing, alignment, background)
 * share Android's pref_novel_* keys and apply live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    model: ReaderScreenModel,
    modifier: Modifier = Modifier,
) {
    val state by model.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    KeepScreenOn(state.keepScreenOn)

    val (bgColor, textColor) = readerColors(state.backgroundColor)
    val contentColor = textColor ?: MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(bgColor?.let { Modifier.background(it) } ?: Modifier),
    ) {
        when {
            state.loading -> LoadingScreen(modifier = Modifier.weight(1f))
            state.error != null -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { model.openChapter(state.chapterUrl) }) {
                    Text(stringResource(MR.strings.action_retry))
                }
            }
            else -> SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = state.sidePadding.dp,
                        vertical = 12.dp,
                    ),
                ) {
                    item {
                        Text(
                            text = state.chapterName,
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    items(state.paragraphs) { paragraph ->
                        Text(
                            text = paragraph,
                            fontSize = state.textSize.sp,
                            lineHeight = (state.textSize * state.lineHeight).sp,
                            textAlign = state.textAlignment.compose(),
                            color = contentColor,
                            modifier = Modifier.padding(
                                bottom = state.paragraphSpacing.dp,
                            ),
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            TextButton(
                onClick = model::prevChapter,
                enabled = state.hasPrev,
            ) {
                Text(stringResource(MR.strings.action_previous_chapter))
            }
            Spacer(Modifier.weight(1f))
            // Android's reading-progress overlay — "chapter X / Y".
            if (state.showProgress && state.chapterCount > 0) {
                Text(
                    text = "${state.chapterIndex} / ${state.chapterCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = model::openChapterSheet) {
                Icon(
                    Icons.AutoMirrored.Filled.FormatListBulleted,
                    contentDescription = stringResource(MR.strings.chapters),
                )
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = stringResource(MR.strings.action_settings),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = model::nextChapter,
                enabled = state.hasNext,
            ) {
                Text(stringResource(MR.strings.action_next_chapter))
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettingsSheet(model = model)
        }
    }

    if (state.showChapters) {
        ChapterListSheet(
            chapters = state.chapters,
            currentUrl = state.chapterUrl,
            onDismiss = model::closeChapterSheet,
            onSelect = model::openChapter,
        )
    }
}

@Composable
private fun readerColors(
    mode: NovelReaderBackgroundColor,
): Pair<Color?, Color?> = when (mode) {
    NovelReaderBackgroundColor.WHITE -> Color.White to Color.Black
    NovelReaderBackgroundColor.BLACK -> Color.Black to Color.White
    NovelReaderBackgroundColor.GRAY -> Color(0xFF202020) to Color.White
    NovelReaderBackgroundColor.SMART_THEME -> null to null
}

private fun TextAlignment.compose(): TextAlign = when (this) {
    TextAlignment.LEFT -> TextAlign.Start
    TextAlignment.CENTER -> TextAlign.Center
    TextAlignment.JUSTIFY -> TextAlign.Justify
    TextAlignment.RIGHT -> TextAlign.End
}

@Composable
private fun ReaderSettingsSheet(model: ReaderScreenModel) {
    val state by model.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(MR.strings.text_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SettingSlider(
            labelRes = MR.strings.text_size,
            value = state.textSize,
            range = 10f..30f,
            onChange = model::setTextSize,
        )
        SettingSlider(
            labelRes = MR.strings.line_height,
            value = state.lineHeight,
            range = 1f..2.5f,
            onChange = model::setLineHeight,
        )
        SettingSlider(
            labelRes = MR.strings.paragraph_spacing,
            value = state.paragraphSpacing.toFloat(),
            range = 0f..40f,
            onChange = { model.setParagraphSpacing(it.toInt()) },
        )
        SettingDropdown(
            labelRes = MR.strings.text_alignment,
            current = stringResource(state.textAlignment.titleRes),
            options = TextAlignment.entries.map { it to it.titleRes },
            onSelect = model::setTextAlignment,
        )
        SettingDropdown(
            labelRes = MR.strings.pref_reader_theme,
            current = stringResource(state.backgroundColor.titleRes),
            options = NovelReaderBackgroundColor.entries.map { it to it.titleRes },
            onSelect = model::setBackgroundColor,
        )
        SettingSlider(
            labelRes = MR.strings.pref_webtoon_side_padding,
            value = state.sidePadding.toFloat(),
            range = 0f..48f,
            onChange = { model.setSidePadding(it.toInt()) },
        )
        SettingSwitch(
            labelRes = MR.strings.pref_keep_screen_on,
            checked = state.keepScreenOn,
            onChange = model::setKeepScreenOn,
        )
        SettingSwitch(
            labelRes = MR.strings.pref_novel_show_reading_progress,
            checked = state.showProgress,
            onChange = model::setShowProgress,
        )
        Spacer(Modifier.height(16.dp))
    }
}
