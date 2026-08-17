package eu.kanade.tachiyomi.metadata.cinemeta

import eu.kanade.tachiyomi.metadata.MetadataSource
import eu.kanade.tachiyomi.metadata.miko.MikoAddonClient
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.serialization.json.Json

class CinemetaMetadataSource(
    networkHelper: NetworkHelper,
    json: Json,
) : MetadataSource by MikoAddonClient(
    manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
    networkHelper = networkHelper,
    json = json,
)
