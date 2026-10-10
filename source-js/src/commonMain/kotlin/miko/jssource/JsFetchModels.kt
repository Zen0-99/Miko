package miko.jssource

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * JSON wire models for the `__native.fetch*` bridge. Ported verbatim from
 * NovelJsRuntimeFactory — the shapes are part of the plugin contract.
 */
@Serializable
data class JsFetchRequest(
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val bodyType: BodyType = BodyType.None,
    val body: String? = null,
    val formEntries: List<FormEntry>? = null,
    val referrer: String? = null,
    val origin: String? = null,
)

@Serializable
data class FormEntry(
    val key: String,
    val value: String,
)

@Serializable
enum class BodyType {
    @SerialName("none")
    None,

    @SerialName("text")
    Text,

    @SerialName("form")
    Form,
}

@Serializable
data class JsFetchResponse(
    val status: Int,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val bodyBase64: String? = null,
)

@Serializable
data class JsProtoFetchConfig(
    val proto: String? = null,
    val requestType: String,
    val responseType: String,
    val requestData: JsonElement = JsonObject(emptyMap()),
)

@Serializable
data class JsDomNode(
    val html: String,
    val text: String,
    val attrs: Map<String, String>,
)
