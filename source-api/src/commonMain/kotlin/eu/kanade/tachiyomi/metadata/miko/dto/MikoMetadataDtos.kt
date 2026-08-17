package eu.kanade.tachiyomi.metadata.miko.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MikoManifest(
    val id: String,
    val name: String,
    val version: String? = null,
    val description: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    val idPrefixes: List<String>? = null,
    val catalogs: List<MikoCatalogDescriptor> = emptyList(),
)

@Serializable
data class MikoCatalogDescriptor(
    val type: String,
    val id: String,
    val name: String? = null,
    val genres: List<String> = emptyList(),
    val extra: List<MikoCatalogExtra> = emptyList(),
    @SerialName("extraSupported") val extraSupported: List<String> = emptyList(),
    @SerialName("extraRequired") val extraRequired: List<String> = emptyList(),
)

@Serializable
data class MikoCatalogExtra(
    val name: String,
    val options: List<String> = emptyList(),
    @SerialName("isRequired") val isRequired: Boolean = false,
    @SerialName("optionsLimit") val optionsLimit: Int? = null,
)

@Serializable
data class MikoCatalogResult(
    val metas: List<MikoMetaShort> = emptyList(),
)

@Serializable
data class MikoMetaShort(
    val id: String,
    val name: String,
    val type: String,
    val poster: String? = null,
    val background: String? = null,
    val releaseInfo: String? = null,
    val description: String? = null,
    val genres: List<String>? = null,
    val imdbRating: String? = null,
)

@Serializable
data class MikoMetaResponse(
    val meta: MikoMeta,
)

@Serializable
data class MikoMeta(
    val id: String,
    val name: String,
    val type: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val year: String? = null,
    val released: String? = null,
    val runtime: String? = null,
    @kotlinx.serialization.json.JsonNames("genre", "genres")
    val genres: List<String>? = null,
    val imdbRating: String? = null,
    val director: List<String>? = null,
    val writer: List<String>? = null,
    val cast: List<String>? = null,
    val country: String? = null,
    val awards: String? = null,
    val videos: List<MikoEpisode>? = null,
)

@Serializable
data class MikoEpisode(
    val id: String,
    val name: String,
    val season: Int = 0,
    val number: Int = 0,
    val episode: Int = 0,
    val firstAired: String? = null,
    val released: String? = null,
    val thumbnail: String? = null,
    val overview: String? = null,
    val description: String? = null,
    val rating: String? = null,
)
