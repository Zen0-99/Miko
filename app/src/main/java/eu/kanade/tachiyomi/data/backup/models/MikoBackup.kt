package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class MikoBackup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
    @ProtoNumber(2) var backupCategories: List<BackupCategory> = emptyList(),
    @ProtoNumber(101) var backupSources: List<BackupSource> = emptyList(),
    @ProtoNumber(104) var backupPreferences: List<BackupPreference> = emptyList(),
    @ProtoNumber(105) var backupSourcePreferences: List<BackupSourcePreferences> = emptyList(),
    @ProtoNumber(200) var backupNovels: List<MikoBackupNovel> = emptyList(),
    @ProtoNumber(201) var backupNovelCategories: List<MikoBackupNovelCategory> = emptyList(),
    @ProtoNumber(202) var backupNovelSources: List<BackupSource> = emptyList(),
)

@Serializable
data class MikoBackupNovel(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var author: String? = null,
    @ProtoNumber(5) var description: String? = null,
    @ProtoNumber(6) var genre: String? = null,
    @ProtoNumber(7) var status: Int = 0,
    @ProtoNumber(8) var posterUrl: String? = null,
    @ProtoNumber(9) var favorite: Boolean = true,
    @ProtoNumber(10) var dateAdded: Long = 0,
    @ProtoNumber(11) var chapterFlags: Int = 0,
    @ProtoNumber(12) var chapters: List<MikoBackupNovelChapter> = emptyList(),
    @ProtoNumber(13) var categories: List<Int> = emptyList(),
    @ProtoNumber(14) var history: List<MikoBackupNovelHistory> = emptyList(),
    @ProtoNumber(15) var wordCount: Int? = null,
    @ProtoNumber(16) var chapterCount: Int? = null,
    @ProtoNumber(17) var vibrantCoverColor: Int? = null,
    @ProtoNumber(18) var filteredTranslators: String? = null,
    @ProtoNumber(19) var highlights: List<MikoBackupNovelHighlight> = emptyList(),
    @ProtoNumber(20) var lastUpdate: Long = 0,
    @ProtoNumber(21) var initialized: Boolean = false,
    @ProtoNumber(22) var coverLastModified: Long = 0,
    @ProtoNumber(23) var tracking: List<MikoBackupNovelTrack> = emptyList(),
) {
    fun toBackupNovel(): BackupNovel {
        return BackupNovel(
            source = this.source,
            url = this.url,
            title = this.title,
            artist = null,
            author = this.author,
            description = this.description,
            genre = this.genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList(),
            status = this.status,
            thumbnailUrl = this.posterUrl,
            favorite = this.favorite,
            dateAdded = this.dateAdded,
            chapterFlags = this.chapterFlags,
            version = 0,
            coverLastModified = this.coverLastModified,
            initialized = this.initialized,
        ).also { backup ->
            backup.chapters = this.chapters.map { it.toBackupNovelChapter() }
            backup.categories = this.categories.map { it.toLong() }
            backup.history = this.history.map { it.toBackupNovelHistory() }
            backup.tracking = this.tracking.map { it.toBackupNovelTracking() }
        }
    }
}

@Serializable
data class MikoBackupNovelChapter(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var read: Boolean = false,
    @ProtoNumber(4) var bookmark: Boolean = false,
    @ProtoNumber(5) var lastReadPosition: Int = 0,
    @ProtoNumber(6) var dateFetch: Long = 0,
    @ProtoNumber(7) var dateUpload: Long = 0,
    @ProtoNumber(8) var chapterNumber: Double = 0.0,
    @ProtoNumber(9) var sourceOrder: Int = 0,
    @ProtoNumber(10) var volumeNumber: Double? = null,
    @ProtoNumber(11) var wordCount: Int? = null,
    @ProtoNumber(12) var readingTimeMs: Long = 0,
    @ProtoNumber(13) var translator: String? = null,
) {
    fun toBackupNovelChapter(): BackupNovelChapter {
        return BackupNovelChapter(
            url = this.url,
            name = this.name,
            scanlator = this.translator,
            read = this.read,
            bookmark = this.bookmark,
            lastCharRead = this.lastReadPosition.toLong(),
            totalCharCount = this.wordCount?.toLong() ?: 0L,
            dateFetch = this.dateFetch,
            dateUpload = this.dateUpload,
            chapterNumber = this.chapterNumber.toFloat(),
            sourceOrder = this.sourceOrder.toLong(),
        )
    }
}

@Serializable
data class MikoBackupNovelHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastRead: Long,
    @ProtoNumber(3) var readDuration: Long = 0,
    @ProtoNumber(4) var characterPosition: Int = 0,
) {
    fun toBackupNovelHistory(): BackupNovelHistory {
        return BackupNovelHistory(
            url = this.url,
            lastRead = this.lastRead,
            readDuration = this.readDuration,
        )
    }
}

@Serializable
data class MikoBackupNovelTrack(
    @ProtoNumber(1) var syncId: Int,
    @ProtoNumber(2) var remoteId: Long,
    @ProtoNumber(3) var libraryId: Long? = null,
    @ProtoNumber(4) var title: String = "",
    @ProtoNumber(5) var lastChapterRead: Float = 0F,
    @ProtoNumber(6) var totalChapters: Int = 0,
    @ProtoNumber(7) var score: Float = 0F,
    @ProtoNumber(8) var status: Int = 0,
    @ProtoNumber(9) var remoteUrl: String = "",
    @ProtoNumber(10) var startDate: Long = 0,
    @ProtoNumber(11) var finishDate: Long = 0,
) {
    fun toBackupNovelTracking(): BackupNovelTracking {
        return BackupNovelTracking(
            syncId = this.syncId,
            libraryId = this.libraryId ?: 0,
            trackingUrl = this.remoteUrl,
            title = this.title,
            lastChapterRead = this.lastChapterRead,
            totalChapters = this.totalChapters,
            score = this.score,
            status = this.status,
            startedReadingDate = this.startDate,
            finishedReadingDate = this.finishDate,
            mediaId = this.remoteId,
        )
    }
}

@Serializable
data class MikoBackupNovelCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Int = 0,
    @ProtoNumber(100) var flags: Int = 0,
) {
    fun toBackupCategory(): BackupCategory {
        return BackupCategory(
            name = this.name,
            order = this.order.toLong(),
            flags = this.flags.toLong(),
        )
    }
}

@Serializable
data class MikoBackupNovelHighlight(
    @ProtoNumber(1) var chapterNumber: Double,
    @ProtoNumber(2) var chapterTitle: String = "",
    @ProtoNumber(3) var text: String,
)
