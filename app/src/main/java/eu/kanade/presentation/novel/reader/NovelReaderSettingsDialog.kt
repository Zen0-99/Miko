package eu.kanade.presentation.novel.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import eu.kanade.tachiyomi.ui.reader.novel.NovelOrientation
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundColor
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.reader.novel.NovelReadingMode
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderTypographyPreset
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderBackgroundTexture
import eu.kanade.tachiyomi.ui.reader.novel.NovelAutoScrollChapterEndBehavior
import eu.kanade.tachiyomi.ui.reader.novel.NovelPageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.novel.TextAlignment
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelReaderSettingsScreenModel(
    val hasDisplayCutout: Boolean,
    val onReadingModeChange: (Int) -> Unit,
    val onBackgroundColorChange: (Int) -> Unit,
    val onOrientationChange: (Int) -> Unit,
    val onTextSettingChange: () -> Unit,
    val preferences: NovelReaderPreferences = Injekt.get(),
    val readerPreferences: ReaderPreferences = Injekt.get(),
    val ttsPreferences: eu.kanade.tachiyomi.ui.reader.novel.NovelTtsPreferences = Injekt.get(),
    // Neural TTS voice management
    val installedNeuralVoices: kotlinx.coroutines.flow.StateFlow<List<eu.kanade.tachiyomi.ui.reader.novel.tts.InstalledNeuralVoice>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList()),
    val downloadingVoiceId: kotlinx.coroutines.flow.StateFlow<String?> = kotlinx.coroutines.flow.MutableStateFlow(null),
    val voiceDownloadProgress: kotlinx.coroutines.flow.StateFlow<Float> = kotlinx.coroutines.flow.MutableStateFlow(0f),
    val onDownloadVoice: (eu.kanade.tachiyomi.ui.reader.novel.tts.NeuralVoiceEntry) -> Unit = {},
    val onUninstallVoice: (String) -> Unit = {},
    val onSelectNeuralVoice: (eu.kanade.tachiyomi.ui.reader.novel.tts.InstalledNeuralVoice) -> Unit = {},
) : ScreenModel

/**
 * Bottom sheet settings — matches Miko's layout: ModalBottomSheet with
 * TabRow + HorizontalPager, both tabs share the same height via
 * weight(1f). Internal column scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    screenModel: NovelReaderSettingsScreenModel,
    accentColor: Color? = null,
    // Scroll states hoisted to caller so they persist across open/close
    // within the same chapter session, but reset when the chapter changes.
    generalScrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    textScrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    ttsScrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    savedPage: Int = 0,
    onPageSaved: (Int) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tabTitles = persistentListOf(
        stringResource(MR.strings.pref_category_general),
        stringResource(MR.strings.text_settings),
        "TTS",
    )
    val pagerState = rememberPagerState(initialPage = savedPage.coerceIn(0, tabTitles.size - 1)) { tabTitles.size }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // Fixed height — both tabs are the same size (55% of screen).
    val sheetHeight = configuration.screenHeightDp.dp * 0.55f

    ModalBottomSheet(
        onDismissRequest = {
            onPageSaved(pagerState.currentPage)
            onDismissRequest()
            onShowMenus()
        },
        sheetState = sheetState,
        // No drag handle — sheet still drags but no visual handle bar.
        dragHandle = null,
    ) {
        val accent = accentColor ?: MaterialTheme.colorScheme.primary
        val accentedScheme = MaterialTheme.colorScheme.copy(primary = accent)
        MaterialTheme(colorScheme = accentedScheme) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    val targetPos = tabPositions[pagerState.currentPage]
                    val fraction = pagerState.currentPageOffsetFraction
                    val leftDp = targetPos.left + targetPos.width * fraction
                    val widthDp = targetPos.width
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .offset(x = leftDp)
                                .width(widthDp)
                                .height(2.dp)
                                .align(Alignment.BottomStart)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                },
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) },
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val scrollState = when (page) {
                0 -> generalScrollState
                1 -> textScrollState
                else -> ttsScrollState
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .verticalScroll(scrollState),
            ) {
                when (page) {
                    0 -> NovelGeneralSettingsPage(screenModel, accentColor)
                    1 -> NovelTextSettingsPage(screenModel, accentColor)
                    2 -> NovelTtsSettingsPage(
                        preferences = screenModel.ttsPreferences,
                        accentColor = accentColor,
                        installedNeuralVoices = screenModel.installedNeuralVoices.collectAsStateWithLifecycle().value,
                        downloadingVoiceId = screenModel.downloadingVoiceId.collectAsStateWithLifecycle().value,
                        downloadProgress = screenModel.voiceDownloadProgress.collectAsStateWithLifecycle().value,
                        onDownloadVoice = screenModel.onDownloadVoice,
                        onUninstallVoice = screenModel.onUninstallVoice,
                        onSelectNeuralVoice = screenModel.onSelectNeuralVoice,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// General page: Series + General sections
// ---------------------------------------------------------------------

@Composable
private fun ColumnScope.NovelGeneralSettingsPage(
    screenModel: NovelReaderSettingsScreenModel,
    accentColor: Color? = null,
) {
    // ---- Series section (colored header) ----
    SectionHeader("Series", accentColor)

    val orientation by screenModel.preferences.orientation().collectAsState()
    val orientationLabels = listOf(
        stringResource(MR.strings.orientation_free),
        stringResource(MR.strings.orientation_portrait),
        stringResource(MR.strings.orientation_landscape),
        stringResource(MR.strings.orientation_locked_portrait),
        stringResource(MR.strings.orientation_locked_landscape),
    )
    val orientationValues = NovelOrientation.entries
    SettingsDropdown(
        label = stringResource(MR.strings.novel_orientation),
        selectedLabel = orientationLabels[orientationValues.indexOf(orientation)],
        options = orientationLabels,
    ) { index ->
        val o = orientationValues[index]
        screenModel.preferences.orientation().set(o)
        screenModel.onOrientationChange(o.prefValue)
    }

    // ---- General section (colored header) ----
    SectionHeader("General", accentColor)

    // ---- Display sub-section ----
    SubHeader("Display", accentColor)

    CheckboxItem(
        label = "Use cover accent color",
        pref = screenModel.preferences.useCoverAccentColor(),
        accentColor = accentColor,
    )

    // ---- Reader tools sub-section ----
    SubHeader("Reader tools", accentColor)

    CheckboxItem(
        label = "Smart-fit margins",
        pref = screenModel.preferences.smartFitMargins(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Join chapters (no headers)",
        pref = screenModel.preferences.joinChapters(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "E-Ink binarization",
        pref = screenModel.preferences.eInkBinarization(),
        accentColor = accentColor,
    )

    // ---- Background sub-section ----
    SubHeader("Background", accentColor)

    val bgColorMode by screenModel.preferences.backgroundColorMode().collectAsState()
    val bgColorLabels = listOf(
        stringResource(MR.strings.white_background),
        stringResource(MR.strings.black_background),
        stringResource(MR.strings.smart_by_theme),
        stringResource(MR.strings.gray_background),
    )
    val bgColorValues = NovelReaderBackgroundColor.entries
    SettingsDropdown(
        label = stringResource(MR.strings.pref_reader_theme),
        selectedLabel = bgColorLabels[bgColorValues.indexOf(bgColorMode)],
        options = bgColorLabels,
    ) { index ->
        val mode = bgColorValues[index]
        screenModel.preferences.backgroundColorMode().set(mode)
        screenModel.onBackgroundColorChange(mode.prefValue)
    }

    val bgTexture by screenModel.preferences.backgroundTexture().collectAsState()
    val textureLabels = listOf("None", "Paper grain", "Linen", "Parchment")
    val textureValues = NovelReaderBackgroundTexture.entries
    SettingsDropdown(
        label = "Texture",
        selectedLabel = textureLabels[textureValues.indexOf(bgTexture)],
        options = textureLabels,
    ) { index ->
        screenModel.preferences.backgroundTexture().set(textureValues[index])
        screenModel.onTextSettingChange()
    }

    val textureStrength by screenModel.preferences.nativeTextureStrength().collectAsState()
    SliderItem(
        label = "Texture strength",
        value = textureStrength,
        valueRange = 0..100,
        valueText = "${textureStrength}%",
        steps = 99,
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.nativeTextureStrength().set(newValue)
        screenModel.onTextSettingChange()
    }

    CheckboxItem(
        label = "OLED edge gradient",
        pref = screenModel.preferences.oledEdgeGradient(),
        accentColor = accentColor,
    )

    // ---- Auto-scroll sub-section (Tier 3) ----
    SubHeader("Auto-scroll", accentColor)

    CheckboxItem(
        label = "Enable auto-scroll",
        pref = screenModel.preferences.autoScroll(),
        accentColor = accentColor,
    )

    val autoScrollInterval by screenModel.preferences.autoScrollInterval().collectAsState()
    SliderItem(
        label = "Scroll interval",
        value = autoScrollInterval,
        valueRange = 500..10000,
        valueText = "${autoScrollInterval}ms",
        steps = 94,
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.autoScrollInterval().set(newValue)
    }

    val autoScrollOffset by screenModel.preferences.autoScrollOffset().collectAsState()
    SliderItem(
        label = "Scroll step (px)",
        value = autoScrollOffset,
        valueRange = 10..500,
        valueText = "${autoScrollOffset}px",
        steps = 489,
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.autoScrollOffset().set(newValue)
    }

    CheckboxItem(
        label = "Adaptive delay",
        pref = screenModel.preferences.autoScrollAdaptiveDelay(),
        accentColor = accentColor,
    )

    val autoScrollEndBehavior by screenModel.preferences.autoScrollChapterEndBehavior().collectAsState()
    val endBehaviorLabels = listOf("Stop at end", "Advance and stop", "Continuous reading")
    val endBehaviorValues = NovelAutoScrollChapterEndBehavior.entries
    SettingsDropdown(
        label = "Chapter end behavior",
        selectedLabel = endBehaviorLabels[endBehaviorValues.indexOf(autoScrollEndBehavior)],
        options = endBehaviorLabels,
    ) { index ->
        screenModel.preferences.autoScrollChapterEndBehavior().set(endBehaviorValues[index])
    }

    CheckboxItem(
        label = "Show floating button",
        pref = screenModel.preferences.showAutoScrollFloatingButton(),
        accentColor = accentColor,
    )

    // ---- Navigation sub-section ----
    SubHeader("Navigation", accentColor)

    val novelReadingMode by screenModel.preferences.readingMode().collectAsState()
    val readingModeLabels = listOf(
        stringResource(MR.strings.reading_mode_default),
        stringResource(MR.strings.reading_mode_infinite_scroll),
        stringResource(MR.strings.reading_mode_overscroll),
    )
    val readingModeValues = NovelReadingMode.entries
    SettingsDropdown(
        label = stringResource(MR.strings.reading_mode_title),
        selectedLabel = readingModeLabels[readingModeValues.indexOf(novelReadingMode)],
        options = readingModeLabels,
    ) { index ->
        val mode = readingModeValues[index]
        screenModel.preferences.readingMode().set(mode)
        screenModel.onReadingModeChange(mode.prefValue)
    }

    val pageTransition by screenModel.preferences.pageTransitionStyle().collectAsState()
    val transitionLabels = listOf("Instant", "Slide", "Depth", "Book", "Curl", "Book flip")
    val transitionValues = NovelPageTransitionStyle.entries
    SettingsDropdown(
        label = "Page transition",
        selectedLabel = transitionLabels[transitionValues.indexOf(pageTransition)],
        options = transitionLabels,
    ) { index ->
        screenModel.preferences.pageTransitionStyle().set(transitionValues[index])
    }

    CheckboxItem(
        label = "Tap to scroll",
        pref = screenModel.preferences.tapToScroll(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Vertical seekbar",
        pref = screenModel.preferences.verticalSeekbar(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Prefetch next chapter",
        pref = screenModel.preferences.prefetchNextChapter(),
        accentColor = accentColor,
    )

    // ---- Info display sub-section (Tier 3) ----
    SubHeader("Info display", accentColor)

    CheckboxItem(
        label = "Scroll percentage",
        pref = screenModel.preferences.showScrollPercentage(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Battery and time",
        pref = screenModel.preferences.showBatteryAndTime(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Estimated reading time",
        pref = screenModel.preferences.showEstimatedReadingTime(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Time to end",
        pref = screenModel.preferences.showTimeToEnd(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Word count",
        pref = screenModel.preferences.showWordCount(),
        accentColor = accentColor,
    )

    // ---- Text selection sub-section (Tier 3) ----
    SubHeader("Text selection", accentColor)

    CheckboxItem(
        label = "Enable text selection",
        pref = screenModel.preferences.textSelectionEnabled(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Selected text translation",
        pref = screenModel.preferences.selectedTextTranslationEnabled(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Novel dictionary (Wiktionary)",
        pref = screenModel.preferences.novelDictionaryEnabled(),
        accentColor = accentColor,
    )

    // ---- System sub-section ----
    SubHeader("System", accentColor)

    CheckboxItem(
        label = stringResource(MR.strings.pref_fullscreen),
        pref = screenModel.preferences.fullscreen(),
        accentColor = accentColor,
    )

    if (screenModel.hasDisplayCutout && screenModel.preferences.fullscreen().get()) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_cutout_short),
            pref = screenModel.preferences.cutoutShort(),
            accentColor = accentColor,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_keep_screen_on),
        pref = screenModel.readerPreferences.keepScreenOn(),
        accentColor = accentColor,
    )
}

// ---------------------------------------------------------------------
// Text page — single-line rows, no section headings (per user choice)
// ---------------------------------------------------------------------

@Composable
private fun ColumnScope.NovelTextSettingsPage(
    screenModel: NovelReaderSettingsScreenModel,
    accentColor: Color? = null,
) {
    val textSize by screenModel.preferences.textSize().collectAsState()
    val lineHeight by screenModel.preferences.lineHeight().collectAsState()
    val paragraphSpacing by screenModel.preferences.paragraphSpacing().collectAsState()
    val sidePadding by screenModel.preferences.sidePadding().collectAsState()
    val textAlignment by screenModel.preferences.textAlignment().collectAsState()
    val bionicReading by screenModel.preferences.bionicReading().collectAsState()

    // No section headings — each row is a single line: label + value.
    SliderItem(
        label = stringResource(MR.strings.text_size),
        value = textSize.toInt(),
        valueRange = 10..32,
        valueText = "${textSize.toInt()}sp",
        steps = 21, // 1sp increments: (32-10)-1 = 21
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.textSize().set(newValue.toFloat())
        screenModel.onTextSettingChange()
    }

    FloatSliderItem(
        label = stringResource(MR.strings.line_height),
        value = lineHeight,
        valueRange = 0.8f..5.0f,
        valueText = "%.1fx".format(lineHeight),
        steps = 20, // 0.2 increments: (5.0-0.8)/0.2 - 1 = 20
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.lineHeight().set(newValue)
        screenModel.onTextSettingChange()
    }

    SliderItem(
        label = stringResource(MR.strings.paragraph_spacing),
        value = paragraphSpacing,
        valueRange = 0..48,
        valueText = "${paragraphSpacing}dp",
        steps = 47, // 1dp increments
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.paragraphSpacing().set(newValue)
        screenModel.onTextSettingChange()
    }

    SliderItem(
        label = stringResource(MR.strings.pref_webtoon_side_padding),
        value = sidePadding,
        valueRange = 0..64,
        valueText = "${sidePadding}dp",
        steps = 63, // 1dp increments
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.sidePadding().set(newValue)
        screenModel.onTextSettingChange()
    }

    val alignmentLabels = listOf(
        stringResource(MR.strings.alignment_left),
        stringResource(MR.strings.alignment_center),
        stringResource(MR.strings.alignment_justify),
        stringResource(MR.strings.alignment_right),
    )
    val alignmentValues = TextAlignment.entries
    SettingsDropdown(
        label = stringResource(MR.strings.text_alignment),
        selectedLabel = alignmentLabels[alignmentValues.indexOf(textAlignment)],
        options = alignmentLabels,
    ) { index ->
        screenModel.preferences.textAlignment().set(alignmentValues[index])
        screenModel.onTextSettingChange()
    }

    CheckboxItem(
        label = stringResource(MR.strings.bionic_reading),
        pref = screenModel.preferences.bionicReading(),
        accentColor = accentColor,
    )

    // ---- Typography sub-section ----
    SubHeader("Typography", accentColor)

    val typographyPreset by screenModel.preferences.typographyPreset().collectAsState()
    val typographyLabels = listOf("Custom", "Super Golden", "Golden")
    val typographyValues = NovelReaderTypographyPreset.entries
    SettingsDropdown(
        label = "Typography preset",
        selectedLabel = typographyLabels[typographyValues.indexOf(typographyPreset)],
        options = typographyLabels,
    ) { index ->
        screenModel.preferences.typographyPreset().set(typographyValues[index])
        screenModel.onTextSettingChange()
    }

    CheckboxItem(
        label = "Force paragraph indent",
        pref = screenModel.preferences.forceParagraphIndent(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Force bold text",
        pref = screenModel.preferences.forceBoldText(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Force italic text",
        pref = screenModel.preferences.forceItalicText(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Preserve source text alignment",
        pref = screenModel.preferences.preserveSourceTextAlignInNative(),
        accentColor = accentColor,
    )

    CheckboxItem(
        label = "Enable text selection",
        pref = screenModel.preferences.textSelectionEnabled(),
        accentColor = accentColor,
    )

    // ---- Text shadow sub-section ----
    SubHeader("Text shadow", accentColor)

    CheckboxItem(
        label = "Enable text shadow",
        pref = screenModel.preferences.textShadowEnabled(),
        accentColor = accentColor,
    )

    val textShadowBlur by screenModel.preferences.textShadowBlur().collectAsState()
    FloatSliderItem(
        label = "Shadow blur",
        value = textShadowBlur,
        valueRange = 0f..20f,
        valueText = "%.1f".format(textShadowBlur),
        steps = 19,
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.textShadowBlur().set(newValue)
        screenModel.onTextSettingChange()
    }

    val textShadowY by screenModel.preferences.textShadowY().collectAsState()
    FloatSliderItem(
        label = "Shadow Y offset",
        value = textShadowY,
        valueRange = -10f..10f,
        valueText = "%.1f".format(textShadowY),
        steps = 19,
        accentColor = accentColor,
    ) { newValue ->
        screenModel.preferences.textShadowY().set(newValue)
        screenModel.onTextSettingChange()
    }
}
