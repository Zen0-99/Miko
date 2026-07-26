package eu.kanade.tachiyomi.data.backup.models

import eu.kanade.tachiyomi.novelsource.model.NovelUpdateStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.entries.novel.model.Novel

@Serializable
data class BackupNovel(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var artist: String? = null,
    @ProtoNumber(5) var author: String? = null,
    @ProtoNumber(6) var description: String? = null,
    @ProtoNumber(7) var genre: List<String> = emptyList(),
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var thumbnailUrl: String? = null,
    @ProtoNumber(13) var dateAdded: Long = 0,
    @ProtoNumber(16) var chapters: List<BackupNovelChapter> = emptyList(),
    @ProtoNumber(17) var collections: List<Long> = emptyList(),
    @ProtoNumber(18) var tracking: List<BackupNovelTracking> = emptyList(),
    // Bump by 100 for values that are not saved/implemented in 1.x but are used in 0.x
    @ProtoNumber(100) var favorite: Boolean = true,
    @ProtoNumber(101) var chapterFlags: Int = 0,
    @ProtoNumber(103) var viewerFlags: Int = 0,
    @ProtoNumber(104) var history: List<BackupNovelHistory> = emptyList(),
    @ProtoNumber(105) var updateStrategy: NovelUpdateStrategy = NovelUpdateStrategy.ALWAYS_UPDATE,
    @ProtoNumber(106) var lastModifiedAt: Long = 0,
    @ProtoNumber(107) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(109) var version: Long = 0,
    @ProtoNumber(110) var coverLastModified: Long = 0,
    @ProtoNumber(111) var initialized: Boolean = false,
) {
    fun getNovelImpl(): Novel {
        return Novel.create().copy(
            url = this@BackupNovel.url,
            title = this@BackupNovel.title,
            artist = this@BackupNovel.artist,
            author = this@BackupNovel.author,
            description = this@BackupNovel.description,
            genre = this@BackupNovel.genre,
            status = this@BackupNovel.status.toLong(),
            thumbnailUrl = this@BackupNovel.thumbnailUrl,
            favorite = this@BackupNovel.favorite,
            source = this@BackupNovel.source,
            dateAdded = this@BackupNovel.dateAdded,
            viewerFlags = this@BackupNovel.viewerFlags.toLong(),
            chapterFlags = this@BackupNovel.chapterFlags.toLong(),
            updateStrategy = this@BackupNovel.updateStrategy,
            lastModifiedAt = this@BackupNovel.lastModifiedAt,
            favoriteModifiedAt = this@BackupNovel.favoriteModifiedAt,
            version = this@BackupNovel.version,
            coverLastModified = this@BackupNovel.coverLastModified,
            initialized = this@BackupNovel.initialized,
        )
    }
}
