package eu.kanade.presentation.more.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class ThemeStep : OnboardingStep {

    override val isComplete: Boolean = true

    private val uiPreferences: UiPreferences = Injekt.get()

    @Composable
    override fun Content() {
        val lightThemePref = uiPreferences.lightTheme()
        val lightTheme by lightThemePref.collectAsState()

        val darkThemePref = uiPreferences.darkTheme()
        val darkTheme by darkThemePref.collectAsState()

        val themeModePref = uiPreferences.themeMode()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsState()

        Column {
            Text(
                text = stringResource(MR.strings.pref_light_theme),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppThemePreferenceWidget(
                value = lightTheme,
                amoled = false,
                isDarkTheme = false,
                onItemClick = { theme ->
                    // Apply to all content modes so anime/manga/novel share the onboarding choice
                    lightThemePref.set(theme)
                    uiPreferences.animeLightTheme().set(theme)
                    uiPreferences.novelLightTheme().set(theme)
                    themeModePref.set(ThemeMode.LIGHT)
                    setAppCompatDelegateThemeMode(ThemeMode.LIGHT)
                },
            )
            Text(
                text = stringResource(MR.strings.pref_dark_theme),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppThemePreferenceWidget(
                value = darkTheme,
                amoled = amoled,
                isDarkTheme = true,
                onItemClick = { theme ->
                    // Apply to all content modes so anime/manga/novel share the onboarding choice
                    darkThemePref.set(theme)
                    uiPreferences.animeDarkTheme().set(theme)
                    uiPreferences.novelDarkTheme().set(theme)
                    themeModePref.set(ThemeMode.DARK)
                    setAppCompatDelegateThemeMode(ThemeMode.DARK)
                },
            )
        }
    }
}
