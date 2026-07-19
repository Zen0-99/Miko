package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ContentMode
import eu.kanade.domain.ui.model.NavBarAppearance
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun lightTheme() = preferenceStore.getEnum(
        "pref_light_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun darkTheme() = preferenceStore.getEnum(
        "pref_dark_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)

    // --- Per-content-mode themes ---
    // Manga uses lightTheme()/darkTheme()/themeDarkAmoled() above as the default.
    // Anime and Novel have their own theme prefs so each content type can look distinct.
    // By default they fall back to the same value as the manga (base) theme.

    fun animeLightTheme() = preferenceStore.getEnum(
        "pref_anime_light_theme",
        if (DeviceUtil.isDynamicColorAvailable) AppTheme.MONET else AppTheme.DEFAULT,
    )

    fun animeDarkTheme() = preferenceStore.getEnum(
        "pref_anime_dark_theme",
        if (DeviceUtil.isDynamicColorAvailable) AppTheme.MONET else AppTheme.DEFAULT,
    )

    fun animeThemeDarkAmoled() = preferenceStore.getBoolean("pref_anime_theme_dark_amoled_key", false)

    fun novelLightTheme() = preferenceStore.getEnum(
        "pref_novel_light_theme",
        if (DeviceUtil.isDynamicColorAvailable) AppTheme.MONET else AppTheme.DEFAULT,
    )

    fun novelDarkTheme() = preferenceStore.getEnum(
        "pref_novel_dark_theme",
        if (DeviceUtil.isDynamicColorAvailable) AppTheme.MONET else AppTheme.DEFAULT,
    )

    fun novelThemeDarkAmoled() = preferenceStore.getBoolean("pref_novel_theme_dark_amoled_key", false)

    /**
     * Resolve the light theme preference for the given [ContentMode].
     * MANGA uses the base [lightTheme]; ANIME and NOVEL use their own.
     */
    fun lightThemeFor(mode: ContentMode) = when (mode) {
        ContentMode.MANGA -> lightTheme()
        ContentMode.ANIME -> animeLightTheme()
        ContentMode.NOVEL -> novelLightTheme()
    }

    /**
     * Resolve the dark theme preference for the given [ContentMode].
     * MANGA uses the base [darkTheme]; ANIME and NOVEL use their own.
     */
    fun darkThemeFor(mode: ContentMode) = when (mode) {
        ContentMode.MANGA -> darkTheme()
        ContentMode.ANIME -> animeDarkTheme()
        ContentMode.NOVEL -> novelDarkTheme()
    }

    /**
     * Resolve the amoled preference for the given [ContentMode].
     */
    fun amoledFor(mode: ContentMode) = when (mode) {
        ContentMode.MANGA -> themeDarkAmoled()
        ContentMode.ANIME -> animeThemeDarkAmoled()
        ContentMode.NOVEL -> novelThemeDarkAmoled()
    }

    /**
     * User display name shown in the Home Hub greeting.
     * Empty string means the default placeholder is used.
     */
    fun userName() = preferenceStore.getString("pref_user_name", "")

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun startScreen() = preferenceStore.getEnum("start_screen", StartScreen.HOME)

    fun navStyle() = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.MOVE_HISTORY_TO_MORE)

    fun navBarAppearance() = preferenceStore.getEnum("nav_bar_appearance", NavBarAppearance.STANDARD)

    fun navBarIconsOnly() = preferenceStore.getBoolean("nav_bar_icons_only", false)

    /**
     * The currently active content mode shown across mode-aware tabs (Library, Updates,
     * History). One global mode is shared by all mode-aware tabs so the user sees a single
     * content type at a time. Defaults to MANGA to match the historical Tachiyomi default.
     */
    fun contentMode() = preferenceStore.getEnum("pref_content_mode", ContentMode.MANGA)

    // --- Media type visibility ---
    // When false, the corresponding mode is hidden from the mode carousel and
    // mode-aware tabs (Library, Updates, History, Browse). Extensions and settings
    // for that type remain accessible.

    fun showAnimeMode() = preferenceStore.getBoolean("pref_show_anime_mode", true)

    fun showMangaMode() = preferenceStore.getBoolean("pref_show_manga_mode", true)

    fun showNovelMode() = preferenceStore.getBoolean("pref_show_novel_mode", true)

    /**
     * When true, disables non-essential animations throughout the app:
     * screen transitions, in-app fades, skeleton-loader pulses, image crossfades.
     * Useful for accessibility (reduced motion preference) and low-end devices.
     */
    fun reduceMotion() = preferenceStore.getBoolean("pref_reduce_motion", false)

    /** Show popup notifications when achievements are unlocked. */
    fun showAchievementNotifications() = preferenceStore.getBoolean("show_achievement_notifications", true)

    /**
     * Returns the set of currently visible [ContentMode]s based on the visibility preferences.
     * Always returns at least one mode (falls back to MANGA if all are disabled).
     */
    fun visibleContentModes(): Set<ContentMode> {
        val modes = mutableSetOf<ContentMode>()
        if (showMangaMode().get()) modes.add(ContentMode.MANGA)
        if (showAnimeMode().get()) modes.add(ContentMode.ANIME)
        if (showNovelMode().get()) modes.add(ContentMode.NOVEL)
        if (modes.isEmpty()) modes.add(ContentMode.MANGA)
        return modes
    }

    companion object {
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }
    }
}
