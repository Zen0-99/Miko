package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.aniyomi.AYMR

/**
 * The currently active content type shown across mode-aware tabs (Library, Updates, History).
 *
 * One global mode is shared by all mode-aware tabs so the user sees a single content type
 * at a time. Browse stays aggregated (cross-type by nature) but its title reflects the
 * current mode.
 */
enum class ContentMode(val titleRes: StringResource) {
    ANIME(AYMR.strings.label_anime),
    MANGA(AYMR.strings.label_manga),
    NOVEL(AYMR.strings.label_novel),
    ;

    /** Index in [carouselOrder]; used to compute peeking neighbors with wrap-around. */
    val carouselIndex: Int get() = carouselOrder.indexOf(this)

    /** The next mode to the right in the carousel (wraps around). */
    fun next(): ContentMode = carouselOrder[(carouselIndex + 1) % carouselOrder.size]

    /** The previous mode to the left in the carousel (wraps around). */
    fun previous(): ContentMode =
        carouselOrder[(carouselIndex - 1 + carouselOrder.size) % carouselOrder.size]

    companion object {
        /** Circular order used by the swipeable title carousel. */
        val carouselOrder: List<ContentMode> = listOf(MANGA, ANIME, NOVEL)

        /**
         * Returns the carousel order filtered to only include [visible] modes.
         * Preserves the original [carouselOrder] ordering. Falls back to [MANGA] if
         * the visible set is empty (shouldn't happen — [UiPreferences.visibleContentModes]
         * guarantees at least one).
         */
        fun carouselOrderFor(visible: Set<ContentMode>): List<ContentMode> {
            val filtered = carouselOrder.filter { it in visible }
            return filtered.ifEmpty { listOf(MANGA) }
        }
    }
}
