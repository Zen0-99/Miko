package tachiyomi.data.source

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource

/**
 * Whether a catalogue source fetches content over the network.
 * On Android this is the `*HttpSource` subtype check; on iOS every
 * catalogue source is online-capable.
 */
internal expect fun CatalogueSource.isOnlineSource(): Boolean

internal expect fun AnimeCatalogueSource.isOnlineSource(): Boolean

internal expect fun NovelCatalogueSource.isOnlineSource(): Boolean
