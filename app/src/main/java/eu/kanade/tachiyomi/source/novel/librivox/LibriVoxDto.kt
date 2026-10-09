package eu.kanade.tachiyomi.source.novel.librivox

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Lenient DTO set for the LibriVox `api/feed/audiobooks` endpoint
 * (RESEARCH §3.2). The feed returns every scalar as a JSON string —
 * ids, counts, seconds — so the fields stay String-typed and parse at
 * use sites. `extended=1` adds `sections`/`readers`/`genres`.
 *
 * All parsing goes through [lvJson] — a dedicated Json instance, never
 * the Injekt one — matching the absJson convention (T-22-01-04).
 */
internal val lvJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** `GET /api/feed/audiobooks?…&extended=1` envelope. */
@Serializable
data class LvFeedResponse(
    val books: List<LvBook> = emptyList(),
)

@Serializable
data class LvBook(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val url_librivox: String = "",
    val url_iarchive: String = "",
    val url_rss: String = "",
    val language: String = "",
    val copyright_year: String = "",
    val num_sections: String = "",
    val totaltime: String = "",
    val totaltimesecs: String = "",
    val authors: List<LvAuthor> = emptyList(),
    val coverart_jpg: String = "",
    val coverart_thumbnail: String = "",
    val coverart_medium: String = "",
    val sections: List<LvSection> = emptyList(),
    val readers: List<LvReader> = emptyList(),
    val genres: List<LvGenre> = emptyList(),
)

@Serializable
data class LvAuthor(
    val id: String = "",
    val first_name: String = "",
    val last_name: String = "",
) {
    fun displayName(): String = "$first_name $last_name".trim()
}

/** `sections[]` — one playable audio segment. `listen_url` on complete
 *  projects is a stable `archive.org/download/{iaId}/{file}` URL. */
@Serializable
data class LvSection(
    val id: String = "",
    val section_number: String = "",
    val title: String = "",
    val listen_url: String? = null,
    val playtime: String = "",
    val file_name: String = "",
)

@Serializable
data class LvReader(
    val id: String = "",
    val first_name: String = "",
    val last_name: String = "",
) {
    fun displayName(): String = "$first_name $last_name".trim()
}

@Serializable
data class LvGenre(
    val id: String = "",
    val name: String = "",
)
