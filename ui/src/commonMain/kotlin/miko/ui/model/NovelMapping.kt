package miko.ui.model

import eu.kanade.tachiyomi.novelsource.model.SNovel
import tachiyomi.domain.entries.novel.model.Novel

/**
 * SNovel <-> domain Novel mapping for the shared screens. Mirrors the app's
 * `eu.kanade.domain.entries.novel.model` converters minus the Android-only
 * pieces (Injekt-bound prefs, cover cache, audio-scheme carrier flags —
 * JS plugin sources never emit `abs://`-family URLs).
 */
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
