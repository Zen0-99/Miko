package tachiyomi.domain.entries.novel.repository

import tachiyomi.domain.entries.novel.model.NovelLink

interface NovelLinkRepository {

    suspend fun getLinkedNovelsByLinkedId(linkedId: Long): List<NovelLink>

    suspend fun getPrimaryByLinkedId(linkedId: Long): NovelLink?

    suspend fun getLinksByNovelId(novelId: Long): List<NovelLink>

    suspend fun getLinkedIdByNovelId(novelId: Long): Long?

    suspend fun insertLink(linkedId: Long, novelId: Long, sourceId: Long, isPrimary: Boolean, extensionType: String, priority: Long = 0)

    suspend fun deleteLink(novelId: Long)

    suspend fun deleteLinksByLinkedId(linkedId: Long)

    suspend fun getNextLinkedId(): Long

    suspend fun isNovelLinked(novelId: Long): Boolean

    suspend fun getAllLinks(): List<NovelLink>

    suspend fun setPrimary(novelId: Long, isPrimary: Boolean)

    suspend fun setPriority(novelId: Long, priority: Long)
}
