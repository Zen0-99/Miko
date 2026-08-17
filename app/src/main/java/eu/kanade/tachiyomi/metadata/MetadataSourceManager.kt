package eu.kanade.tachiyomi.metadata

class MetadataSourceManager(
    val sources: List<MetadataSource>,
) {
    fun get(id: Long): MetadataSource? = sources.firstOrNull { it.id == id }
    val cinemeta: MetadataSource get() = sources.first { it.name == "Cinemeta" }
}
