package miko.jssource

import java.net.URI
import java.util.Base64

actual fun platformResolveUrl(input: String, base: String?): String {
    val inputValue = input.trim()
    val baseValue = base?.trim().orEmpty()
    return runCatching {
        val inputUri = URI(inputValue)
        if (inputUri.isAbsolute) return inputUri.toString()
        if (baseValue.isBlank()) return inputValue
        val baseUri = if (baseValue.isNotBlank()) {
            ensureResolvableBaseUri(URI(baseValue))
        } else {
            URI("")
        }
        baseUri.resolve(inputUri).toString()
    }.getOrElse { inputValue }
}

private fun ensureResolvableBaseUri(baseUri: URI): URI {
    if (!baseUri.isAbsolute || !baseUri.path.isNullOrEmpty()) {
        return baseUri
    }
    return URI(
        baseUri.scheme,
        baseUri.authority,
        "/",
        baseUri.query,
        baseUri.fragment,
    )
}

actual fun platformGetPathname(url: String): String {
    val value = url.trim()
    return runCatching { URI(value).path ?: "" }.getOrDefault("")
}

actual fun platformBase64Encode(bytes: ByteArray): String =
    Base64.getEncoder().encodeToString(bytes)

actual fun platformBase64Decode(b64: String): ByteArray =
    Base64.getDecoder().decode(b64)
