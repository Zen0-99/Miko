package tachiyomi.domain.items.episode.model

data class Episode(
    val id: Long,
    val animeId: Long,
    val seen: Boolean,
    val bookmark: Boolean,
    val fillermark: Boolean,
    val lastSecondSeen: Long,
    val totalSeconds: Long,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val episodeNumber: Double,
    val scanlator: String?,
    val summary: String?,
    val previewUrl: String?,
    val lastModifiedAt: Long,
    val version: Long,
    /** Canonical episode kind (19-04): 'main'|'special'|'nc'; null
     *  until canonical hydration binds the row. */
    val episodeType: String? = null,
    /** Feed-asserted filler flag (canonical data, not user state) —
     *  the UI reads `fillermark || canonicalFiller`. */
    val canonicalFiller: Boolean? = null,
) {
    val isRecognizedNumber: Boolean
        get() = episodeNumber >= 0f

    fun copyFrom(other: Episode): Episode {
        return copy(
            name = other.name,
            url = other.url,
            dateUpload = other.dateUpload,
            episodeNumber = other.episodeNumber,
            fillermark = other.fillermark,
            scanlator = other.scanlator?.ifBlank { null },
            summary = other.summary?.ifBlank { null },
            previewUrl = other.previewUrl?.ifBlank { null },
        )
    }

    companion object {
        fun create() = Episode(
            id = -1,
            animeId = -1,
            seen = false,
            bookmark = false,
            fillermark = false,
            lastSecondSeen = 0,
            totalSeconds = 0,
            dateFetch = 0,
            sourceOrder = 0,
            url = "",
            name = "",
            dateUpload = -1,
            episodeNumber = -1.0,
            scanlator = null,
            summary = null,
            previewUrl = null,
            lastModifiedAt = 0,
            version = 1,
        )
    }
}
