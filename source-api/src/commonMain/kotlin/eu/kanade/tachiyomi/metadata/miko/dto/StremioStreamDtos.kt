package eu.kanade.tachiyomi.metadata.miko.dto

import kotlinx.serialization.Serializable

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStreamDto> = emptyList(),
)

@Serializable
data class StremioStreamDto(
    val name: String,
    val title: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val behaviorHints: StremioBehaviorHints? = null,
    val sources: List<String>? = null,
) {
    val filename: String?
        get() = behaviorHints?.filename

    val bingeGroup: String?
        get() = behaviorHints?.bingeGroup
}

@Serializable
data class StremioBehaviorHints(
    val filename: String? = null,
    val bingeGroup: String? = null,
)
