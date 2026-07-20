package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.AppBar.OverflowAction
import eu.kanade.presentation.components.AppBar.CheckableOverflowAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Creates the global overflow actions shown in every tab's three-dot menu:
 * Settings, Downloaded only (toggle), Incognito mode (toggle).
 *
 * Usage: append [create] to each tab's action list.
 */
@Composable
fun globalOverflowActions(
    onClickSettings: () -> Unit,
): ImmutableList<AppBar.AppBarAction> {
    val basePreferences: BasePreferences = Injekt.get()
    val downloadedOnly by basePreferences.downloadedOnly().changes().collectAsState(basePreferences.downloadedOnly().get())
    val incognitoMode by basePreferences.incognitoMode().changes().collectAsState(basePreferences.incognitoMode().get())

    return listOf(
        CheckableOverflowAction(
            title = stringResource(MR.strings.label_downloaded_only),
            checked = downloadedOnly,
            onCheckedChange = { basePreferences.downloadedOnly().set(it) },
        ),
        CheckableOverflowAction(
            title = stringResource(MR.strings.pref_incognito_mode),
            checked = incognitoMode,
            onCheckedChange = { basePreferences.incognitoMode().set(it) },
        ),
        OverflowAction(
            title = stringResource(MR.strings.label_settings),
            onClick = onClickSettings,
        ),
    ).toImmutableList()
}
