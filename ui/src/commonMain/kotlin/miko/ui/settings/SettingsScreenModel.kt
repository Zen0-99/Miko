package miko.ui.settings

import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import miko.ui.reader.NovelReaderBackgroundColor
import miko.ui.reader.TextAlignment
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

/** Mirror of app's eu.kanade.domain.ui.model.ThemeMode (shared key/value). */
enum class ThemeMode(val titleRes: StringResource) {
    LIGHT(MR.strings.theme_light),
    DARK(MR.strings.theme_dark),
    SYSTEM(MR.strings.theme_system),
}

/**
 * Shared settings — same preference keys as Android's UiPreferences /
 * LibraryPreferences so settings converge when the shells merge.
 */
class SettingsScreenModel(
    private val preferenceStore: PreferenceStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class State(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val showLibraryTitle: Boolean = true,
        val readerTextSize: Float = 16f,
        val readerLineHeight: Float = 1.5f,
        val readerParagraphSpacing: Int = 16,
        val readerTextAlignment: TextAlignment = TextAlignment.JUSTIFY,
        val readerBackgroundColor: NovelReaderBackgroundColor =
            NovelReaderBackgroundColor.GRAY,
    )

    private val themeModePref = preferenceStore.getEnum(
        "pref_theme_mode_key",
        ThemeMode.SYSTEM,
    )
    private val libraryTitlePref = preferenceStore.getBoolean(
        "display_library_title",
        true,
    )
    private val readerTextSizePref = preferenceStore.getFloat(
        "pref_novel_text_size",
        16f,
    )
    private val readerLineHeightPref = preferenceStore.getFloat(
        "pref_novel_line_height",
        1.5f,
    )
    private val readerParagraphSpacingPref = preferenceStore.getInt(
        "pref_novel_paragraph_spacing",
        16,
    )
    private val readerTextAlignmentPref = preferenceStore.getEnum(
        "pref_novel_text_alignment",
        TextAlignment.JUSTIFY,
    )
    private val readerBackgroundColorPref = preferenceStore.getEnum(
        "pref_novel_bg_color_mode",
        NovelReaderBackgroundColor.GRAY,
    )

    private val scope = CoroutineScope(Job() + dispatcher)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        scope.launch {
            themeModePref.changes().collect { mode ->
                _state.value = _state.value.copy(themeMode = mode)
            }
        }
        scope.launch {
            libraryTitlePref.changes().collect { show ->
                _state.value = _state.value.copy(showLibraryTitle = show)
            }
        }
        scope.launch {
            readerTextSizePref.changes().collect { v ->
                _state.value = _state.value.copy(readerTextSize = v)
            }
        }
        scope.launch {
            readerLineHeightPref.changes().collect { v ->
                _state.value = _state.value.copy(readerLineHeight = v)
            }
        }
        scope.launch {
            readerParagraphSpacingPref.changes().collect { v ->
                _state.value = _state.value.copy(readerParagraphSpacing = v)
            }
        }
        scope.launch {
            readerTextAlignmentPref.changes().collect { v ->
                _state.value = _state.value.copy(readerTextAlignment = v)
            }
        }
        scope.launch {
            readerBackgroundColorPref.changes().collect { v ->
                _state.value = _state.value.copy(readerBackgroundColor = v)
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) = themeModePref.set(mode)

    fun setShowLibraryTitle(show: Boolean) = libraryTitlePref.set(show)

    fun setReaderTextSize(size: Float) = readerTextSizePref.set(size)

    fun setReaderLineHeight(height: Float) = readerLineHeightPref.set(height)

    fun setReaderParagraphSpacing(spacing: Int) = readerParagraphSpacingPref.set(spacing)

    fun setReaderTextAlignment(alignment: TextAlignment) = readerTextAlignmentPref.set(alignment)

    fun setReaderBackgroundColor(color: NovelReaderBackgroundColor) =
        readerBackgroundColorPref.set(color)

    fun dispose() = scope.cancel()
}
