package mihon.domain.extensionrepo.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Protobuf DTOs for the index.pb format used by extension repos (e.g. keiyoushi).
 * Schema based on tachiyomix index.proto.
 */
@Serializable
data class IndexProto(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val badgeLabel: String = "",
    @ProtoNumber(3) val signingKey: String = "",
    @ProtoNumber(4) val contact: ContactProto? = null,
    @ProtoNumber(101) val extensionList: ExtensionListProto? = null,
    @ProtoNumber(102) val extensionListUrl: String = "",
)

@Serializable
data class ContactProto(
    @ProtoNumber(1) val website: String = "",
    @ProtoNumber(2) val discord: String = "",
)

@Serializable
data class ExtensionListProto(
    @ProtoNumber(1) val extensions: List<ExtensionProto> = emptyList(),
)

@Serializable
data class ExtensionProto(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val packageName: String = "",
    @ProtoNumber(3) val resources: ResourcesProto? = null,
    @ProtoNumber(4) val extensionLib: String = "",
    @ProtoNumber(5) val versionCode: Long = 0,
    @ProtoNumber(6) val versionName: String = "",
    @ProtoNumber(7) val contentWarning: Int = 0,
    @ProtoNumber(8) val sources: List<SourceProto> = emptyList(),
)

@Serializable
data class ResourcesProto(
    @ProtoNumber(1) val apkUrl: String = "",
    @ProtoNumber(2) val iconUrl: String = "",
)

@Serializable
data class SourceProto(
    @ProtoNumber(1) val id: Long = 0,
    @ProtoNumber(2) val name: String = "",
    @ProtoNumber(3) val language: String = "",
    @ProtoNumber(4) val homeUrl: String = "",
    @ProtoNumber(5) val mirrorUrls: List<String> = emptyList(),
    @ProtoNumber(7) val message: String = "",
)

/**
 * Normalized extension entry from either JSON or protobuf index.
 * Used by the extension APIs to convert to their specific Available models.
 */
data class ExtensionIndexEntry(
    val name: String,
    val pkgName: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val lang: String,
    val isNsfw: Boolean,
    val isTorrent: Boolean = false,
    val sources: List<ExtensionIndexSource>,
    val apkName: String,
    val iconUrl: String,
    val repoUrl: String,
)

data class ExtensionIndexSource(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

/**
 * Converts a protobuf Index to normalized entries.
 */
internal fun IndexProto.toEntries(repoUrl: String): List<ExtensionIndexEntry> {
    val extensions = extensionList?.extensions ?: return emptyList()
    return extensions.map { ext ->
        val resources = ext.resources
        val apkUrl = resources?.apkUrl ?: ""
        val iconUrl = resources?.iconUrl ?: "$repoUrl/icon/${ext.packageName}.png"
        val apkName = apkUrl.substringAfterLast("/")
        ExtensionIndexEntry(
            name = ext.name.substringAfter("Tachiyomi: ").substringAfter("Aniyomi: "),
            pkgName = ext.packageName,
            versionName = ext.versionName,
            versionCode = ext.versionCode,
            libVersion = ext.extensionLib.substringBeforeLast('.').toDoubleOrNull() ?: 0.0,
            lang = ext.sources.firstOrNull()?.language ?: "",
            isNsfw = ext.contentWarning == 3, // CONTENT_WARNING_NSFW
            sources = ext.sources.map { src ->
                ExtensionIndexSource(
                    id = src.id,
                    lang = src.language,
                    name = src.name,
                    baseUrl = src.homeUrl,
                )
            },
            apkName = apkName,
            iconUrl = iconUrl,
            repoUrl = repoUrl,
        )
    }
}

// JSON DTOs for the legacy index.min.json format

@Serializable
internal data class ExtensionRepoExtensionJsonDto(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val torrent: Int = 0,
    val sources: List<ExtensionRepoSourceJsonDto>? = null,
)

@Serializable
internal data class ExtensionRepoSourceJsonDto(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

internal fun List<ExtensionRepoExtensionJsonDto>.toEntries(repoUrl: String): List<ExtensionIndexEntry> {
    return this.map {
        ExtensionIndexEntry(
            name = it.name.substringAfter("Tachiyomi: ").substringAfter("Aniyomi: "),
            pkgName = it.pkg,
            versionName = it.version,
            versionCode = it.code,
            libVersion = it.version.substringBeforeLast('.').toDoubleOrNull() ?: 0.0,
            lang = it.lang,
            isNsfw = it.nsfw == 1,
            isTorrent = it.torrent == 1,
            sources = it.sources?.map { src ->
                ExtensionIndexSource(
                    id = src.id,
                    lang = src.lang,
                    name = src.name,
                    baseUrl = src.baseUrl,
                )
            } ?: emptyList(),
            apkName = it.apk,
            iconUrl = "$repoUrl/icon/${it.pkg}.png",
            repoUrl = repoUrl,
        )
    }
}

