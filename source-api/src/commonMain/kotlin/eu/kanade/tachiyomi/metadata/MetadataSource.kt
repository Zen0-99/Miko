package eu.kanade.tachiyomi.metadata

import eu.kanade.tachiyomi.metadata.miko.dto.MikoCatalogResult
import eu.kanade.tachiyomi.metadata.miko.dto.MikoManifest
import eu.kanade.tachiyomi.metadata.miko.dto.MikoMeta

interface MetadataSource {
    val id: Long
    val name: String

    /** Content types this source supports, e.g. ["movie", "series"]. */
    val types: List<String>

    /** Fetch the addon manifest (catalog list, capabilities). */
    suspend fun getManifest(): MikoManifest

    /** Fetch a page of a catalog. skip = pagination offset (not page numbers). */
    suspend fun getCatalog(type: String, catalogId: String, skip: Int = 0, genre: String? = null): MikoCatalogResult

    /** Search across a catalog by title. */
    suspend fun search(type: String, query: String, skip: Int = 0): MikoCatalogResult

    /** Fetch full metadata for a single item. */
    suspend fun getMeta(type: String, id: String): MikoMeta
}
