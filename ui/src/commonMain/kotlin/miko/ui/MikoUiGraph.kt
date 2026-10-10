package miko.ui

import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager

/**
 * The dependency surface shared screens consume. Each platform's composition
 * root (ios-app's AppGraph today; Android's AppModule at convergence) builds
 * one of these — screens/models never see platform wiring.
 */
class MikoUiGraph(
    val novelSourceManager: NovelSourceManager,
    val getLibraryNovels: GetLibraryNovels,
    val getNovel: GetNovel,
    val getNovelChaptersByNovelId: GetNovelChaptersByNovelId,
    val networkToLocalNovel: NetworkToLocalNovel,
    val novelRepository: NovelRepository,
    val novelChapterRepository: NovelChapterRepository,
)
