package miko.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Minimal observable back-stack navigator shared by Android and iOS shells.
 *
 * The stack is a [SnapshotStateList], so composables reading [current] recompose
 * automatically on push/pop. Shells render [current] through their own dispatch
 * (a `when` over [Destination]) — the navigator intentionally knows nothing
 * about which screen each destination maps to.
 */
class Navigator(start: Destination) {

    private val backStack: SnapshotStateList<Destination> = mutableStateListOf(start)

    /** The destination currently rendered. */
    val current: Destination
        get() = backStack.last()

    /** Snapshot of the stack bottom-to-top, for rendering transitions/chrome. */
    val stack: List<Destination>
        get() = backStack.toList()

    val canPop: Boolean
        get() = backStack.size > 1

    fun push(destination: Destination) {
        backStack += destination
    }

    /** Pops the top destination. Returns false when already at the root. */
    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    /** Replaces the current destination without growing the stack. */
    fun replace(destination: Destination) {
        backStack[backStack.lastIndex] = destination
    }

    /** Pops back to the first destination matching [predicate]; no-op if absent. */
    fun popUntil(predicate: (Destination) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last())) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}

@Composable
fun rememberNavigator(start: Destination): Navigator {
    return remember { Navigator(start) }
}
