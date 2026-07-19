package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.items.chapter.model.NovelChapter

@Serializable
data class BackupNovelChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var scanlator: String? = null,
    @ProtoNumber(4) var read: Boolean = false,
    @ProtoNumber(5) var bookmark: Boolean = false,
    @ProtoNumber(6) var lastCharRead: Long = 0,
    @ProtoNumber(7) var dateFetch: Long = 0,
    @ProtoNumber(8) var dateUpload: Long = 0,
    @ProtoNumber(9) var chapterNumber: Float = 0F,
    @ProtoNumber(10) var sourceOrder: Long = 0,
    @ProtoNumber(11) var lastModifiedAt: Long = 0,
    @ProtoNumber(12) var version: Long = 0,
    @ProtoNumber(13) var totalCharCount: Long = 0,
) {
    fun toNovelChapterImpl(): NovelChapter {
        return NovelChapter.create().copy(
            url = this@BackupNovelChapter.url,
            name = this@BackupNovelChapter.name,
            chapterNumber = this@BackupNovelChapter.chapterNumber.toDouble(),
            scanlator = this@BackupNovelChapter.scanlator,
            read = this@BackupNovelChapter.read,
            bookmark = this@BackupNovelChapter.bookmark,
            lastCharRead = this@BackupNovelChapter.lastCharRead,
            totalCharCount = this@BackupNovelChapter.totalCharCount,
            dateFetch = this@BackupNovelChapter.dateFetch,
            dateUpload = this@BackupNovelChapter.dateUpload,
            sourceOrder = this@BackupNovelChapter.sourceOrder,
            lastModifiedAt = this@BackupNovelChapter.lastModifiedAt,
            version = this@BackupNovelChapter.version,
        )
    }
}

val backupNovelChapterMapper = {
        _: Long,
        _: Long,
        url: String,
        name: String,
        scanlator: String?,
        read: Boolean,
        bookmark: Boolean,
        lastCharRead: Long,
        totalCharCount: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
        _: Long,
    ->
    BackupNovelChapter(
        url = url,
        name = name,
        chapterNumber = chapterNumber.toFloat(),
        scanlator = scanlator,
        read = read,
        bookmark = bookmark,
        lastCharRead = lastCharRead,
        totalCharCount = totalCharCount,
        dateFetch = dateFetch,
        dateUpload = dateUpload,
        sourceOrder = sourceOrder,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
