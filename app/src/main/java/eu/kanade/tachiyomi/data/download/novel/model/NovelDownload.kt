package eu.kanade.tachiyomi.data.download.novel.model

import eu.kanade.tachiyomi.novelsource.online.NovelHttpSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class NovelDownload(
    val source: NovelHttpSource,
    val novel: Novel,
    val chapter: NovelChapter,
) {

    var text: String? = null

    val totalProgress: Int
        get() = if (text != null) 100 else 0

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    val progressFlow = flow {
        emit(0)
        while (text == null && status == State.DOWNLOADING) {
            delay(50)
            emit(0)
        }
        if (text != null) emit(100)
    }
        .distinctUntilChanged()

    val progress: Int
        get() = if (text != null) 100 else 0

    enum class State(val value: Int) {
        NOT_DOWNLOADED(0),
        QUEUE(1),
        DOWNLOADING(2),
        DOWNLOADED(3),
        ERROR(4),
    }

    companion object {
        suspend fun fromChapterId(
            chapterId: Long,
            getNovelChapter: GetNovelChapter = Injekt.get(),
            getNovel: GetNovel = Injekt.get(),
            sourceManager: NovelSourceManager = Injekt.get(),
        ): NovelDownload? {
            val chapter = getNovelChapter.await(chapterId) ?: return null
            val novel = getNovel.await(chapter.novelId) ?: return null

            // Canon chapters carry source id 0 — resolve the serving
            // carrier's source + chapter url via the router.
            if (eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter.isCanonUrl(chapter.url)) {
                val workId = eu.kanade.tachiyomi.data.mikodb.CanonicalCatalog
                    .workIdFromNovelUrl(novel.url) ?: return null
                val seq = eu.kanade.tachiyomi.data.mikodb.CanonicalChapterRouter
                    .seqFromCanonUrl(workId, chapter.url) ?: return null
                val (carrier, candidate) = Injekt.get<eu.kanade.tachiyomi.data.mikodb
                    .CanonicalChapterRouter>()
                    .novelDownloadTarget(workId, seq) ?: return null
                return NovelDownload(carrier, novel, chapter.copy(url = candidate.chapterUrl))
            }

            val source = sourceManager.get(novel.source) as? NovelHttpSource ?: return null

            return NovelDownload(source, novel, chapter)
        }
    }
}
