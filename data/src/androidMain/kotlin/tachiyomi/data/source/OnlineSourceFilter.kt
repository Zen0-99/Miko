package tachiyomi.data.source

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource

internal actual fun CatalogueSource.isOnlineSource(): Boolean = this is HttpSource

internal actual fun AnimeCatalogueSource.isOnlineSource(): Boolean = this is AnimeHttpSource

internal actual fun NovelCatalogueSource.isOnlineSource(): Boolean = this is NovelHttpSource
