package eu.kanade.tachiyomi.source

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 *
 * KMP: expect interfaces cannot declare members with bodies, so this
 * declares only the members that are abstract on every platform. The
 * rest of the contract — suspend `get*` calls, `lang`, the RxJava
 * `fetch*` methods and their bridge defaults (extensions-lib ABI on
 * Android) — live as extra members on each platform actual.
 */
expect interface MangaSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String
}
