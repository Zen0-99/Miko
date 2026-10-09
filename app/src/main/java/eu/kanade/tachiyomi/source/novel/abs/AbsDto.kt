package eu.kanade.tachiyomi.source.novel.abs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Lenient DTO set for the Audiobookshelf REST API (RESEARCH §3.1).
 *
 * All parsing goes through [absJson] — a dedicated Json instance, NEVER
 * the Injekt one — so a hostile or newer server can't poison app-wide
 * decoding and unknown fields across ABS versions are ignored
 * (T-22-01-04). Numeric fields are defaulted so a partial payload
 * degrades to an empty list instead of a crash.
 */
internal val absJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** `GET /api/me` */
@Serializable
data class AbsUser(
    val id: String = "",
    val username: String = "",
    val type: String = "",
)

/** `GET /api/libraries` */
@Serializable
data class AbsLibrariesResponse(
    val libraries: List<AbsLibrary> = emptyList(),
)

@Serializable
data class AbsLibrary(
    val id: String = "",
    val name: String = "",
    /** "book" | "podcast" — podcast libraries are filtered out. */
    val mediaType: String = "",
    val folders: List<JsonElement> = emptyList(),
)

/** `GET /api/libraries/{id}/items` — page 0-indexed. */
@Serializable
data class AbsItemsResponse(
    val results: List<AbsItem> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val page: Int = 0,
)

/** `GET /api/me/items-in-progress` */
@Serializable
data class AbsItemsInProgressResponse(
    val libraryItems: List<AbsItem> = emptyList(),
)

@Serializable
data class AbsItem(
    val id: String = "",
    val ino: String = "",
    val libraryId: String = "",
    val path: String = "",
    val mediaType: String = "",
    val media: AbsMedia? = null,
    /** Epoch ms the item was added to the library. */
    val addedAt: Long = 0L,
    val numFiles: Int = 0,
)

@Serializable
data class AbsMedia(
    val metadata: AbsMetadata? = null,
    val tracks: List<AbsTrack> = emptyList(),
    val audioFiles: List<AbsAudioFile> = emptyList(),
    /** Book-global chapter marks (start/end seconds across files). */
    val chapters: List<AbsChapter> = emptyList(),
    val duration: Double = 0.0,
    val numTracks: Int = 0,
    val numChapters: Int = 0,
)

@Serializable
data class AbsMetadata(
    val title: String? = null,
    val authorName: String? = null,
    val narratorName: String? = null,
    val seriesName: String? = null,
    val publishedYear: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val isbn: String? = null,
    val asin: String? = null,
)

@Serializable
data class AbsTrack(
    val index: Int = 0,
    val ino: String = "",
    val startOffset: Double = 0.0,
    val duration: Double = 0.0,
    val title: String? = null,
    val filename: String? = null,
    val relPath: String? = null,
    val contentUrl: String? = null,
    val mimeType: String? = null,
)

@Serializable
data class AbsAudioFile(
    val ino: String = "",
    val index: Int = 0,
    val duration: Double = 0.0,
    val filename: String? = null,
)

@Serializable
data class AbsChapter(
    val id: Int = 0,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val title: String? = null,
)
