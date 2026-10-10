package eu.kanade.tachiyomi.data.coil

import coil3.decode.DataSource
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.transition.CrossfadeTransition
import coil3.transition.Transition
import coil3.transition.TransitionTarget

/**
 * Crossfade only art that genuinely arrived over the network.
 *
 * Coil's stock factory skips the fade for memory hits only — a disk
 * hit still fades from the placeholder. Grids lose their memory entries
 * whenever a detail screen decodes its big hero/backdrop art (the cache
 * is a heap fraction), so popping back re-decodes every visible cell
 * from disk and each one visibly fades out/in. Disk decodes land within
 * a frame or two; painting them directly reads as "already there".
 */
class NetworkOnlyCrossfade(durationMillis: Int) : Transition.Factory {

    private val crossfade = CrossfadeTransition.Factory(durationMillis)

    override fun create(target: TransitionTarget, result: ImageResult): Transition {
        if (result is SuccessResult && result.dataSource != DataSource.NETWORK) {
            return Transition.Factory.NONE.create(target, result)
        }
        return crossfade.create(target, result)
    }
}
