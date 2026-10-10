package tachiyomi.domain.savedsearches.model

data class SavedSearch(
    val id: Long,
    val sourceId: Long,
    val name: String,
    val query: String,
    val filtersJson: String,
) {
    companion object {
        fun create(sourceId: Long, name: String, query: String, filtersJson: String) = SavedSearch(
            id = -1L,
            sourceId = sourceId,
            name = name,
            query = query,
            filtersJson = filtersJson,
        )
    }
}
