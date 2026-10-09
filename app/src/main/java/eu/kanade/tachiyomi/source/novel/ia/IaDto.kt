package eu.kanade.tachiyomi.source.novel.ia

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Lenient DTO set for the Internet Archive advancedsearch + metadata
 * APIs (RESEARCH §3.3).
 *
 * IA metadata fields are polymorphic across items — `creator`,
 * `subject`, `language`, `description`, `year`, `track`, `length` all
 * arrive as either a scalar or an array depending on how many values the
 * uploader attached — so those stay [JsonElement] and coerce through
 * [asStringList]. All parsing goes through [iaJson], never the Injekt
 * Json (T-22-01-04 convention).
 */
internal val iaJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

/** `GET /advancedsearch.php?…&output=json` envelope. */
@Serializable
data class IaSearchResponse(
    val response: IaSearchBody = IaSearchBody(),
)

@Serializable
data class IaSearchBody(
    val numFound: Int = 0,
    val start: Int = 0,
    val docs: List<IaDoc> = emptyList(),
)

@Serializable
data class IaDoc(
    val identifier: String = "",
    val title: String = "",
    val creator: JsonElement? = null,
    val date: String? = null,
    val year: JsonElement? = null,
    val description: JsonElement? = null,
    val subject: JsonElement? = null,
    val language: JsonElement? = null,
    val downloads: Long = 0L,
    val avg_rating: JsonElement? = null,
    /** Real API shape — `fl[]=external-identifier` returns hyphenated. */
    @SerialName("external-identifier") val externalIdentifier: JsonElement? = null,
    /** Tolerated underscore variant seen in some responses. */
    @SerialName("external_identifier") val externalIdentifierAlt: JsonElement? = null,
    @SerialName("identifier-access") val identifierAccess: JsonElement? = null,
) {
    /** Dedup gate 1 — a doc carrying a librivox.org back-link belongs to
     *  LibriVoxSource, not here (SD-7). */
    fun hasLibrivoxBacklink(): Boolean =
        librivoxBacklinkPresent(externalIdentifier, externalIdentifierAlt, identifierAccess)
}

/** `GET /metadata/{identifier}` envelope. */
@Serializable
data class IaMetadataResponse(
    val metadata: IaMetadata = IaMetadata(),
    val files: List<IaFile> = emptyList(),
)

@Serializable
data class IaMetadata(
    val identifier: String = "",
    val title: String = "",
    val creator: JsonElement? = null,
    val description: JsonElement? = null,
    val subject: JsonElement? = null,
    val language: JsonElement? = null,
    @SerialName("external-identifier") val externalIdentifier: JsonElement? = null,
    @SerialName("external_identifier") val externalIdentifierAlt: JsonElement? = null,
    @SerialName("identifier-access") val identifierAccess: JsonElement? = null,
) {
    /** Dedup gate 2 (SD-7) — checked at detail/chapter-list time too. */
    fun hasLibrivoxBacklink(): Boolean =
        librivoxBacklinkPresent(externalIdentifier, externalIdentifierAlt, identifierAccess)
}

@Serializable
data class IaFile(
    val name: String = "",
    val source: String? = null,
    val format: String? = null,
    val length: JsonElement? = null,
    val size: JsonElement? = null,
    val bitrate: JsonElement? = null,
    val title: String? = null,
    /** "1" | "1/12" | number — ordering hint, coerce at use site. */
    val track: JsonElement? = null,
)

/** Scalar-or-array coercion for IA's polymorphic metadata fields. */
internal fun JsonElement?.asStringList(): List<String> = when (this) {
    null, JsonNull -> emptyList()
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> contentOrNull?.let { listOf(it) } ?: emptyList()
    else -> emptyList()
}

/** First coerced value — for scalar-ish fields. */
internal fun JsonElement?.firstString(): String? = asStringList().firstOrNull()

/** True when any field's coerced values mention librivox — the back-link
 *  an IA copy of a LibriVox recording carries (SD-7). */
internal fun librivoxBacklinkPresent(vararg fields: JsonElement?): Boolean =
    fields.any { field ->
        field.asStringList().any { it.contains("librivox", ignoreCase = true) }
    }
