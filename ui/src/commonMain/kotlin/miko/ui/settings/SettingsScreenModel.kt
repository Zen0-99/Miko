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
    )

    private val themeModePref = preferenceStore.getEnum(
        "pref_theme_mode_key",
        ThemeMode.SYSTEM,
    )
    private val libraryTitlePref = preferenceStore.getBoolean(
        "display_library_title",
        true,
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
    }

    fun themeModeChanges() = themeModePref.changes()

    fun setThemeMode(mode: ThemeMode) = themeModePref.set(mode)

    fun setShowLibraryTitle(show: Boolean) = libraryTitlePref.set(show)

    fun dispose() = scope.cancel()
}
