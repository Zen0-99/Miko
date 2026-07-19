package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.domain.ui.model.NavBarAppearance
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

object SettingsAppearanceScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @ReadOnlyComposable
    @Composable
    override fun getSubtitleRes() = MR.strings.pref_appearance_summary

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        // Local editing mode — which content mode's theme the user is editing.
        // Defaults to the current global content mode so users edit what they see.
        var editingMode by remember {
            mutableStateOf(uiPreferences.contentMode().get())
        }

        // Resolve the per-mode theme preferences for the currently edited mode.
        val lightThemePref = uiPreferences.lightThemeFor(editingMode)
        val lightTheme by lightThemePref.collectAsState()

        val darkThemePref = uiPreferences.darkThemeFor(editingMode)
        val darkTheme by darkThemePref.collectAsState()

        val themeModePref = uiPreferences.themeMode()

        val amoledPref = uiPreferences.amoledFor(editingMode)
        val amoled by amoledPref.collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                // --- Content mode selector ---
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(AYMR.strings.pref_content_mode_theme),
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PrefsHorizontalPadding),
                    ) {
                        ContentMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = mode == editingMode,
                                onClick = { editingMode = mode },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index,
                                    ContentMode.entries.size,
                                ),
                            ) {
                                Text(stringResource(mode.titleRes))
                            }
                        }
                    }
                },
                // --- Light theme for the selected mode ---
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_light_theme),
                ) {
                    AppThemePreferenceWidget(
                        value = lightTheme,
                        amoled = false,
                        isDarkTheme = false,
                        onItemClick = {
                            lightThemePref.set(it)
                            themeModePref.set(ThemeMode.LIGHT)
                            setAppCompatDelegateThemeMode(ThemeMode.LIGHT)
                            (context as? Activity)?.let { activity -> ActivityCompat.recreate(activity) }
                        },
                    )
                },
                // --- Dark theme for the selected mode ---
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_dark_theme),
                ) {
                    AppThemePreferenceWidget(
                        value = darkTheme,
                        amoled = amoled,
                        isDarkTheme = true,
                        onItemClick = {
                            darkThemePref.set(it)
                            themeModePref.set(ThemeMode.DARK)
                            setAppCompatDelegateThemeMode(ThemeMode.DARK)
                            (context as? Activity)?.let { activity -> ActivityCompat.recreate(activity) }
                        },
                    )
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsState()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.tabletUiMode(),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.startScreen(),
                    entries = StartScreen.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_start_screen),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.navStyle(),
                    entries = NavStyle.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = "Navigation Style",
                    onValueChanged = { true },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.navBarAppearance(),
                    entries = NavBarAppearance.entries
                        .associateWith { it.displayName }
                        .toImmutableMap(),
                    title = "Bottom navigation appearance",
                    onValueChanged = { true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showAnimeMode(),
                    title = "Show Anime",
                    subtitle = "Display anime in the mode carousel and mode-aware tabs",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showMangaMode(),
                    title = "Show Manga",
                    subtitle = "Display manga in the mode carousel and mode-aware tabs",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showNovelMode(),
                    title = "Show Novels",
                    subtitle = "Display novels in the mode carousel and mode-aware tabs",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.reduceMotion(),
                    title = "Reduce Motion",
                    subtitle = "Disable non-essential animations for accessibility and performance",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.showAchievementNotifications(),
                    title = stringResource(AYMR.strings.pref_show_achievement_notifications),
                    subtitle = stringResource(AYMR.strings.pref_show_achievement_notifications_summary),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.dateFormat(),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_date_format),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }
}

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
