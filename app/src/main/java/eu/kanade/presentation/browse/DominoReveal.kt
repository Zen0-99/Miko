package eu.kanade.presentation.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

private const val DOMINO_DELAY_MS = 80L
private const val DOMINO_DELAY_MANY_MS = 40L
private const val DOMINO_MANY_THRESHOLD = 10

/**
 * Keys this process has already revealed, keyed `scope:persistKey`.
 * Process-scoped on purpose: a discovery view dominoes in once per
 * cold boot; re-entering it (back nav, tab hop, config change) must
 * land instantly instead of replaying the cascade.
 */
private val SHOWN: MutableSet<String> = ConcurrentHashMap.newKeySet()

class DominoRevealState<K> internal constructor(
    internal val scope: String,
    internal val persistKey: (K) -> String,
    internal val revealed: SnapshotStateMap<K, Boolean>,
) {
    internal val requests = Channel<K>(Channel.UNLIMITED)

    /** Reveal requests in flight — drain rate adapts to the backlog. */
    internal var pending = 0

    /**
     * Called when a cell enters composition. Previously-shown keys mark
     * revealed synchronously (no animation); new keys queue for the
     * staggered reveal — so both the first page AND items scrolling in
     * later (pagination, deeper rows) get their own drop-in.
     */
    fun requestReveal(key: K) {
        if (revealed[key] == true) return
        if ("$scope:${persistKey(key)}" in SHOWN) {
            revealed[key] = true
        } else {
            pending++
            requests.trySend(key)
        }
    }

    /** Per-cell accessor: registers this cell for reveal on composition
     *  and returns whether it should currently draw. Keys this process
     *  already revealed must read true on the FIRST frame — otherwise
     *  re-entering a seen view composes every cell invisible and the
     *  effect-driven reveal replays the whole cascade at once. */
    @Composable
    fun isVisible(key: K): Boolean {
        LaunchedEffect(key) { requestReveal(key) }
        return revealed[key] == true || "$scope:${persistKey(key)}" in SHOWN
    }
}

/**
 * Domino-style staggered reveal for keyed grids. The first composition
 * of each cell queues a reveal (~80ms cadence, ~40ms under backlog) —
 * cells already shown this process-resolve instantly.
 *
 * Usage inside a LazyVerticalGrid item block:
 *   val domino = rememberDominoReveal(scope = "catalog:$medium")
 *   items(items.size, key = { items[it].id }) { i ->
 *       DominoRevealItem(visible = domino.isVisible(items[i].id)) { ... }
 *   }
 */
@Composable
fun <K> rememberDominoReveal(
    scope: String,
    persistKey: (K) -> String = { it.toString() },
): DominoRevealState<K> {
    val state = remember(scope) {
        DominoRevealState(scope, persistKey, mutableStateMapOf())
    }
    LaunchedEffect(state) {
        for (key in state.requests) {
            state.pending--
            if (state.revealed[key] == true) continue
            delay(
                if (state.pending > DOMINO_MANY_THRESHOLD) {
                    DOMINO_DELAY_MANY_MS
                } else {
                    DOMINO_DELAY_MS
                },
            )
            state.revealed[key] = true
            SHOWN += "$scope:${state.persistKey(key)}"
        }
    }
    return state
}

/** Wrap one grid cell — invisible until [visible], then fade+drop-in. */
@Composable
fun DominoRevealItem(
    visible: Boolean,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 300)) +
            slideInVertically(
                animationSpec = tween(durationMillis = 300),
                initialOffsetY = { -it / 2 },
            ),
    ) {
        content()
    }
}
