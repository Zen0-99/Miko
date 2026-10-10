package tachiyomi.domain.collection.model

import java.io.Serializable

data class Collection(
    val id: Long,
    val name: String,
    val order: Long,
    val flags: Long,
    val hidden: Boolean,
) : Serializable {

    val isSystemCollection: Boolean = id == UNCATEGORIZED_ID

    companion object {
        const val UNCATEGORIZED_ID = 0L
    }
}
