package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.fastMap
import androidx.core.content.ContextCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.collection.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.PreferenceItem
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.ui.collection.CollectionsTab
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.manga.interactor.ResetMangaCollectionFlags
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_NETWORK_NOT_METERED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_COMPLETED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_NON_VIEWED
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_OUTSIDE_RELEASE_PERIOD
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_EXISTING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_NEW
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_EPISODE_SEEN_EXISTING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_EPISODE_SEEN_NEW
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = MR.strings.pref_category_library

    @Composable
    @ReadOnlyComposable
    override fun getSubtitleRes() = AYMR.strings.pref_library_summary

    @Composable
    override fun getPreferences(): List<Preference> {
        val getCollections = remember { Injekt.get<GetMangaCollections>() }
        val allCollections by getCollections.subscribe().collectAsState(initial = emptyList())
        val getAnimeCollections = remember { Injekt.get<GetAnimeCollections>() }
        val allAnimeCollections by getAnimeCollections.subscribe().collectAsState(initial = emptyList())
        val getNovelCollections = remember { Injekt.get<tachiyomi.domain.collection.novel.interactor.GetNovelCollections>() }
        val allNovelCollections by getNovelCollections.subscribe().collectAsState(initial = emptyList())
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        return listOf(
            getCollectionsGroup(
                LocalNavigator.currentOrThrow,
                allCollections,
                allAnimeCollections,
                allNovelCollections,
                libraryPreferences,
            ),
            getGlobalUpdateGroup(allCollections, allAnimeCollections, allNovelCollections, libraryPreferences),
            getSeasonBehaviorGroup(libraryPreferences),
            getAnimeBehaviorGroup(libraryPreferences),
            getBehaviorGroup(libraryPreferences),
        )
    }

    @Composable
    private fun getCollectionsGroup(
        navigator: Navigator,
        allCollections: List<Collection>,
        allAnimeCollections: List<Collection>,
        allNovelCollections: List<Collection>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val userCollectionsCount = allCollections.filterNot(Collection::isSystemCollection).size
        val userAnimeCollectionsCount = allAnimeCollections.filterNot(Collection::isSystemCollection).size
        val userNovelCollectionsCount = allNovelCollections.filterNot(Collection::isSystemCollection).size

        // For default collection
        val mangaIds = listOf(libraryPreferences.defaultMangaCollection().defaultValue()) +
            allCollections.fastMap { it.id.toInt() }
        val animeIds = listOf(libraryPreferences.defaultAnimeCollection().defaultValue()) +
            allAnimeCollections.fastMap { it.id.toInt() }
        val novelIds = listOf(libraryPreferences.defaultNovelCollection().defaultValue()) +
            allNovelCollections.fastMap { it.id.toInt() }

        val mangaLabels = listOf(stringResource(MR.strings.default_collection_summary)) +
            allCollections.fastMap { it.visualName }
        val animeLabels = listOf(stringResource(MR.strings.default_collection_summary)) +
            allAnimeCollections.fastMap { it.visualName }
        val novelLabels = listOf(stringResource(MR.strings.default_collection_summary)) +
            allNovelCollections.fastMap { it.visualName }

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.general_collections),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.action_edit_anime_collections),
                    subtitle = pluralStringResource(
                        MR.plurals.num_collections,
                        count = userAnimeCollectionsCount,
                        userAnimeCollectionsCount,
                    ),
                    onClick = { navigator.push(CollectionsTab) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.defaultAnimeCollection(),
                    entries = animeIds.zip(animeLabels).toMap().toImmutableMap(),
                    title = stringResource(AYMR.strings.default_anime_collection),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.action_edit_manga_collections),
                    subtitle = pluralStringResource(
                        MR.plurals.num_collections,
                        count = userCollectionsCount,
                        userCollectionsCount,
                    ),
                    onClick = {
                        navigator.push(CollectionsTab)
                        CollectionsTab.showMangaCollection()
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.defaultMangaCollection(),
                    entries = mangaIds.zip(mangaLabels).toMap().toImmutableMap(),
                    title = stringResource(AYMR.strings.default_manga_collection),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.action_edit_novel_collections),
                    subtitle = pluralStringResource(
                        MR.plurals.num_collections,
                        count = userNovelCollectionsCount,
                        userNovelCollectionsCount,
                    ),
                    onClick = {
                        navigator.push(CollectionsTab)
                        CollectionsTab.showNovelCollection()
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.defaultNovelCollection(),
                    entries = novelIds.zip(novelLabels).toMap().toImmutableMap(),
                    title = stringResource(AYMR.strings.default_novel_collection),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.perCollectionDisplaySettings(),
                    title = stringResource(MR.strings.per_collection_display_settings),
                    onValueChanged = {
                        if (!it) {
                            scope.launch {
                                Injekt.get<ResetMangaCollectionFlags>().await()
                                Injekt.get<tachiyomi.domain.collection.anime.interactor.ResetAnimeCollectionFlags>().await()
                                Injekt.get<tachiyomi.domain.collection.novel.interactor.ResetNovelCollectionFlags>().await()
                            }
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.hideHiddenCollectionsSettings(),
                    title = stringResource(AYMR.strings.pref_category_hide_hidden),
                ),
            ),
        )
    }

    @Composable
    private fun getGlobalUpdateGroup(
        allMangaCollections: List<Collection>,
        allAnimeCollections: List<Collection>,
        allNovelCollections: List<Collection>,
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current

        val autoUpdateIntervalPref = libraryPreferences.autoUpdateInterval()
        val autoUpdateInterval by autoUpdateIntervalPref.collectAsState()

        val animeAutoUpdateCollectionsPref = libraryPreferences.animeUpdateCollections()
        val animeAutoUpdateCollectionsExcludePref =
            libraryPreferences.animeUpdateCollectionsExclude()

        val includedAnime by animeAutoUpdateCollectionsPref.collectAsState()
        val excludedAnime by animeAutoUpdateCollectionsExcludePref.collectAsState()
        var showAnimeCollectionsDialog by rememberSaveable { mutableStateOf(false) }
        if (showAnimeCollectionsDialog) {
            TriStateListDialog(
                title = stringResource(AYMR.strings.anime_collections),
                message = stringResource(AYMR.strings.pref_anime_library_update_collections_details),
                items = allAnimeCollections,
                initialChecked = includedAnime.mapNotNull { id -> allAnimeCollections.find { it.id.toString() == id } },
                initialInversed = excludedAnime.mapNotNull { id -> allAnimeCollections.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showAnimeCollectionsDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    animeAutoUpdateCollectionsPref.set(newIncluded.map { it.id.toString() }.toSet())
                    animeAutoUpdateCollectionsExcludePref.set(
                        newExcluded.map { it.id.toString() }
                            .toSet(),
                    )
                    showAnimeCollectionsDialog = false
                },
            )
        }

        val autoUpdateCollectionsPref = libraryPreferences.mangaUpdateCollections()
        val autoUpdateCollectionsExcludePref =
            libraryPreferences.mangaUpdateCollectionsExclude()

        val includedManga by autoUpdateCollectionsPref.collectAsState()
        val excludedManga by autoUpdateCollectionsExcludePref.collectAsState()
        var showMangaCollectionsDialog by rememberSaveable { mutableStateOf(false) }
        if (showMangaCollectionsDialog) {
            TriStateListDialog(
                title = stringResource(AYMR.strings.manga_collections),
                message = stringResource(AYMR.strings.pref_manga_library_update_collections_details),
                items = allMangaCollections,
                initialChecked = includedManga.mapNotNull { id -> allMangaCollections.find { it.id.toString() == id } },
                initialInversed = excludedManga.mapNotNull { id -> allMangaCollections.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showMangaCollectionsDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    autoUpdateCollectionsPref.set(newIncluded.map { it.id.toString() }.toSet())
                    autoUpdateCollectionsExcludePref.set(
                        newExcluded.map { it.id.toString() }
                            .toSet(),
                    )
                    showMangaCollectionsDialog = false
                },
            )
        }

        val novelAutoUpdateCollectionsPref = libraryPreferences.novelUpdateCollections()
        val novelAutoUpdateCollectionsExcludePref =
            libraryPreferences.novelUpdateCollectionsExclude()

        val includedNovel by novelAutoUpdateCollectionsPref.collectAsState()
        val excludedNovel by novelAutoUpdateCollectionsExcludePref.collectAsState()
        var showNovelCollectionsDialog by rememberSaveable { mutableStateOf(false) }
        if (showNovelCollectionsDialog) {
            TriStateListDialog(
                title = stringResource(AYMR.strings.novel_collections),
                message = stringResource(AYMR.strings.pref_novel_library_update_collections_details),
                items = allNovelCollections,
                initialChecked = includedNovel.mapNotNull { id -> allNovelCollections.find { it.id.toString() == id } },
                initialInversed = excludedNovel.mapNotNull { id -> allNovelCollections.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showNovelCollectionsDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    novelAutoUpdateCollectionsPref.set(newIncluded.map { it.id.toString() }.toSet())
                    novelAutoUpdateCollectionsExcludePref.set(
                        newExcluded.map { it.id.toString() }
                            .toSet(),
                    )
                    showNovelCollectionsDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_library_update),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = autoUpdateIntervalPref,
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.update_never),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        72 to stringResource(MR.strings.update_72hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(MR.strings.pref_library_update_interval),
                    onValueChanged = {
                        MangaLibraryUpdateJob.setupTask(context, it)
                        AnimeLibraryUpdateJob.setupTask(context, it)
                        eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.autoUpdateDeviceRestrictions(),
                    entries = persistentMapOf(
                        DEVICE_ONLY_ON_WIFI to stringResource(MR.strings.connected_to_wifi),
                        DEVICE_NETWORK_NOT_METERED to stringResource(MR.strings.network_not_metered),
                        DEVICE_CHARGING to stringResource(MR.strings.charging),
                    ),
                    title = stringResource(MR.strings.pref_library_update_restriction),
                    subtitle = stringResource(MR.strings.restrictions),
                    enabled = autoUpdateInterval > 0,
                    onValueChanged = {
                        // Post to event looper to allow the preference to be updated.
                        ContextCompat.getMainExecutor(context).execute {
                            MangaLibraryUpdateJob.setupTask(context)
                            AnimeLibraryUpdateJob.setupTask(context)
                            eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob.setupTask(context)
                        }
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.anime_collections),
                    subtitle = getCollectionsLabel(
                        allCollections = allAnimeCollections,
                        included = includedAnime,
                        excluded = excludedAnime,
                    ),
                    onClick = { showAnimeCollectionsDialog = true },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.manga_collections),
                    subtitle = getCollectionsLabel(
                        allCollections = allMangaCollections,
                        included = includedManga,
                        excluded = excludedManga,
                    ),
                    onClick = { showMangaCollectionsDialog = true },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.novel_collections),
                    subtitle = getCollectionsLabel(
                        allCollections = allNovelCollections,
                        included = includedNovel,
                        excluded = excludedNovel,
                    ),
                    onClick = { showNovelCollectionsDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.autoUpdateMetadata(),
                    title = stringResource(MR.strings.pref_library_update_refresh_metadata),
                    subtitle = stringResource(MR.strings.pref_library_update_refresh_metadata_summary),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.autoUpdateItemRestrictions(),
                    entries = persistentMapOf(
                        ENTRY_HAS_UNVIEWED to stringResource(AYMR.strings.pref_update_only_completely_read),
                        ENTRY_NON_VIEWED to stringResource(MR.strings.pref_update_only_started),
                        ENTRY_NON_COMPLETED to stringResource(MR.strings.pref_update_only_non_completed),
                        ENTRY_OUTSIDE_RELEASE_PERIOD to stringResource(MR.strings.pref_update_only_in_release_period),
                    ),
                    title = stringResource(MR.strings.pref_library_update_smart_update),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.newShowUpdatesCount(),
                    title = stringResource(AYMR.strings.pref_library_update_show_tab_badge),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.showUpdateProgressOverlay(),
                    title = stringResource(AYMR.strings.pref_show_update_progress_overlay),
                    subtitle = stringResource(AYMR.strings.pref_show_update_progress_overlay_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getSeasonBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_library_season),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateSeasonOnRefresh(),
                    title = stringResource(AYMR.strings.pref_update_seasons_refresh),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = libraryPreferences.updateSeasonOnLibraryUpdate(),
                    title = stringResource(AYMR.strings.pref_update_seasons_update),
                ),
            ),
        )
    }

    @Composable
    private fun getBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_behavior),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeChapterStartAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to
                            stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeChapterEndAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to
                            stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateReadChapterAsRead(),
                    entries = persistentMapOf(
                        MARK_DUPLICATE_CHAPTER_READ_EXISTING to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_existing),
                        MARK_DUPLICATE_CHAPTER_READ_NEW to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_new),
                    ),
                    title = stringResource(MR.strings.pref_mark_duplicate_read_chapter_read),
                ),
            ),
        )
    }

    @Composable
    private fun getAnimeBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_behavior_episode),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeEpisodeStartAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.EpisodeSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                            stringResource(AYMR.strings.action_bookmark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark to
                            stringResource(AYMR.strings.action_fillermark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                            stringResource(AYMR.strings.action_mark_as_seen),
                        LibraryPreferences.EpisodeSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(AYMR.strings.pref_episode_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeEpisodeEndAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.EpisodeSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                            stringResource(AYMR.strings.action_bookmark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark to
                            stringResource(AYMR.strings.action_fillermark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                            stringResource(AYMR.strings.action_mark_as_seen),
                        LibraryPreferences.EpisodeSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(AYMR.strings.pref_episode_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateSeenEpisodeAsSeen(),
                    entries = persistentMapOf(
                        MARK_DUPLICATE_EPISODE_SEEN_EXISTING to
                            stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen_existing),
                        MARK_DUPLICATE_EPISODE_SEEN_NEW to
                            stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen_new),
                    ),
                    title = stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen),
                ),
            ),
        )
    }
}
