package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * One local carrier exclusion (Phase 20-06) — (work, provider) removes
 * ride the backup so a user's "never this source on this entry" decision
 * survives reinstall. `providerRef` is kept so a later restore can still
 * retract the server-side signal.
 */
@Serializable
class BackupCarrierExclusion(
    @ProtoNumber(1) var workId: String,
    @ProtoNumber(2) var provider: String,
    @ProtoNumber(3) var providerRef: String? = null,
    @ProtoNumber(4) var createdAt: Long = 0,
)

val backupCarrierExclusionMapper = { row: data.Carrier_exclusions ->
    BackupCarrierExclusion(
        workId = row.work_id,
        provider = row.provider,
        providerRef = row.provider_ref,
        createdAt = row.created_at,
    )
}
