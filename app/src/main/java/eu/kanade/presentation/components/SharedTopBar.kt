package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Shared top-bar state. Each tab writes its title and actions into this holder;
 * the HomeScreen renders a single persistent [AppBar] from it so the top bar
 * does not rebuild/fade on tab navigation — the same way the bottom nav bar
 * stays persistent.
 */
class SharedTopBarState {
    var title by mutableStateOf("")
    var actions by mutableStateOf<ImmutableList<AppBar.AppBarAction>>(persistentListOf())
    var navigateUp by mutableStateOf<(() -> Unit)?>(null)
}

val LocalSharedTopBar = compositionLocalOf<SharedTopBarState?> { null }

/**
 * Convenience helper for tabs to update the shared top bar.
 * Call inside a Composable to set the title and actions for the current tab.
 */
@Composable
fun useSharedTopBar(
    title: String,
    actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    navigateUp: (() -> Unit)? = null,
) {
    val state = LocalSharedTopBar.current
    state?.let {
        it.title = title
        it.actions = actions
        it.navigateUp = navigateUp
    }
}
