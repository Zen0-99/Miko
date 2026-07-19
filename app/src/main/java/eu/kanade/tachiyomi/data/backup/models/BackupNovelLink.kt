package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Backup model for a novel source link.
 *
 * Linked sources cluster novel entries from different sources (e.g. APK
 * extension and JS plugin for the same site). This allows backups to be
 * reverse-compatible — restoring on a JS-only app recovers JS entries,
 * restoring on an APK app recovers APK entries, and restoring on an app
 * with both recovers the full linked cluster.
 *
 * ProtoNumber 700+ to avoid collision with existing backup fields.
 */
@Serializable
data class BackupNovelLink(
    @ProtoNumber(1) var linkedId: Long,
    @ProtoNumber(2) var novelId: Long,
    @ProtoNumber(3) var sourceId: Long,
    @ProtoNumber(4) var isPrimary: Boolean = false,
    @ProtoNumber(5) var extensionType: String = "apk",
    @ProtoNumber(6) var novelUrl: String = "",
    @ProtoNumber(7) var novelTitle: String = "",
    @ProtoNumber(8) var priority: Long = 0,
)
