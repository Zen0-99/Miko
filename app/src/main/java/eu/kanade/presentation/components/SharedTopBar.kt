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
 *
 * Search support: tabs can opt into search by setting [searchEnabled] = true
 * and providing [onSearchQueryChange]. When [searchQuery] is non-null, the
 * HomeScreen renders a search text field instead of the title. An optional
 * [searchPillContent] composable can be provided to render content (e.g. a
 * mode toggle pill) below the search bar while search is active.
 */
class SharedTopBarState {
    var title by mutableStateOf("")
    var actions by mutableStateOf<ImmutableList<AppBar.AppBarAction>>(persistentListOf())
    var navigateUp by mutableStateOf<(() -> Unit)?>(null)

    // Search state
    /** Whether this tab supports search at all. If false, no search icon or tappable area. */
    var searchAvailable by mutableStateOf(false)
    var searchEnabled by mutableStateOf(false)
    var searchQuery by mutableStateOf<String?>(null)
    var onSearchQueryChange by mutableStateOf<(String?) -> Unit>({ })
    var onSearch by mutableStateOf<(String) -> Unit>({ })
    var searchPlaceholderText by mutableStateOf<String?>(null)
    var searchPillContent by mutableStateOf<(@Composable () -> Unit)?>(null)

    /**
     * Reset search-related fields. Called by HomeScreen when switching tabs
     * so stale search state from a previous tab doesn't leak.
     */
    fun resetSearch() {
        searchAvailable = false
        searchEnabled = false
        searchQuery = null
        onSearchQueryChange = { }
        onSearch = { }
        searchPlaceholderText = null
        searchPillContent = null
    }
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
        // Clear search state so it doesn't leak from a previous tab that had search
        it.searchAvailable = false
        it.searchEnabled = false
        it.searchQuery = null
        it.onSearchQueryChange = { }
        it.onSearch = { }
        it.searchPlaceholderText = null
        it.searchPillContent = null
    }
}

/**
 * Extended helper for tabs that need search support in the shared top bar.
 * Sets title, actions, and search-related fields.
 *
 * The shared [SharedTopBarState.searchQuery] is the single source of truth for
 * the search text. The [onSearchQueryChange] callback is wrapped so it also
 * updates the shared state immediately — this avoids a double-state lag where
 * the SearchToolbar reads a stale value for one frame (which caused the cursor
 * to jump to the start after the first keystroke).
 */
@Composable
fun useSharedTopBarWithSearch(
    title: String,
    actions: ImmutableList<AppBar.AppBarAction> = persistentListOf(),
    navigateUp: (() -> Unit)? = null,
    searchEnabled: Boolean = false,
    searchQuery: String? = null,
    onSearchQueryChange: (String?) -> Unit = {},
    onSearch: (String) -> Unit = {},
    searchPlaceholderText: String? = null,
    searchPillContent: (@Composable () -> Unit)? = null,
) {
    val state = LocalSharedTopBar.current
    state?.let {
        it.title = title
        it.actions = actions
        it.navigateUp = navigateUp
        it.searchAvailable = true
        it.searchEnabled = searchEnabled
        // Only sync from external source if different (avoids feedback loop)
        if (it.searchQuery != searchQuery) {
            it.searchQuery = searchQuery
        }
        // Wrap the callback so the shared state updates synchronously.
        it.onSearchQueryChange = { query ->
            it.searchQuery = query
            onSearchQueryChange(query)
        }
        it.onSearch = onSearch
        it.searchPlaceholderText = searchPlaceholderText
        // Always update pill content — null clears any previous tab's pill
        it.searchPillContent = searchPillContent
    }
}
