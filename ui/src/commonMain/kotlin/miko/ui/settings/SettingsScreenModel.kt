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
import tachiyomi.domain.library.service.LibraryPreferences
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
    private val libraryPreferences: LibraryPreferences,
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
        // Android's PlayerPreferences skip block — same keys/defaults.
        val enableSkipIntro: Boolean = true,
        val autoSkipIntro: Boolean = false,
        val netflixStyleSkip: Boolean = false,
        val waitingTimeSkip: Int = 5,
        val aniSkipEnabled: Boolean = false,
        val disableAniSkipOnChapters: Boolean = true,
        /** TMDB BYOK (`pref_tmdb_api_key`) — Discovery facet filters. */
        val tmdbApiKey: String = "",
        /** Android's smart-update multi-select (`library_update_manga_restriction`). */
        val updateItemRestrictions: Set<String> = emptySet(),
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

    // Android's PlayerPreferences skip block — same keys/defaults.
    private val enableSkipIntroPref = preferenceStore.getBoolean(
        "pref_enable_skip_intro",
        true,
    )
    private val autoSkipIntroPref = preferenceStore.getBoolean(
        "pref_enable_auto_skip_ani_skip",
        false,
    )
    private val netflixStylePref = preferenceStore.getBoolean(
        "pref_enable_netflixStyle_aniskip",
        false,
    )
    private val waitingTimePref = preferenceStore.getInt(
        "pref_waiting_time_aniskip",
        5,
    )
    private val aniSkipEnabledPref = preferenceStore.getBoolean(
        "pref_enable_ani_skip",
        false,
    )
    private val disableAniSkipChaptersPref = preferenceStore.getBoolean(
        "pref_disabled_ani_skip_chapters",
        true,
    )

    // Android's NetworkPreferences BYOK slot (Settings → Advanced).
    private val tmdbApiKeyPref = preferenceStore.getString("pref_tmdb_api_key", "")

    // Android's Settings → Library update smart-update multi-select.
    private val updateItemRestrictionsPref = libraryPreferences.autoUpdateItemRestrictions()

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
        scope.launch {
            enableSkipIntroPref.changes().collect { v ->
                _state.value = _state.value.copy(enableSkipIntro = v)
            }
        }
        scope.launch {
            autoSkipIntroPref.changes().collect { v ->
                _state.value = _state.value.copy(autoSkipIntro = v)
            }
        }
        scope.launch {
            netflixStylePref.changes().collect { v ->
                _state.value = _state.value.copy(netflixStyleSkip = v)
            }
        }
        scope.launch {
            waitingTimePref.changes().collect { v ->
                _state.value = _state.value.copy(waitingTimeSkip = v)
            }
        }
        scope.launch {
            aniSkipEnabledPref.changes().collect { v ->
                _state.value = _state.value.copy(aniSkipEnabled = v)
            }
        }
        scope.launch {
            disableAniSkipChaptersPref.changes().collect { v ->
                _state.value = _state.value.copy(disableAniSkipOnChapters = v)
            }
        }
        scope.launch {
            tmdbApiKeyPref.changes().collect { v ->
                _state.value = _state.value.copy(tmdbApiKey = v)
            }
        }
        scope.launch {
            updateItemRestrictionsPref.changes().collect { v ->
                _state.value = _state.value.copy(updateItemRestrictions = v)
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

    fun setEnableSkipIntro(v: Boolean) = enableSkipIntroPref.set(v)

    fun setAutoSkipIntro(v: Boolean) = autoSkipIntroPref.set(v)

    fun setNetflixStyleSkip(v: Boolean) = netflixStylePref.set(v)

    fun setWaitingTimeSkip(seconds: Int) = waitingTimePref.set(seconds)

    fun setAniSkipEnabled(v: Boolean) = aniSkipEnabledPref.set(v)

    fun setDisableAniSkipOnChapters(v: Boolean) = disableAniSkipChaptersPref.set(v)

    fun setTmdbApiKey(key: String) = tmdbApiKeyPref.set(key.trim())

    fun setUpdateItemRestriction(key: String, enabled: Boolean) {
        val current = updateItemRestrictionsPref.get()
        updateItemRestrictionsPref.set(
            if (enabled) current + key else current - key,
        )
    }

    fun dispose() = scope.cancel()
}
