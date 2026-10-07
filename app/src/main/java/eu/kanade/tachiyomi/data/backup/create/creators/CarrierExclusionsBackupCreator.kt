package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.models.BackupCarrierExclusion
import eu.kanade.tachiyomi.data.backup.models.backupCarrierExclusionMapper
import eu.kanade.tachiyomi.data.mikodb.CarrierExclusions
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CarrierExclusionsBackupCreator(
    private val carrierExclusions: CarrierExclusions = Injekt.get(),
) {

    suspend operator fun invoke(): List<BackupCarrierExclusion> {
        return carrierExclusions.all().map(backupCarrierExclusionMapper)
    }
}
