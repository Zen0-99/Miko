package eu.kanade.tachiyomi.ui.browse.novel.migration

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.entries.novel.model.hasCustomCover
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import eu.kanade.tachiyomi.data.cache.NovelCoverCache
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import uy.kohesive.injekt.injectLazy

data class NovelMigrationFlag(
    val flag: Int,
    val isDefaultSelected: Boolean,
    val titleId: StringResource,
) {
    companion object {
        fun create(flag: Int, defaultSelectionMap: Int, titleId: StringResource): NovelMigrationFlag {
            return NovelMigrationFlag(
                flag = flag,
                isDefaultSelected = defaultSelectionMap and flag != 0,
                titleId = titleId,
            )
        }
    }
}

object NovelMigrationFlags {

    private const val CHAPTERS = 0b00001
    private const val COLLECTIONS = 0b00010
    private const val CUSTOM_COVER = 0b01000
    private const val DELETE_DOWNLOADED = 0b10000

    private val coverCache: NovelCoverCache by injectLazy()
    private val downloadCache: NovelDownloadCache by injectLazy()

    fun hasChapters(value: Int): Boolean {
        return value and CHAPTERS != 0
    }

    fun hasCollections(value: Int): Boolean {
        return value and COLLECTIONS != 0
    }

    fun hasCustomCover(value: Int): Boolean {
        return value and CUSTOM_COVER != 0
    }

    fun hasDeleteDownloaded(value: Int): Boolean {
        return value and DELETE_DOWNLOADED != 0
    }

    fun getFlags(novel: Novel?, defaultSelectedBitMap: Int): List<NovelMigrationFlag> {
        val flags = mutableListOf<NovelMigrationFlag>()
        flags += NovelMigrationFlag.create(CHAPTERS, defaultSelectedBitMap, MR.strings.chapters)
        flags += NovelMigrationFlag.create(COLLECTIONS, defaultSelectedBitMap, MR.strings.collections)

        if (novel != null) {
            if (novel.hasCustomCover(coverCache)) {
                flags += NovelMigrationFlag.create(
                    CUSTOM_COVER,
                    defaultSelectedBitMap,
                    MR.strings.custom_cover,
                )
            }
            if (downloadCache.getDownloadCount(novel) > 0) {
                flags += NovelMigrationFlag.create(
                    DELETE_DOWNLOADED,
                    defaultSelectedBitMap,
                    MR.strings.delete_downloaded,
                )
            }
        }
        return flags
    }

    fun getSelectedFlagsBitMap(
        selectedFlags: List<Boolean>,
        flags: List<NovelMigrationFlag>,
    ): Int {
        return selectedFlags
            .zip(flags)
            .filter { (isSelected, _) -> isSelected }
            .map { (_, flag) -> flag.flag }
            .reduceOrNull { acc, mask -> acc or mask } ?: 0
    }
}
