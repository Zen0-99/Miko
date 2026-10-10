package tachiyomi.domain.extension.novel.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.domain.extension.novel.model.NovelPlugin
import tachiyomi.domain.extension.novel.normalizeNovelLang

/**
 * Portable port of the app's `NovelPluginIndexParser` — same LNReader-style
 * plugin-index schema, same Kotlin-APK entry support, plus the optional
 * per-entry `kind` field used by multi-type repos like MikoDB.
 *
 * URL resolution is string-based (no OkHttp): absolute `http(s)` refs pass
 * through, `/`-leading refs resolve against scheme://authority, everything
 * else resolves relative to the repo root with `.`/`..` segment handling.
 */
class JsPluginIndexParser(
    private val json: Json,
) {
    fun parse(payload: String, repoUrl: String): List<NovelPlugin.Available> {
        val entries = json.parseToJsonElement(payload).jsonArray
        return entries.mapNotNull { element ->
            (element as? JsonObject)?.toPlugin(repoUrl)
        }
    }
}

private fun JsonObject.toPlugin(repoUrl: String): NovelPlugin.Available? {
    toKotlinExtensionPlugin(repoUrl)?.let { return it }

    val id = stringValue("id") ?: return null
    val name = stringValue("name") ?: return null
    val site = stringValue("site") ?: return null
    val lang = normalizeNovelLang(stringValue("lang"))
    val url = stringValue("url")?.let { resolveIndexRef(repoUrl, it) } ?: return null
    val iconUrl = stringValue("iconUrl")?.let { resolveIndexRef(repoUrl, it) }
    val customJs = stringValue("customJS")?.let { resolveIndexRef(repoUrl, it) }
    val customCss = stringValue("customCSS")?.let { resolveIndexRef(repoUrl, it) }
    val hasSettings = this["hasSettings"]?.jsonPrimitive?.booleanOrNull ?: false
    val sha256 = stringValue("sha256").orEmpty()
    val versionCode = parseVersion(this["version"])
    val rawVersion = stringValue("version")
    val versionName = rawVersion?.takeIf { it.isNotBlank() } ?: versionCode.toString()
    val isNsfw = this["nsfw"].parseNsfwFlag()
    val kind = normalizePluginKind(stringValue("kind"))

    return NovelPlugin.Available(
        id = id,
        name = name,
        site = site,
        lang = lang,
        versionCode = versionCode,
        versionName = versionName,
        url = url,
        iconUrl = iconUrl,
        customJs = customJs,
        customCss = customCss,
        hasSettings = hasSettings,
        sha256 = sha256,
        repoUrl = repoUrl,
        isNsfw = isNsfw,
        kind = kind,
    )
}

private fun JsonObject.toKotlinExtensionPlugin(repoUrl: String): NovelPlugin.Available? {
    if (this["isNovel"]?.jsonPrimitive?.booleanOrNull != true) return null
    val pkgName = stringValue("pkg") ?: return null
    val apkUrl = stringValue("apk")?.let { resolveApkRef(repoUrl, it) } ?: return null
    val rawName = stringValue("name") ?: pkgName
    val name = rawName
        .substringAfter("Tsundoku: ")
        .substringAfter("NovelApp: ")
        .ifBlank { rawName }
    val lang = normalizeNovelLang(stringValue("lang"))
    val versionCode = parseVersion(this["code"] ?: this["version"])
    val versionName = stringValue("version")?.takeIf { it.isNotBlank() } ?: versionCode.toString()
    val site = this["sources"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.stringValue("baseUrl")
        .orEmpty()
    val isNsfw = this["nsfw"].parseNsfwFlag()

    return NovelPlugin.Available(
        id = pkgName,
        name = name,
        site = site,
        lang = lang,
        versionCode = versionCode,
        versionName = versionName,
        url = apkUrl,
        iconUrl = resolveIndexRef(repoUrl, "icon/$pkgName.png"),
        customJs = null,
        customCss = null,
        hasSettings = false,
        sha256 = stringValue("sha256").orEmpty(),
        repoUrl = repoUrl,
        pkgName = pkgName,
        apkUrl = apkUrl,
        isKotlinExtension = true,
        isNsfw = isNsfw,
        kind = "novel",
    )
}

internal fun normalizePluginKind(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "manga" -> "manga"
        "anime", "show", "shows" -> "anime"
        // novel/book/absent/unrecognized — LNReader-compatible default.
        else -> "novel"
    }
}

private val absoluteHttpUrl = Regex("^https?://", RegexOption.IGNORE_CASE)

/**
 * Resolves an index-relative reference against the repo base URL.
 * - absolute `http(s)` refs pass through untouched
 * - `/`-leading refs resolve against `scheme://authority`
 * - other refs join onto the repo root (a `.json`-suffixed repo URL resolves
 *   against its directory), with `.`/`..` segment normalization
 */
internal fun resolveIndexRef(repoUrl: String, ref: String): String? {
    val raw = ref.trim()
    if (raw.isBlank()) return null
    if (absoluteHttpUrl.containsMatchIn(raw)) return raw

    val normalizedRepo = repoUrl.trim()
    val repoRoot = if (normalizedRepo.endsWith(".json", ignoreCase = true)) {
        normalizedRepo.substringBeforeLast('/')
    } else {
        normalizedRepo.trimEnd('/')
    }
    if (!absoluteHttpUrl.containsMatchIn(repoRoot)) return raw

    val joined = if (raw.startsWith("/")) {
        val authorityEnd = repoRoot.indexOf('/', startIndex = repoRoot.indexOf("://") + 3)
        val origin = if (authorityEnd == -1) repoRoot else repoRoot.substring(0, authorityEnd)
        "$origin$raw"
    } else {
        "$repoRoot/$raw"
    }
    return normalizeDotSegments(joined)
}

private fun resolveApkRef(repoUrl: String, ref: String): String? {
    val raw = ref.trim()
    if (raw.isBlank()) return null
    if (absoluteHttpUrl.containsMatchIn(raw)) return raw
    val apkPath = if (raw.startsWith("apk/")) raw else "apk/$raw"
    return resolveIndexRef(repoUrl, apkPath)
}

private fun normalizeDotSegments(url: String): String {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd == -1) return url
    val prefix = url.substring(0, schemeEnd + 3)
    var rest = url.substring(schemeEnd + 3)
    var suffix = ""
    val cut = rest.indexOfFirst { it == '?' || it == '#' }
    if (cut != -1) {
        suffix = rest.substring(cut)
        rest = rest.substring(0, cut)
    }
    val out = ArrayDeque<String>()
    rest.split('/').forEach { segment ->
        when (segment) {
            ".." -> if (out.size > 1) out.removeLast()
            "." -> {}
            else -> out.addLast(segment)
        }
    }
    return prefix + out.joinToString("/") + suffix
}

private fun JsonElement?.parseNsfwFlag(): Boolean {
    val primitive = this?.jsonPrimitive ?: return false
    return when {
        primitive.isString -> {
            val content = primitive.content
            content == "1" || content.equals("true", ignoreCase = true)
        }
        else -> primitive.intOrNull == 1 || primitive.booleanOrNull == true
    }
}

private fun parseVersion(element: JsonElement?): Int {
    val primitive = element?.jsonPrimitive ?: return 0
    if (!primitive.isString) {
        return primitive.content.toIntOrNull() ?: 0
    }
    val raw = primitive.content.trim()
    if (raw.isEmpty()) return 0
    return if (raw.contains('.')) {
        val parts = raw.split('.', '-', '_')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        major * 1_000_000 + minor * 1_000 + patch
    } else {
        raw.toIntOrNull() ?: 0
    }
}

private fun JsonObject.stringValue(key: String): String? {
    return (this[key] as? JsonPrimitive)?.content
}
