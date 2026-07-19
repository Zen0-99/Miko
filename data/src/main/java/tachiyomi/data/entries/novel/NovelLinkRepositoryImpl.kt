package tachiyomi.data.entries.novel

import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.entries.novel.model.NovelLink
import tachiyomi.domain.entries.novel.repository.NovelLinkRepository

class NovelLinkRepositoryImpl(
    private val handler: NovelDatabaseHandler,
) : NovelLinkRepository {

    override suspend fun getLinkedNovelsByLinkedId(linkedId: Long): List<NovelLink> {
        return handler.awaitList {
            novellinksQueries.getLinkedNovelsByLinkedId(linkedId, ::mapNovelLink)
        }
    }

    override suspend fun getPrimaryByLinkedId(linkedId: Long): NovelLink? {
        return handler.awaitOneOrNull {
            novellinksQueries.getPrimaryByLinkedId(linkedId, ::mapNovelLink)
        }
    }

    override suspend fun getLinksByNovelId(novelId: Long): List<NovelLink> {
        return handler.awaitList {
            novellinksQueries.getLinksByNovelId(novelId, ::mapNovelLink)
        }
    }

    override suspend fun getLinkedIdByNovelId(novelId: Long): Long? {
        return handler.awaitOneOrNullExecutable {
            novellinksQueries.getLinkedIdByNovelId(novelId)
        }
    }

    override suspend fun insertLink(
        linkedId: Long,
        novelId: Long,
        sourceId: Long,
        isPrimary: Boolean,
        extensionType: String,
    ) {
        handler.await(inTransaction = true) {
            novellinksQueries.insertLink(linkedId, novelId, sourceId, isPrimary, extensionType)
        }
    }

    override suspend fun deleteLink(novelId: Long) {
        handler.await(inTransaction = true) {
            novellinksQueries.deleteLink(novelId)
        }
    }

    override suspend fun deleteLinksByLinkedId(linkedId: Long) {
        handler.await(inTransaction = true) {
            novellinksQueries.deleteLinksByLinkedId(linkedId)
        }
    }

    override suspend fun getNextLinkedId(): Long {
        return handler.awaitOneExecutable {
            novellinksQueries.getNextLinkedId()
        }
    }

    override suspend fun isNovelLinked(novelId: Long): Boolean {
        return handler.awaitOneExecutable {
            novellinksQueries.isNovelLinked(novelId)
        }
    }

    override suspend fun getAllLinks(): List<NovelLink> {
        return handler.awaitList {
            novellinksQueries.getAllLinks(::mapNovelLink)
        }
    }

    override suspend fun setPrimary(novelId: Long, isPrimary: Boolean) {
        handler.await(inTransaction = true) {
            novellinksQueries.setPrimary(isPrimary, novelId)
        }
    }

    override suspend fun setPriority(novelId: Long, priority: Long) {
        handler.await(inTransaction = true) {
            novellinksQueries.updatePriority(priority, novelId)
        }
    }

    private fun mapNovelLink(
        id: Long,
        linkedId: Long,
        novelId: Long,
        sourceId: Long,
        isPrimary: Boolean,
        extensionType: String,
        priority: Long,
    ): NovelLink {
        return NovelLink(
            id = id,
            linkedId = linkedId,
            novelId = novelId,
            sourceId = sourceId,
            isPrimary = isPrimary,
            extensionType = extensionType,
            priority = priority,
        )
    }
}
