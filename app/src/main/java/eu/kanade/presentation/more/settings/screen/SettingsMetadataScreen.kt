package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.network.NetworkPreferences
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Cover/metadata provider configuration — API keys and credentials for
 * external databases (Metron today; Cinemeta, TMDB, etc. later).
 */
object SettingsMetadataScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_metadata

    @ReadOnlyComposable
    @Composable
    override fun getSubtitleRes() = MR.strings.pref_metadata_summary

    @Composable
    override fun getPreferences(): List<Preference> {
        val networkPreferences = remember { Injekt.get<NetworkPreferences>() }

        return listOf(
            Preference.PreferenceGroup(
                title = "Metron",
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.EditTextPreference(
                        preference = networkPreferences.metronApiToken(),
                        title = stringResource(MR.strings.pref_metron_api_token),
                        subtitle = stringResource(MR.strings.pref_metron_api_token_summary),
                    ),
                ),
            ),
        )
    }
}
