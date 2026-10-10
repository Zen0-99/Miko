package eu.kanade.domain.source.service

import eu.kanade.domain.source.interactor.SetMigrateSorting
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.domain.library.model.LibraryDisplayMode

class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    // Common options

    fun sourceDisplayMode() = preferenceStore.getObject(
        "pref_display_mode_catalogue",
        LibraryDisplayMode.default,
        LibraryDisplayMode.Serializer::serialize,
        LibraryDisplayMode.Serializer::deserialize,
    )

    fun enabledLanguages() = preferenceStore.getStringSet(
        "source_languages",
        LocaleHelper.getDefaultEnabledLanguages(),
    )

    fun showNsfwSource() = preferenceStore.getBoolean("show_nsfw_source", false)

    fun migrationSortingMode() = preferenceStore.getEnum(
        "pref_migration_sorting",
        SetMigrateSorting.Mode.ALPHABETICAL,
    )

    fun migrationSortingDirection() = preferenceStore.getEnum(
        "pref_migration_direction",
        SetMigrateSorting.Direction.ASCENDING,
    )

    fun animeExtensionRepos() = preferenceStore.getStringSet("anime_extension_repos", emptySet())

    fun mangaExtensionRepos() = preferenceStore.getStringSet("extension_repos", emptySet())

    fun novelExtensionRepos() = preferenceStore.getStringSet("novel_extension_repos", emptySet())

    fun trustedExtensions() = preferenceStore.getStringSet(
        Preference.appStateKey("trusted_extensions"),
        emptySet(),
    )

    fun globalSearchFilterState() = preferenceStore.getBoolean(
        Preference.appStateKey("has_filters_toggle_state"),
        false,
    )

    // Mixture Sources

    fun disabledAnimeSources() = preferenceStore.getStringSet("hidden_anime_catalogues", emptySet())
    fun disabledMangaSources() = preferenceStore.getStringSet("hidden_catalogues", emptySet())
    fun disabledNovelSources() = preferenceStore.getStringSet("hidden_novel_catalogues", emptySet())

    /** Per-work carrier preference (16-04) — `workId|pkgName` entries.
     *  The canonical router ranks the preferred carrier above
     *  stickiness/tier; the source picker writes it. */
    fun preferredWorkCarriers() = preferenceStore.getStringSet("preferred_work_carriers", emptySet())

    fun preferredCarrierFor(workId: String): String? = preferredWorkCarriers().get()
        .firstNotNullOfOrNull { e ->
            e.split('|', limit = 2).takeIf { it[0] == workId }?.get(1)
        }

    fun setPreferredCarrier(workId: String, pkgName: String?) {
        val cur = preferredWorkCarriers().get()
            .filterNotTo(mutableSetOf()) { it.substringBefore('|') == workId }
        if (pkgName != null) cur += "$workId|$pkgName"
        preferredWorkCarriers().set(cur)
    }

    /** Per-chapter carrier override (16-04) — `workId|seq|pkgName`
     *  entries; ranks above [preferredCarrierFor] for that chapter's
     *  routing, cleared back to the work default by writing null. */
    fun preferredChapterCarriers() =
        preferenceStore.getStringSet("preferred_chapter_carriers", emptySet())

    fun preferredChapterCarrier(workId: String, seq: Int): String? =
        preferredChapterCarriers().get().firstNotNullOfOrNull { e ->
            e.split('|', limit = 3)
                .takeIf { it[0] == workId && it[1].toIntOrNull() == seq }
                ?.get(2)
        }

    fun setPreferredChapterCarrier(workId: String, seq: Int, pkgName: String?) {
        val cur = preferredChapterCarriers().get()
            .filterNotTo(mutableSetOf()) {
                it.substringBefore('|') == workId &&
                    it.substringAfter('|').substringBefore('|').toIntOrNull() == seq
            }
        if (pkgName != null) cur += "$workId|$seq|$pkgName"
        preferredChapterCarriers().set(cur)
    }

    /** Last successful canonize POST per work — `workId|epochMs` entries.
     *  Gates CanonizeWork's rebuild; the chapter/episode read caches can't
     *  gate it because their reads refresh the same timestamp the gate
     *  checks — UI reads would starve feed posts forever (canon froze at
     *  whatever the last canonize produced). */
    fun canonizeRanAt() = preferenceStore.getStringSet("canonize_ran_at_v1", emptySet())

    fun canonizeRanAtFor(workId: String): Long = canonizeRanAt().get()
        .firstNotNullOfOrNull { e ->
            e.split('|', limit = 2)
                .takeIf { it[0] == workId }
                ?.get(1)
                ?.toLongOrNull()
        } ?: 0L

    fun markCanonizeRan(workId: String) {
        val cur = canonizeRanAt().get()
            .filterNotTo(mutableSetOf()) { it.substringBefore('|') == workId }
        cur += "$workId|${System.currentTimeMillis()}"
        canonizeRanAt().set(cur)
    }

    fun incognitoAnimeExtensions() = preferenceStore.getStringSet("incognito_anime_extensions", emptySet())
    fun incognitoMangaExtensions() = preferenceStore.getStringSet("incognito_manga_extensions", emptySet())
    fun incognitoNovelExtensions() = preferenceStore.getStringSet("incognito_novel_extensions", emptySet())

    fun incognitoPolicy() = preferenceStore.getEnum(
        "incognito_policy",
        eu.kanade.domain.source.model.IncognitoPolicy.MANUAL_ONLY,
    )

    fun pinnedAnimeSources() = preferenceStore.getStringSet("pinned_anime_catalogues", emptySet())
    fun pinnedMangaSources() = preferenceStore.getStringSet("pinned_catalogues", emptySet())
    fun pinnedNovelSources() = preferenceStore.getStringSet("pinned_novel_catalogues", emptySet())

    fun lastUsedAnimeSource() = preferenceStore.getLong(
        Preference.appStateKey("last_anime_catalogue_source"),
        -1,
    )
    fun lastUsedMangaSource() = preferenceStore.getLong(
        Preference.appStateKey("last_catalogue_source"),
        -1,
    )
    fun lastUsedNovelSource() = preferenceStore.getLong(
        Preference.appStateKey("last_novel_catalogue_source"),
        -1,
    )

    fun animeExtensionUpdatesCount() = preferenceStore.getInt("animeext_updates_count", 0)
    fun mangaExtensionUpdatesCount() = preferenceStore.getInt("ext_updates_count", 0)
    fun novelExtensionUpdatesCount() = preferenceStore.getInt("novel_ext_updates_count", 0)

    // Default repo seeding flags
    fun novelDefaultRepoSeeded() = preferenceStore.getBoolean("novel_default_repo_seeded", false)
    fun novelPluginDefaultRepoSeeded() = preferenceStore.getBoolean("novel_plugin_default_repo_seeded", false)

    // Browse card design
    fun browseCardDesign() = preferenceStore.getBoolean("browse_card_design", false)
    fun browseCardColumns() = preferenceStore.getInt("browse_card_columns", 2)

    fun hideInAnimeLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_anime_library_items",
        false,
    )

    fun hideInMangaLibraryItems() = preferenceStore.getBoolean(
        "browse_hide_in_library_items",
        false,
    )

    fun swipeToHideSource() = preferenceStore.getBoolean("swipe_to_hide_source", false)

    // SY -->

    // fun enableSourceBlacklist() = preferenceStore.getBoolean("eh_enable_source_blacklist", true)

    // fun sourcesTabCollections() = preferenceStore.getStringSet("sources_tab_categories", mutableSetOf())

    // fun sourcesTabCollectionsFilter() = preferenceStore.getBoolean("sources_tab_categories_filter", false)

    // fun sourcesTabSourcesInCollections() = preferenceStore.getStringSet("sources_tab_source_categories", mutableSetOf())

    fun dataSaver() = preferenceStore.getEnum("data_saver", DataSaver.NONE)

    fun dataSaverIgnoreJpeg() = preferenceStore.getBoolean("ignore_jpeg", false)

    fun dataSaverIgnoreGif() = preferenceStore.getBoolean("ignore_gif", true)

    fun dataSaverImageQuality() = preferenceStore.getInt("data_saver_image_quality", 80)

    fun dataSaverImageFormatJpeg() = preferenceStore.getBoolean(
        "data_saver_image_format_jpeg",
        false,
    )

    fun dataSaverServer() = preferenceStore.getString("data_saver_server", "")

    fun dataSaverColorBW() = preferenceStore.getBoolean("data_saver_color_bw", false)

    fun dataSaverExcludedSources() = preferenceStore.getStringSet("data_saver_excluded", emptySet())

    fun dataSaverDownloader() = preferenceStore.getBoolean("data_saver_downloader", true)

    enum class DataSaver {
        NONE,
        BANDWIDTH_HERO,
        WSRV_NL,
        RESMUSH_IT,
    }
    // SY <--

    // Suggestions

    fun entrySuggestionsEnabled() = preferenceStore.getBoolean("entry_suggestions_enabled", true)

    fun entrySuggestionsExpandInline() = preferenceStore.getBoolean("entry_suggestions_expand_inline", true)

    fun entrySuggestionsShowInOverflow() = preferenceStore.getBoolean("entry_suggestions_show_in_overflow", true)

    /**
     * Suggestion source toggles for NOVEL catalogues.
     *
     * Each flag independently controls whether the corresponding source
     * participates in the "Similar works" pipeline for novels.
     */
    fun suggestionsUseMangaUpdatesNovel() = preferenceStore.getBoolean(
        "suggestions_use_mangaupdates_novel",
        true,
    )

    fun suggestionsUseNovelUpdates() = preferenceStore.getBoolean(
        "suggestions_use_novelupdates",
        true,
    )

    fun suggestionsPopularBackfillEnabled() = preferenceStore.getBoolean(
        "suggestions_popular_backfill_enabled",
        true,
    )
}
