package eu.kanade.tachiyomi.ui.browse.shows.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.shows.model.hasCustomBackground
import eu.kanade.domain.entries.shows.model.hasCustomCover
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.shows.ShowsDownloadCache
import tachiyomi.domain.entries.shows.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.injectLazy

data class ShowsMigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): ShowsMigrationFlag {
            return ShowsMigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object ShowsMigrationFlags {

    private const val EPISODES = 0b00001
    private const val COLLECTIONS = 0b00010
    private const val CUSTOM_BACKGROUND = 0b00100
    private const val CUSTOM_COVER = 0b01000
    private const val DELETE_DOWNLOADED = 0b10000

    private val coverCache: AnimeCoverCache by injectLazy()
    private val backgroundCache: AnimeBackgroundCache by injectLazy()
    private val downloadCache: ShowsDownloadCache by injectLazy()

    fun hasEpisodes(value: Int): Boolean {
        return value and EPISODES != 0
    }

    fun hasCollections(value: Int): Boolean {
        return value and COLLECTIONS != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasCustomBackground(value: Int): Boolean {
        return value and CUSTOM_BACKGROUND != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    /** Returns information about applicable flags with default selections. */
    fun getFlags(anime: Anime?, defaultSelectedBitMap: Int): List<ShowsMigrationFlag> {
        val flags = mutableListOf<ShowsMigrationFlag>()

        if (anime?.fetchType == FetchType.Episodes) {
            flags += ShowsMigrationFlag.create(EPISODES, defaultSelectedBitMap, AYMR.strings.episodes)
        }
        flags += ShowsMigrationFlag.create(COLLECTIONS, defaultSelectedBitMap, MR.strings.collections)

        if (anime != null) {
            if (anime.hasCustomCover(coverCache)) {
                flags += ShowsMigrationFlag.create(
                    CUSTOM_COVER,
                    defaultSelectedBitMap,
                    MR.strings.custom_cover,
                )
            }
            if (anime.hasCustomBackground(backgroundCache)) {
                flags += ShowsMigrationFlag.create(
                    CUSTOM_BACKGROUND,
                    defaultSelectedBitMap,
                    AYMR.strings.custom_background,
                )
            }
            if (downloadCache.getDownloadCount(anime) > 0) {
                flags += ShowsMigrationFlag.create(
                    DELETE_DOWNLOADED,
                    defaultSelectedBitMap,
                    MR.strings.delete_downloaded,
                )
            }
        }
        return flags
    }

    /** Returns a bit map of selected flags. */
    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<ShowsMigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
