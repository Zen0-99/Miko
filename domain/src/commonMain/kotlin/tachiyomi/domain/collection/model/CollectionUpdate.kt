package tachiyomi.domain.collection.model

data class CollectionUpdate(
    val id: Long,
    val name: String? = null,
    val order: Long? = null,
    val flags: Long? = null,
    val hidden: Boolean? = null,
)
