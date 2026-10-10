package tachiyomi.data.source

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.source.CatalogueSource

internal actual fun CatalogueSource.isOnlineSource(): Boolean = true

internal actual fun AnimeCatalogueSource.isOnlineSource(): Boolean = true

internal actual fun NovelCatalogueSource.isOnlineSource(): Boolean = true
