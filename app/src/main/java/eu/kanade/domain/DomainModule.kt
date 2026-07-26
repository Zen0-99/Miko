package eu.kanade.domain

import eu.kanade.domain.download.anime.interactor.DeleteEpisodeDownload
import eu.kanade.domain.download.manga.interactor.DeleteChapterDownload
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetMangaViewerFlags
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionLanguages
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionSources
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.extension.novel.interactor.TrustNovelExtension
import eu.kanade.domain.extension.novel.interactor.GetNovelExtensionsByType
import eu.kanade.domain.extension.novel.interactor.GetNovelExtensionLanguages
import eu.kanade.domain.extension.novel.interactor.GetNovelExtensionSources
import eu.kanade.domain.extension.manga.interactor.GetExtensionSources
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionLanguages
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.interactor.TrustMangaExtension
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.domain.source.anime.interactor.GetAnimeIncognitoState
import eu.kanade.domain.source.anime.interactor.GetAnimeSourcesWithFavoriteCount
import eu.kanade.domain.source.anime.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.anime.interactor.GetLanguagesWithAnimeSources
import eu.kanade.domain.source.anime.interactor.ToggleAnimeIncognito
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSource
import eu.kanade.domain.source.anime.interactor.ToggleAnimeSourcePin
import eu.kanade.domain.entries.novel.interactor.UpdateNovel
import eu.kanade.domain.items.chapter.interactor.SetNovelReadStatus
import eu.kanade.domain.items.chapter.interactor.SetNovelReadingPosition
import eu.kanade.tachiyomi.ui.reader.novel.CharacterPositionTracker
import tachiyomi.domain.items.chapter.interactor.UpdateNovelChapter
import eu.kanade.domain.source.novel.interactor.GetEnabledNovelSources
import eu.kanade.domain.source.novel.interactor.GetNovelSourcesWithFavoriteCount
import eu.kanade.domain.source.novel.interactor.ToggleNovelSource
import eu.kanade.domain.source.novel.interactor.ToggleNovelSourcePin
import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.domain.source.interactor.ToggleLanguage
import eu.kanade.domain.source.manga.interactor.GetEnabledMangaSources
import eu.kanade.domain.source.manga.interactor.GetLanguagesWithMangaSources
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.manga.interactor.GetMangaSourcesWithFavoriteCount
import eu.kanade.domain.source.manga.interactor.ToggleMangaIncognito
import eu.kanade.domain.source.manga.interactor.ToggleMangaSource
import eu.kanade.domain.source.manga.interactor.ToggleMangaSourcePin
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.SyncEpisodeProgressWithTrack
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.SyncChapterProgressWithTrack
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.tachiyomi.extension.novel.NovelExtensionUpdateChecker
import eu.kanade.tachiyomi.extension.novel.repo.NovelExtensionListingInteractor
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoParser
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoService
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoServiceContract
import eu.kanade.tachiyomi.extension.novel.repo.NovelPluginRepoUpdateInteractor
import eu.kanade.tachiyomi.ui.player.utils.TrackSelect
import mihon.data.repository.anime.AnimeExtensionRepoRepositoryImpl
import mihon.data.repository.manga.MangaExtensionRepoRepositoryImpl
import mihon.data.repository.novel.NovelExtensionRepoRepositoryImpl
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepoCount
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepoCount
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.novel.repository.NovelExtensionRepoRepository
import mihon.domain.extensionrepo.novel.interactor.CreateNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.DeleteNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.GetNovelExtensionRepoCount
import mihon.domain.extensionrepo.novel.interactor.ReplaceNovelExtensionRepo
import mihon.domain.extensionrepo.novel.interactor.UpdateNovelExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoService
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import mihon.domain.upcoming.novel.interactor.GetUpcomingNovel
import tachiyomi.data.collection.anime.AnimeCollectionRepositoryImpl
import tachiyomi.data.collection.manga.MangaCollectionRepositoryImpl
import tachiyomi.data.library.FailedFetchRepositoryImpl
import tachiyomi.domain.library.interactor.ClearFailedFetches
import tachiyomi.domain.library.interactor.DeleteFailedFetch
import tachiyomi.domain.library.interactor.GetFailedFetches
import tachiyomi.domain.library.interactor.InsertFailedFetch
import tachiyomi.domain.library.repository.FailedFetchRepository
import tachiyomi.data.custombutton.CustomButtonRepositoryImpl
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.entries.novel.NovelRepositoryImpl
import tachiyomi.data.entries.novel.NovelLinkRepositoryImpl
import tachiyomi.data.collection.novel.NovelCollectionRepositoryImpl
import tachiyomi.data.extension.novel.NovelPluginRepositoryImpl
import tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl
import tachiyomi.data.history.manga.MangaHistoryRepositoryImpl
import tachiyomi.data.history.novel.NovelHistoryRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.data.items.chapter.NovelChapterRepositoryImpl
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.data.release.ReleaseServiceImpl
import tachiyomi.data.source.anime.AnimeSourceRepositoryImpl
import tachiyomi.data.source.anime.AnimeStubSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaSourceRepositoryImpl
import tachiyomi.data.source.manga.MangaStubSourceRepositoryImpl
import tachiyomi.data.source.novel.NovelSourceRepositoryImpl
import tachiyomi.data.source.novel.NovelStubSourceRepositoryImpl
import tachiyomi.data.track.anime.AnimeTrackRepositoryImpl
import tachiyomi.data.track.manga.MangaTrackRepositoryImpl
import tachiyomi.data.savedsearches.anime.AnimeSavedSearchRepositoryImpl
import tachiyomi.data.savedsearches.manga.MangaSavedSearchRepositoryImpl
import tachiyomi.data.savedsearches.novel.NovelSavedSearchRepositoryImpl
import tachiyomi.data.track.novel.NovelTrackRepositoryImpl
import tachiyomi.data.updates.anime.AnimeUpdatesRepositoryImpl
import tachiyomi.data.updates.manga.MangaUpdatesRepositoryImpl
import tachiyomi.data.updates.novel.NovelUpdatesRepositoryImpl
import tachiyomi.domain.collection.anime.interactor.CreateAnimeCollectionWithName
import tachiyomi.domain.collection.anime.interactor.DeleteAnimeCollection
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.anime.interactor.GetAnimeCustomOrder
import tachiyomi.domain.collection.anime.interactor.GetVisibleAnimeCollections
import tachiyomi.domain.collection.anime.interactor.HideAnimeCollection
import tachiyomi.domain.collection.anime.interactor.RenameAnimeCollection
import tachiyomi.domain.collection.anime.interactor.ReorderAnimeCollection
import tachiyomi.domain.collection.anime.interactor.ResetAnimeCollectionFlags
import tachiyomi.domain.collection.anime.interactor.SetAnimeCollections
import tachiyomi.domain.collection.anime.interactor.SetAnimeCustomOrder
import tachiyomi.domain.collection.anime.interactor.SetAnimeDisplayMode
import tachiyomi.domain.collection.anime.interactor.SetSortModeForAnimeCollection
import tachiyomi.domain.collection.anime.interactor.UpdateAnimeCollection
import tachiyomi.domain.collection.anime.repository.AnimeCollectionRepository
import tachiyomi.domain.collection.manga.interactor.CreateMangaCollectionWithName
import tachiyomi.domain.collection.manga.interactor.DeleteMangaCollection
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.GetMangaCustomOrder
import tachiyomi.domain.collection.novel.interactor.GetNovelCollections
import tachiyomi.domain.collection.manga.interactor.GetVisibleMangaCollections
import tachiyomi.domain.collection.manga.interactor.HideMangaCollection
import tachiyomi.domain.collection.manga.interactor.RenameMangaCollection
import tachiyomi.domain.collection.manga.interactor.ReorderMangaCollection
import tachiyomi.domain.collection.manga.interactor.ResetMangaCollectionFlags
import tachiyomi.domain.collection.manga.interactor.SetMangaCollections
import tachiyomi.domain.collection.manga.interactor.SetMangaCustomOrder
import tachiyomi.domain.collection.manga.interactor.SetMangaDisplayMode
import tachiyomi.domain.collection.manga.interactor.SetSortModeForMangaCollection
import tachiyomi.domain.collection.manga.interactor.UpdateMangaCollection
import tachiyomi.domain.collection.manga.repository.MangaCollectionRepository
import tachiyomi.domain.custombuttons.interactor.CreateCustomButton
import tachiyomi.domain.custombuttons.interactor.DeleteCustomButton
import tachiyomi.domain.custombuttons.interactor.GetCustomButtons
import tachiyomi.domain.custombuttons.interactor.ReorderCustomButton
import tachiyomi.domain.custombuttons.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombuttons.interactor.UpdateCustomButton
import tachiyomi.domain.custombuttons.repository.CustomButtonRepository
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.interactor.ResetAnimeViewerFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.entries.manga.interactor.ResetMangaViewerFlags
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.interactor.GetNovel
import tachiyomi.domain.entries.novel.interactor.GetNovelFavorites
import tachiyomi.domain.entries.novel.interactor.GetNovelByUrlAndSourceId
import tachiyomi.domain.entries.novel.interactor.GetLinkedNovels
import tachiyomi.domain.entries.novel.interactor.GetNovelLinks
import tachiyomi.domain.entries.novel.interactor.GetNovelLinkedId
import tachiyomi.domain.entries.novel.interactor.LinkNovels
import tachiyomi.domain.entries.novel.interactor.UnlinkNovel
import tachiyomi.domain.entries.novel.interactor.GetAllNovelLinks
import tachiyomi.domain.entries.novel.interactor.SetNovelSourcePriority
import tachiyomi.domain.entries.novel.interactor.MergeLinkedNovelChapters
import tachiyomi.domain.entries.novel.interactor.MakeLinkedPrimary
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovels
import tachiyomi.domain.entries.novel.interactor.GetNovelWithChapters
import tachiyomi.domain.entries.novel.interactor.GetDuplicateLibraryNovel
import tachiyomi.domain.entries.novel.interactor.NetworkToLocalNovel
import tachiyomi.domain.entries.novel.interactor.SetNovelChapterFlags
import tachiyomi.domain.entries.novel.interactor.SetNovelDefaultChapterFlags
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.entries.novel.repository.NovelLinkRepository
import tachiyomi.domain.source.novel.interactor.GetRemoteNovel
import tachiyomi.domain.source.novel.repository.NovelSourceRepository
import tachiyomi.domain.collection.novel.repository.NovelCollectionRepository
import tachiyomi.domain.collection.novel.interactor.GetVisibleNovelCollections
import tachiyomi.domain.collection.novel.interactor.GetNovelCustomOrder
import tachiyomi.domain.collection.novel.interactor.SetNovelCollections
import tachiyomi.domain.collection.novel.interactor.SetNovelCustomOrder
import tachiyomi.domain.collection.novel.interactor.SetSortModeForNovelCollection
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.anime.interactor.GetNextEpisodes
import tachiyomi.domain.history.anime.interactor.RemoveAnimeHistory
import tachiyomi.domain.history.anime.interactor.UpsertAnimeHistory
import tachiyomi.domain.history.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.history.manga.interactor.RemoveMangaHistory
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.history.manga.repository.MangaHistoryRepository
import tachiyomi.domain.history.novel.interactor.GetNovelHistory
import tachiyomi.domain.history.novel.interactor.GetNextNovelChapters
import tachiyomi.domain.history.novel.interactor.GetTotalNovelReadDuration
import tachiyomi.domain.history.novel.interactor.RemoveNovelHistory
import tachiyomi.domain.history.novel.interactor.UpsertNovelHistory
import tachiyomi.domain.history.novel.repository.NovelHistoryRepository
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.items.chapter.interactor.GetNovelChapter
import tachiyomi.domain.items.chapter.interactor.GetNovelChaptersByNovelId
import tachiyomi.domain.items.chapter.interactor.GetTotalReadNovelCharCount
import tachiyomi.domain.items.chapter.repository.NovelChapterRepository
import tachiyomi.domain.extension.novel.repository.NovelPluginRepository
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.items.season.interactor.ShouldUpdateDbSeason
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.service.ReleaseService
import tachiyomi.domain.source.anime.interactor.GetAnimeSourcesWithNonLibraryAnime
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.anime.repository.AnimeSourceRepository
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.novel.repository.NovelStubSourceRepository
import tachiyomi.domain.source.manga.interactor.GetMangaSourcesWithNonLibraryManga
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.manga.repository.MangaSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.GetTracksPerAnime
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.anime.repository.AnimeTrackRepository
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.GetTracksPerManga
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.manga.repository.MangaTrackRepository
import tachiyomi.domain.track.novel.interactor.DeleteNovelTrack
import tachiyomi.domain.track.novel.interactor.GetNovelTracks
import tachiyomi.domain.track.novel.interactor.InsertNovelTrack
import tachiyomi.domain.track.novel.repository.NovelTrackRepository
import eu.kanade.domain.track.novel.interactor.AddNovelTracks
import eu.kanade.domain.track.novel.interactor.RefreshNovelTracks
import eu.kanade.domain.track.service.ResolveTrackProgressSync
import eu.kanade.domain.track.novel.interactor.SyncNovelChapterProgressWithTrack
import eu.kanade.domain.track.novel.interactor.TrackNovelChapter
import tachiyomi.domain.savedsearches.anime.interactor.DeleteAnimeSavedSearch
import tachiyomi.domain.savedsearches.anime.interactor.GetAnimeSavedSearches
import tachiyomi.domain.savedsearches.anime.interactor.InsertAnimeSavedSearch
import tachiyomi.domain.savedsearches.anime.repository.AnimeSavedSearchRepository
import tachiyomi.domain.savedsearches.manga.interactor.DeleteMangaSavedSearch
import tachiyomi.domain.savedsearches.manga.interactor.GetMangaSavedSearches
import tachiyomi.domain.savedsearches.manga.interactor.InsertMangaSavedSearch
import tachiyomi.domain.savedsearches.manga.repository.MangaSavedSearchRepository
import tachiyomi.domain.savedsearches.novel.interactor.DeleteNovelSavedSearch
import tachiyomi.domain.savedsearches.novel.interactor.GetNovelSavedSearches
import tachiyomi.domain.savedsearches.novel.interactor.InsertNovelSavedSearch
import tachiyomi.domain.savedsearches.novel.repository.NovelSavedSearchRepository
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import tachiyomi.domain.updates.novel.interactor.GetNovelUpdates
import tachiyomi.domain.updates.novel.repository.NovelUpdatesRepository
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addFactory
import uy.kohesive.injekt.api.addSingletonFactory
import uy.kohesive.injekt.api.get

class DomainModule : InjektModule {

    override fun InjektRegistrar.registerInjectables() {
        addSingletonFactory<AnimeCollectionRepository> { AnimeCollectionRepositoryImpl(get()) }
        addFactory { GetAnimeCollections(get()) }
        addFactory { GetAnimeCustomOrder(get()) }
        addFactory { GetVisibleAnimeCollections(get()) }
        addFactory { ResetAnimeCollectionFlags(get(), get()) }
        addFactory { SetAnimeDisplayMode(get()) }
        addFactory { SetSortModeForAnimeCollection(get(), get()) }
        addFactory { CreateAnimeCollectionWithName(get(), get()) }
        addFactory { RenameAnimeCollection(get()) }
        addFactory { ReorderAnimeCollection(get()) }
        addFactory { UpdateAnimeCollection(get()) }
        addFactory { HideAnimeCollection(get()) }
        addFactory { DeleteAnimeCollection(get(), get(), get()) }

        addSingletonFactory<MangaCollectionRepository> { MangaCollectionRepositoryImpl(get()) }
        addFactory { GetMangaCollections(get()) }
        addFactory { GetMangaCustomOrder(get()) }
        addFactory { GetVisibleMangaCollections(get()) }
        addFactory { ResetMangaCollectionFlags(get(), get()) }
        addFactory { SetMangaDisplayMode(get()) }
        addFactory { SetSortModeForMangaCollection(get(), get()) }
        addFactory { CreateMangaCollectionWithName(get(), get()) }
        addFactory { RenameMangaCollection(get()) }
        addFactory { ReorderMangaCollection(get()) }
        addFactory { UpdateMangaCollection(get()) }
        addFactory { HideMangaCollection(get()) }
        addFactory { DeleteMangaCollection(get(), get(), get()) }

        // Failed fetch tracking (Fetching tab in Updates)
        addSingletonFactory<FailedFetchRepository> { FailedFetchRepositoryImpl(get()) }
        addFactory { GetFailedFetches(get()) }
        addFactory { InsertFailedFetch(get()) }
        addFactory { DeleteFailedFetch(get()) }
        addFactory { ClearFailedFetches(get()) }

        addSingletonFactory<AnimeRepository> { AnimeRepositoryImpl(get(), get()) }
        addFactory { GetDuplicateLibraryAnime(get()) }
        addFactory { GetAnimeFavorites(get()) }
        addFactory { GetLibraryAnime(get()) }
        addFactory { GetAnimeWithEpisodesAndSeasons(get(), get()) }
        addFactory { GetAnimeByUrlAndSourceId(get()) }
        addFactory { GetAnime(get()) }
        addFactory { GetAnimeSeasonsByParentId(get()) }
        addFactory { GetNextEpisodes(get(), get(), get()) }
        addFactory { GetUpcomingAnime(get()) }
        addFactory { ResetAnimeViewerFlags(get()) }
        addFactory { SetAnimeEpisodeFlags(get()) }
        addFactory { SetAnimeSeasonFlags(get()) }
        addFactory { AnimeFetchInterval(get()) }
        addFactory { SetAnimeDefaultEpisodeFlags(get(), get(), get()) }
        addFactory { SetAnimeDefaultSeasonFlags(get(), get(), get()) }
        addFactory { SetAnimeViewerFlags(get()) }
        addFactory { NetworkToLocalAnime(get(), get()) }
        addFactory { UpdateAnime(get(), get()) }
        addFactory { SetAnimeCollections(get()) }
        addFactory { SetAnimeCustomOrder(get()) }
        addFactory { ShouldUpdateDbSeason() }
        addFactory { SyncSeasonsWithSource(get(), get(), get(), get(), get()) }

        addSingletonFactory<MangaRepository> { MangaRepositoryImpl(get(), get()) }
        addFactory { GetDuplicateLibraryManga(get()) }
        addFactory { GetMangaFavorites(get()) }
        addFactory { GetLibraryManga(get()) }
        addFactory { GetMangaWithChapters(get(), get()) }
        addFactory { GetMangaByUrlAndSourceId(get()) }
        addFactory { GetManga(get()) }
        addFactory { GetNextChapters(get(), get(), get()) }
        addFactory { GetUpcomingManga(get()) }
        addFactory { ResetMangaViewerFlags(get()) }
        addFactory { SetMangaChapterFlags(get()) }
        addFactory { MangaFetchInterval(get()) }
        addFactory {
            SetMangaDefaultChapterFlags(
                get(),
                get(),
                get(),
            )
        }
        addFactory { SetMangaViewerFlags(get()) }
        addFactory { NetworkToLocalManga(get()) }
        addFactory { UpdateManga(get(), get()) }
        addFactory { SetMangaCollections(get()) }
        addFactory { SetMangaCustomOrder(get()) }
        addFactory { GetExcludedScanlators(get()) }
        addFactory { SetExcludedScanlators(get()) }

        addSingletonFactory<ReleaseService> { ReleaseServiceImpl(get(), get()) }
        addFactory { GetApplicationRelease(get(), get()) }

        addSingletonFactory<AnimeTrackRepository> { AnimeTrackRepositoryImpl(get()) }
        addFactory { TrackEpisode(get(), get(), get(), get()) }
        addFactory { AddAnimeTracks(get(), get(), get(), get()) }
        addFactory { RefreshAnimeTracks(get(), get(), get(), get()) }
        addFactory { DeleteAnimeTrack(get()) }
        addFactory { GetTracksPerAnime(get()) }
        addFactory { GetAnimeTracks(get()) }
        addFactory { InsertAnimeTrack(get()) }
        addFactory { SyncEpisodeProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<MangaTrackRepository> { MangaTrackRepositoryImpl(get()) }
        addFactory { TrackChapter(get(), get(), get(), get()) }
        addFactory { AddMangaTracks(get(), get(), get(), get()) }
        addFactory { RefreshMangaTracks(get(), get(), get(), get()) }
        addFactory { DeleteMangaTrack(get()) }
        addFactory { GetTracksPerManga(get()) }
        addFactory { GetMangaTracks(get()) }
        addFactory { InsertMangaTrack(get()) }
        addFactory { SyncChapterProgressWithTrack(get(), get(), get()) }

        addSingletonFactory<EpisodeRepository> { EpisodeRepositoryImpl(get()) }
        addFactory { GetEpisode(get()) }
        addFactory { GetEpisodesByAnimeId(get()) }
        addFactory { GetEpisodeByUrlAndAnimeId(get()) }
        addFactory { UpdateEpisode(get()) }
        addFactory { SetSeenStatus(get(), get(), get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbEpisode() }
        addFactory { SyncEpisodesWithSource(get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { FilterEpisodesForDownload(get(), get(), get()) }

        addSingletonFactory<ChapterRepository> { ChapterRepositoryImpl(get()) }
        addFactory { GetChapter(get()) }
        addFactory { GetChaptersByMangaId(get()) }
        addFactory { GetChapterByUrlAndMangaId(get()) }
        addFactory { UpdateChapter(get()) }
        addFactory { SetReadStatus(get(), get(), get(), get(), get(), get()) }
        addFactory { ShouldUpdateDbChapter() }
        addFactory { SyncChaptersWithSource(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
        addFactory { GetAvailableScanlators(get()) }
        addFactory { FilterChaptersForDownload(get(), get(), get()) }

        addSingletonFactory<AnimeHistoryRepository> { AnimeHistoryRepositoryImpl(get()) }
        addFactory { GetAnimeHistory(get()) }
        addFactory { UpsertAnimeHistory(get()) }
        addFactory { RemoveAnimeHistory(get()) }

        addFactory { DeleteEpisodeDownload(get(), get()) }

        addFactory { GetAnimeExtensionsByType(get(), get()) }
        addFactory { GetAnimeExtensionSources(get()) }
        addFactory { GetAnimeExtensionLanguages(get(), get()) }

        addSingletonFactory<MangaHistoryRepository> { MangaHistoryRepositoryImpl(get()) }
        addFactory { GetMangaHistory(get()) }
        addFactory { UpsertMangaHistory(get()) }
        addFactory { RemoveMangaHistory(get()) }
        addFactory { GetTotalReadDuration(get()) }

        addFactory { DeleteChapterDownload(get(), get()) }

        addFactory { GetMangaExtensionsByType(get(), get()) }
        addFactory { GetExtensionSources(get()) }
        addFactory { GetMangaExtensionLanguages(get(), get()) }
        addFactory { GetNovelExtensionsByType(get(), get()) }
        addFactory { GetNovelExtensionLanguages(get(), get()) }
        addFactory { GetNovelExtensionSources(get()) }

        addSingletonFactory<AnimeUpdatesRepository> { AnimeUpdatesRepositoryImpl(get()) }
        addFactory { GetAnimeUpdates(get()) }

        addSingletonFactory<MangaUpdatesRepository> { MangaUpdatesRepositoryImpl(get()) }
        addFactory { GetMangaUpdates(get()) }

        addSingletonFactory<AnimeSourceRepository> { AnimeSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<AnimeStubSourceRepository> { AnimeStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledAnimeSources(get(), get()) }
        addFactory { GetLanguagesWithAnimeSources(get(), get()) }
        addFactory { GetRemoteAnime(get()) }
        addFactory { GetAnimeSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetAnimeSourcesWithNonLibraryAnime(get()) }
        addFactory { ToggleAnimeSource(get()) }
        addFactory { ToggleAnimeSourcePin(get()) }

        addSingletonFactory<MangaSourceRepository> { MangaSourceRepositoryImpl(get(), get()) }
        addSingletonFactory<MangaStubSourceRepository> { MangaStubSourceRepositoryImpl(get()) }
        addFactory { GetEnabledMangaSources(get(), get()) }
        addFactory { GetLanguagesWithMangaSources(get(), get()) }
        addFactory { GetRemoteManga(get()) }
        addFactory { GetMangaSourcesWithFavoriteCount(get(), get()) }
        addFactory { GetMangaSourcesWithNonLibraryManga(get()) }
        addFactory { SetMigrateSorting(get()) }
        addFactory { ToggleLanguage(get()) }
        addFactory { ToggleMangaSource(get()) }
        addFactory { ToggleMangaSourcePin(get()) }
        addFactory { TrustAnimeExtension(get(), get()) }
        addFactory { TrustMangaExtension(get(), get()) }
        addFactory { TrustNovelExtension(get(), get()) }

        addFactory { ExtensionRepoService(get(), get()) }

        addSingletonFactory<AnimeExtensionRepoRepository> { AnimeExtensionRepoRepositoryImpl(get()) }
        addFactory { GetAnimeExtensionRepo(get()) }
        addFactory { GetAnimeExtensionRepoCount(get()) }
        addFactory { CreateAnimeExtensionRepo(get(), get()) }
        addFactory { DeleteAnimeExtensionRepo(get()) }
        addFactory { ReplaceAnimeExtensionRepo(get()) }
        addFactory { UpdateAnimeExtensionRepo(get(), get()) }
        addFactory { ToggleAnimeIncognito(get()) }
        addFactory { GetAnimeIncognitoState(get(), get(), get()) }

        addSingletonFactory<MangaExtensionRepoRepository> { MangaExtensionRepoRepositoryImpl(get()) }
        addFactory { GetMangaExtensionRepo(get()) }
        addFactory { GetMangaExtensionRepoCount(get()) }
        addFactory { CreateMangaExtensionRepo(get(), get()) }
        addFactory { DeleteMangaExtensionRepo(get()) }
        addFactory { ReplaceMangaExtensionRepo(get()) }
        addFactory { UpdateMangaExtensionRepo(get(), get()) }
        addFactory { ToggleMangaIncognito(get()) }
        addFactory { GetMangaIncognitoState(get(), get(), get()) }
        addFactory { eu.kanade.domain.source.novel.interactor.GetNovelIncognitoState(get(), get(), get()) }

        addSingletonFactory<NovelStubSourceRepository> { NovelStubSourceRepositoryImpl(get()) }
        addSingletonFactory<NovelSourceRepository> { NovelSourceRepositoryImpl(get()) }
        addSingletonFactory<NovelExtensionRepoRepository> { NovelExtensionRepoRepositoryImpl(get()) }
        addFactory { GetNovelExtensionRepo(get()) }
        addFactory { GetNovelExtensionRepoCount(get()) }
        addFactory { CreateNovelExtensionRepo(get(), get()) }
        addFactory { DeleteNovelExtensionRepo(get()) }
        addFactory { ReplaceNovelExtensionRepo(get()) }
        addFactory { UpdateNovelExtensionRepo(get(), get()) }

        addSingletonFactory<NovelPluginRepository> { NovelPluginRepositoryImpl(get()) }
        addSingletonFactory<NovelPluginRepoServiceContract> {
            NovelPluginRepoService(get(), NovelPluginRepoParser(get()))
        }
        addFactory { NovelExtensionUpdateChecker() }
        addFactory { NovelPluginRepoUpdateInteractor(get(), get(), get()) }
        addFactory { NovelExtensionListingInteractor() }

        addSingletonFactory<NovelRepository> { NovelRepositoryImpl(get(), get()) }
        addSingletonFactory<NovelLinkRepository> { NovelLinkRepositoryImpl(get()) }
        addSingletonFactory<NovelChapterRepository> { NovelChapterRepositoryImpl(get()) }
        addSingletonFactory<NovelCollectionRepository> { NovelCollectionRepositoryImpl(get()) }
        addFactory { GetNovel(get()) }
        addFactory { GetNovelFavorites(get()) }
        addFactory { GetNovelByUrlAndSourceId(get()) }
        addFactory { GetNovelChapter(get()) }
        addFactory { GetNovelChaptersByNovelId(get()) }
        addFactory { GetTotalReadNovelCharCount(get()) }
        addFactory { GetNovelCollections(get()) }
        addFactory { GetNovelCustomOrder(get()) }
        addFactory { GetLinkedNovels(get()) }
        addFactory { GetNovelLinks(get()) }
        addFactory { GetNovelLinkedId(get()) }
        addFactory { LinkNovels(get()) }
        addFactory { UnlinkNovel(get()) }
        addFactory { GetAllNovelLinks(get()) }
        addFactory { SetNovelSourcePriority(get()) }
        addFactory { MergeLinkedNovelChapters() }
        addFactory { MakeLinkedPrimary(get()) }
        addFactory { GetLibraryNovels(get()) }
        addFactory { GetVisibleNovelCollections(get()) }
        addFactory { SetNovelCollections(get()) }
        addFactory { SetNovelCustomOrder(get()) }
        addFactory { SetSortModeForNovelCollection(get(), get()) }
        addFactory { NetworkToLocalNovel(get()) }
        addFactory { GetRemoteNovel(get()) }
        addFactory { GetEnabledNovelSources(get(), get()) }
        addFactory { GetNovelSourcesWithFavoriteCount(get(), get()) }
        addFactory { ToggleNovelSource(get()) }
        addFactory { ToggleNovelSourcePin(get()) }
        addFactory { GetNovelWithChapters(get(), get()) }
        addFactory { GetDuplicateLibraryNovel(get()) }
        addFactory { SetNovelChapterFlags(get()) }
        addFactory { SetNovelDefaultChapterFlags(get(), get(), get()) }
        addFactory { UpdateNovel(get()) }
        addFactory { UpdateNovelChapter(get()) }
        addFactory { SetNovelReadStatus(get(), get(), get()) }
        addFactory { SetNovelReadingPosition(get()) }
        addFactory { CharacterPositionTracker(get()) }
        addFactory { tachiyomi.domain.entries.novel.interactor.NovelFetchInterval() }
        addFactory { GetUpcomingNovel(get()) }

        addSingletonFactory<NovelHistoryRepository> { NovelHistoryRepositoryImpl(get()) }
        addFactory { GetNovelHistory(get()) }
        addFactory { UpsertNovelHistory(get()) }
        addFactory { RemoveNovelHistory(get()) }
        addFactory { GetTotalNovelReadDuration(get()) }
        addFactory { GetNextNovelChapters(get(), get(), get()) }

        addSingletonFactory<NovelUpdatesRepository> { NovelUpdatesRepositoryImpl(get()) }
        addFactory { GetNovelUpdates(get()) }

        addSingletonFactory<NovelTrackRepository> { NovelTrackRepositoryImpl(get()) }
        addFactory { GetNovelTracks(get()) }
        addFactory { InsertNovelTrack(get()) }
        addFactory { DeleteNovelTrack(get()) }
        addFactory { AddNovelTracks(get(), get(), get(), get()) }
        addFactory { TrackNovelChapter(get(), get(), get()) }
        addFactory { ResolveTrackProgressSync() }
        addFactory { SyncNovelChapterProgressWithTrack(get(), get(), get(), get(), get()) }
        addFactory { RefreshNovelTracks(get(), get(), get(), get()) }

        addSingletonFactory<CustomButtonRepository> { CustomButtonRepositoryImpl(get()) }
        addFactory { CreateCustomButton(get()) }
        addFactory { DeleteCustomButton(get()) }
        addFactory { GetCustomButtons(get()) }
        addFactory { UpdateCustomButton(get()) }
        addFactory { ReorderCustomButton(get()) }
        addFactory { ToggleFavoriteCustomButton(get()) }

        addFactory { TrackSelect(get(), get()) }

        addSingletonFactory<MangaSavedSearchRepository> { MangaSavedSearchRepositoryImpl(get()) }
        addFactory { GetMangaSavedSearches(get()) }
        addFactory { InsertMangaSavedSearch(get()) }
        addFactory { DeleteMangaSavedSearch(get()) }

        addSingletonFactory<AnimeSavedSearchRepository> { AnimeSavedSearchRepositoryImpl(get()) }
        addFactory { GetAnimeSavedSearches(get()) }
        addFactory { InsertAnimeSavedSearch(get()) }
        addFactory { DeleteAnimeSavedSearch(get()) }

        addSingletonFactory<NovelSavedSearchRepository> { NovelSavedSearchRepositoryImpl(get()) }
        addFactory { GetNovelSavedSearches(get()) }
        addFactory { InsertNovelSavedSearch(get()) }
        addFactory { DeleteNovelSavedSearch(get()) }
    }
}
