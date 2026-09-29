package eu.kanade.domain.entries.novel.model

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.novel.model.Novel
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun SNovel.toDomainNovel(sourceId: Long): Novel {
    return Novel.create().copy(
        url = url,
        title = title,
        artist = artist,
        author = author,
        description = description,
        genre = getGenres(),
        status = status.toLong(),
        thumbnailUrl = thumbnail_url,
        updateStrategy = update_strategy,
        initialized = initialized,
        source = sourceId,
    )
}

fun Novel.toSNovel(): SNovel = SNovel.create().also {
    it.url = url
    it.title = title
    it.artist = artist
    it.author = author
    it.description = description
    it.genre = genre.orEmpty().joinToString()
    it.status = status.toInt()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}

fun Novel.copyFrom(other: SNovel): Novel {
    val author = other.author ?: author
    val artist = other.artist ?: artist
    val description = other.description ?: description
    val genres = if (other.genre != null) {
        other.getGenres()
    } else {
        genre
    }
    val thumbnailUrl = other.thumbnail_url ?: thumbnailUrl
    return this.copy(
        author = author,
        artist = artist,
        description = description,
        genre = genres,
        thumbnailUrl = thumbnailUrl,
        status = other.status.toLong(),
        updateStrategy = other.update_strategy,
        initialized = true,
    )
}

fun Novel.chaptersFiltered(): Boolean {
    return unreadFilter != TriState.DISABLED ||
        downloadedFilter != TriState.DISABLED ||
        bookmarkedFilter != TriState.DISABLED
}

val Novel.downloadedFilter: TriState
    get() {
        if (Injekt.get<BasePreferences>().downloadedOnly().get()) return TriState.ENABLED_IS
        return when (downloadedFilterRaw) {
            Novel.CHAPTER_SHOW_DOWNLOADED -> TriState.ENABLED_IS
            Novel.CHAPTER_SHOW_NOT_DOWNLOADED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }
    }

fun Novel.hasCustomCover(coverCache: NovelCoverCache = Injekt.get()): Boolean {
    return coverCache.getCustomCoverFile(id).exists()
}
