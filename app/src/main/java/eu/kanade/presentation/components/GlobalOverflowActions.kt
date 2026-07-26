package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import eu.kanade.presentation.components.AppBar.OverflowAction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Creates the global overflow actions shown in every tab's three-dot menu.
 *
 * "Downloaded only" and "Incognito mode" toggles have been moved to the
 * Settings screen. Only the Settings entry point remains here.
 *
 * Usage: append [globalOverflowActions] to each tab's action list.
 */
@Composable
fun globalOverflowActions(
    onClickSettings: () -> Unit,
): ImmutableList<AppBar.AppBarAction> {
    return listOf(
        OverflowAction(
            title = stringResource(MR.strings.label_settings),
            onClick = onClickSettings,
        ),
    ).toImmutableList()
}
