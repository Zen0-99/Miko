package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BackupNovelSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var sourceId: Long,
    @ProtoNumber(3) var baseUrl: String = "",
)
