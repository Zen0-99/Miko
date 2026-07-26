package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastMap
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.collection.visualName
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.TriStateListDialog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.domain.collection.anime.interactor.GetAnimeCollections
import tachiyomi.domain.collection.manga.interactor.GetMangaCollections
import tachiyomi.domain.collection.model.Collection
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.OutlinedNumericChooser
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsDownloadScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @ReadOnlyComposable
    @Composable
    override fun getSubtitleRes() = MR.strings.pref_downloads_summary

    @Composable
    override fun getPreferences(): List<Preference> {
        val getMangaCollections = remember { Injekt.get<GetMangaCollections>() }
        val allMangaCollections by getMangaCollections.subscribe().collectAsState(initial = emptyList())
        val getAnimeCollections = remember { Injekt.get<GetAnimeCollections>() }
        val allAnimeCollections by getAnimeCollections.subscribe().collectAsState(initial = emptyList())
        val downloadPreferences = remember { Injekt.get<DownloadPreferences>() }
        val basePreferences = remember { Injekt.get<BasePreferences>() }
        val speedLimit by downloadPreferences.downloadSpeedLimit().collectAsState()
        var currentSpeedLimit by remember { mutableIntStateOf(speedLimit) }
        var showDownloadLimitDialog by rememberSaveable { mutableStateOf(false) }
        if (showDownloadLimitDialog) {
            DownloadLimitDialog(
                initialValue = currentSpeedLimit,
                onDismissRequest = { showDownloadLimitDialog = false },
                onValueChanged = {
                    currentSpeedLimit = it
                },
                onConfirm = {
                    downloadPreferences.downloadSpeedLimit().set(currentSpeedLimit)
                    showDownloadLimitDialog = false
                },
            )
        }
        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.downloadOnlyOverWifi(),
                title = stringResource(MR.strings.connected_to_wifi),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.download_speed_limit),
                subtitle = if (speedLimit == 0) {
                    stringResource(MR.strings.off)
                } else {
                    "$speedLimit KiB/s"
                },
                onClick = { showDownloadLimitDialog = true },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.saveChaptersAsCBZ(),
                title = stringResource(MR.strings.save_chapter_as_cbz),
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = downloadPreferences.splitTallImages(),
                title = stringResource(MR.strings.split_tall_images),
                subtitle = stringResource(MR.strings.split_tall_images_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = downloadPreferences.numberOfDownloads(),
                entries = (1..5).associateWith { it.toString() }.toImmutableMap(),
                title = stringResource(AYMR.strings.pref_download_slots),
            ),
            Preference.PreferenceItem.InfoPreference(stringResource(AYMR.strings.download_slots_info)),
            getDeleteChaptersGroup(
                downloadPreferences = downloadPreferences,
                animeCollections = allAnimeCollections.toImmutableList(),
                mangaCollections = allMangaCollections.toImmutableList(),
            ),
            getAutoDownloadGroup(
                downloadPreferences = downloadPreferences,
                allAnimeCollections = allAnimeCollections.toImmutableList(),
                allMangaCollections = allMangaCollections.toImmutableList(),
            ),
            getDownloadAheadGroup(downloadPreferences = downloadPreferences),
            getExternalDownloaderGroup(
                downloadPreferences = downloadPreferences,
                basePreferences = basePreferences,
            ),
        )
    }

    @Composable
    private fun getDeleteChaptersGroup(
        downloadPreferences: DownloadPreferences,
        animeCollections: ImmutableList<Collection>,
        mangaCollections: ImmutableList<Collection>,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_delete_chapters),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeAfterMarkedAsRead(),
                    title = stringResource(AYMR.strings.pref_remove_after_marked_as_read),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.removeAfterReadSlots(),
                    entries = persistentMapOf(
                        -1 to stringResource(MR.strings.disabled),
                        0 to stringResource(MR.strings.last_read_chapter),
                        1 to stringResource(MR.strings.second_to_last),
                        2 to stringResource(MR.strings.third_to_last),
                        3 to stringResource(MR.strings.fourth_to_last),
                        4 to stringResource(MR.strings.fifth_to_last),
                    ),
                    title = stringResource(AYMR.strings.pref_remove_after_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.removeBookmarkedChapters(),
                    title = stringResource(AYMR.strings.pref_remove_bookmarked_chapters),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadPreferences.downloadFillermarkedItems(),
                    title = stringResource(AYMR.strings.pref_download_fillermarked_items),
                ),
                getExcludedAnimeCollectionsPreference(
                    downloadPreferences = downloadPreferences,
                    collections = { animeCollections },
                ),
                getExcludedCollectionsPreference(
                    downloadPreferences = downloadPreferences,
                    collections = { mangaCollections },
                ),
            ),
        )
    }

    @Composable
    private fun getExcludedCollectionsPreference(
        downloadPreferences: DownloadPreferences,
        collections: () -> List<Collection>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeCollections(),
            entries = collections()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
            title = stringResource(AYMR.strings.pref_remove_exclude_collections_manga),
        )
    }

    @Composable
    private fun getExcludedAnimeCollectionsPreference(
        downloadPreferences: DownloadPreferences,
        collections: () -> List<Collection>,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = downloadPreferences.removeExcludeAnimeCollections(),
            entries = collections()
                .associate { it.id.toString() to it.visualName }
                .toImmutableMap(),
            title = stringResource(AYMR.strings.pref_remove_exclude_collections_anime),
        )
    }

    @Composable
    private fun getAutoDownloadGroup(
        downloadPreferences: DownloadPreferences,
        allAnimeCollections: ImmutableList<Collection>,
        allMangaCollections: ImmutableList<Collection>,
    ): Preference.PreferenceGroup {
        val downloadNewEpisodesPref = downloadPreferences.downloadNewEpisodes()
        val downloadNewUnseenEpisodesOnlyPref = downloadPreferences.downloadNewUnseenEpisodesOnly()
        val downloadNewEpisodeCollectionsPref = downloadPreferences.downloadNewEpisodeCollections()
        val downloadNewEpisodeCollectionsExcludePref = downloadPreferences.downloadNewEpisodeCollectionsExclude()

        val downloadNewEpisodes by downloadNewEpisodesPref.collectAsState()

        val includedAnime by downloadNewEpisodeCollectionsPref.collectAsState()
        val excludedAnime by downloadNewEpisodeCollectionsExcludePref.collectAsState()
        var showAnimeDialog by rememberSaveable { mutableStateOf(false) }
        if (showAnimeDialog) {
            TriStateListDialog(
                title = stringResource(AYMR.strings.anime_collections),
                message = stringResource(MR.strings.pref_download_new_collections_details),
                items = allAnimeCollections,
                initialChecked = includedAnime.mapNotNull { id -> allAnimeCollections.find { it.id.toString() == id } },
                initialInversed = excludedAnime.mapNotNull { id -> allAnimeCollections.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showAnimeDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewEpisodeCollectionsPref.set(
                        newIncluded.fastMap { it.id.toString() }.toSet(),
                    )
                    downloadNewEpisodeCollectionsExcludePref.set(
                        newExcluded.fastMap { it.id.toString() }.toSet(),
                    )
                    showAnimeDialog = false
                },
            )
        }

        val downloadNewChaptersPref = downloadPreferences.downloadNewChapters()
        val downloadNewUnreadChaptersOnlyPref = downloadPreferences.downloadNewUnreadChaptersOnly()
        val downloadNewChapterCollectionsPref = downloadPreferences.downloadNewChapterCollections()
        val downloadNewChapterCollectionsExcludePref = downloadPreferences.downloadNewChapterCollectionsExclude()

        val downloadNewChapters by downloadNewChaptersPref.collectAsState()

        val included by downloadNewChapterCollectionsPref.collectAsState()
        val excluded by downloadNewChapterCollectionsExcludePref.collectAsState()
        var showDialog by rememberSaveable { mutableStateOf(false) }
        if (showDialog) {
            TriStateListDialog(
                title = stringResource(AYMR.strings.manga_collections),
                message = stringResource(MR.strings.pref_download_new_collections_details),
                items = allMangaCollections,
                initialChecked = included.mapNotNull { id -> allMangaCollections.find { it.id.toString() == id } },
                initialInversed = excluded.mapNotNull { id -> allMangaCollections.find { it.id.toString() == id } },
                itemLabel = { it.visualName },
                onDismissRequest = { showDialog = false },
                onValueChanged = { newIncluded, newExcluded ->
                    downloadNewChapterCollectionsPref.set(
                        newIncluded.fastMap { it.id.toString() }.toSet(),
                    )
                    downloadNewChapterCollectionsExcludePref.set(
                        newExcluded.fastMap { it.id.toString() }.toSet(),
                    )
                    showDialog = false
                },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_auto_download),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewEpisodesPref,
                    title = stringResource(AYMR.strings.pref_download_new_episodes),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnseenEpisodesOnlyPref,
                    title = stringResource(AYMR.strings.pref_download_new_unseen_episodes_only),
                    enabled = downloadNewEpisodes,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.anime_collections),
                    subtitle = getCollectionsLabel(
                        allCollections = allAnimeCollections,
                        included = includedAnime,
                        excluded = excludedAnime,
                    ),
                    enabled = downloadNewEpisodes,
                    onClick = { showAnimeDialog = true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewChaptersPref,
                    title = stringResource(MR.strings.pref_download_new),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = downloadNewUnreadChaptersOnlyPref,
                    title = stringResource(MR.strings.pref_download_new_unread_chapters_only),
                    enabled = downloadNewChapters,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.manga_collections),
                    subtitle = getCollectionsLabel(
                        allCollections = allMangaCollections,
                        included = included,
                        excluded = excluded,
                    ),
                    enabled = downloadNewChapters,
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun getDownloadAheadGroup(
        downloadPreferences: DownloadPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.download_ahead),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileWatching(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(AYMR.plurals.next_unseen_episodes, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.auto_download_while_watching),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = downloadPreferences.autoDownloadWhileReading(),
                    entries = listOf(0, 2, 3, 5, 10)
                        .associateWith {
                            if (it == 0) {
                                stringResource(MR.strings.disabled)
                            } else {
                                pluralStringResource(MR.plurals.next_unread_chapters, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.auto_download_while_reading),
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(AYMR.strings.download_ahead_info),
                ),
            ),
        )
    }

    @Composable
    private fun getExternalDownloaderGroup(
        downloadPreferences: DownloadPreferences,
        basePreferences: BasePreferences,
    ): Preference.PreferenceGroup {
        val useExternalDownloader = downloadPreferences.useExternalDownloader()
        val externalDownloaderPreference = downloadPreferences.externalDownloaderSelection()

        val pm = basePreferences.context.packageManager
        val installedPackages = pm.getInstalledPackages(0)
        val supportedDownloaders = installedPackages.filter {
            when (it.packageName) {
                "idm.internet.download.manager" -> true
                "idm.internet.download.manager.plus" -> true
                "idm.internet.download.manager.adm.lite" -> true
                "com.dv.adm" -> true
                else -> false
            }
        }
        val packageNames = supportedDownloaders.map { it.packageName }
        val packageNamesReadable = supportedDownloaders
            .map { pm.getApplicationLabel(it.applicationInfo!!).toString() }

        val packageNamesMap: Map<String, String> =
            mapOf("" to "None") + packageNames.zip(packageNamesReadable).toMap()

        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_category_external_downloader),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useExternalDownloader,
                    title = stringResource(AYMR.strings.pref_use_external_downloader),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = externalDownloaderPreference,
                    entries = packageNamesMap.toPersistentMap(),
                    title = stringResource(AYMR.strings.pref_external_downloader_selection),
                ),
            ),
        )
    }

    @Composable
    private fun DownloadLimitDialog(
        initialValue: Int,
        onDismissRequest: () -> Unit,
        onValueChanged: (newValue: Int) -> Unit,
        onConfirm: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(AYMR.strings.download_speed_limit)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        OutlinedNumericChooser(
                            label = stringResource(AYMR.strings.download_speed_limit),
                            placeholder = "0",
                            suffix = "KiB/s",
                            value = initialValue,
                            step = 100,
                            min = 0,
                            onValueChanged = onValueChanged,
                        )
                    }
                    Text(text = stringResource(AYMR.strings.download_speed_limit_hint))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }
}
