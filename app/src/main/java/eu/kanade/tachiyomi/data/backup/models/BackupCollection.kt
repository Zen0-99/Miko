package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.collection.model.Collection

@Serializable
class BackupCollection(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var flags: Long = 0,
) {
    fun toCollection(id: Long) = Collection(
        id = id,
        name = this@BackupCollection.name,
        flags = this@BackupCollection.flags,
        order = this@BackupCollection.order,
        hidden = false,
    )
}

val backupCollectionMapper = { collection: Collection ->
    BackupCollection(
        id = collection.id,
        name = collection.name,
        order = collection.order,
        flags = collection.flags,
    )
}
