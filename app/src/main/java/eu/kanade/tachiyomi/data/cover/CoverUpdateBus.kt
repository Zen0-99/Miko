package eu.kanade.tachiyomi.data.cover

import eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide signal that an entry row's `thumbnailUrl` changed. The
 * discovery catalog collects it to patch its cached feeds + the disk
 * snapshot while the detail screen is still on top — the card then
 * paints the healed art on the pop's first frame (bitmap already in
 * Coil's memory cache) instead of swapping the model after the return
 * animation lands and crossfading visibly.
 *
 * Emitted by the entry update interactors, so every write path —
 * pipeline heal, progress-volume write-back, hi-res upgrade, provider
 * pick — reaches discovery without each site knowing about feeds.
 */
class CoverUpdateBus {

    /** [entryId] = the mangas/novels row id; [workId] = `mk:…` when the
     *  entry is a canonical `miko:` row (bound-card patching only needs
     *  the entry id). [manual] = a human picked this art (pin / unpin /
     *  gallery edit) — always applied. Automatic thumbnail writes may
     *  never demote recognized hi-res art back to extension art, which
     *  happens when a refresh transiently rewrites `thumbnail_url`. */
    data class Update(
        val entryId: Long,
        val workId: String?,
        val coverUrl: String,
        val manual: Boolean = false,
    )

    private val _updates = MutableSharedFlow<Update>(extraBufferCapacity = 64)
    val updates: SharedFlow<Update> = _updates.asSharedFlow()

    fun emit(entryId: Long, entryUrl: String, coverUrl: String, manual: Boolean = false) {
        val workId = CanonicalCatalog.workIdFromMangaUrl(entryUrl)
            ?: CanonicalCatalog.workIdFromNovelUrl(entryUrl)
        _updates.tryEmit(Update(entryId, workId, coverUrl, manual))
    }
}
