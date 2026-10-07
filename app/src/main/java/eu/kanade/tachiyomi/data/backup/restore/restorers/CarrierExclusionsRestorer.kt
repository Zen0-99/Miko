package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCarrierExclusion
import eu.kanade.tachiyomi.data.mikodb.CarrierExclusions
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Merge-restores carrier exclusions (Phase 20-06): OR IGNORE, so
 * exclusions the user added AFTER this backup was taken survive the
 * restore — an older file never resurrects a provider the user
 * re-removed.
 */
class CarrierExclusionsRestorer(
    private val carrierExclusions: CarrierExclusions = Injekt.get(),
) {

    suspend operator fun invoke(exclusions: List<BackupCarrierExclusion>) {
        if (exclusions.isEmpty()) return
        carrierExclusions.insertAll(
            exclusions.map {
                data.Carrier_exclusions(
                    work_id = it.workId,
                    provider = it.provider,
                    provider_ref = it.providerRef,
                    created_at = it.createdAt,
                )
            },
        )
    }
}
