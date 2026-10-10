package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList

/**
 * KMP: see [MangaSource] — expect members must be abstract everywhere,
 * so the suspend `get*` calls and flag defaults live on the actuals.
 */
expect interface CatalogueSource : MangaSource {

    /**
     * An ISO 639-1 compliant language code (two letters in lower case).
     */
    val lang: String

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean

    /**
     * Returns the list of filters for the source.
     */
    fun getFilterList(): FilterList
}
