package eu.kanade.tachiyomi.util.novel

import androidx.annotation.ColorInt
import eu.kanade.tachiyomi.util.EntryCoverMetadata

/**
 * Deprecated — use [eu.kanade.tachiyomi.util.EntryCoverMetadata] with
 * [EntryCoverMetadata.EntryType.NOVEL] instead. Kept as a thin delegate for
 * backward compatibility.
 */
object NovelCoverMetadata {

    private val type = EntryCoverMetadata.EntryType.NOVEL

    @ColorInt
    fun getBaseColor(novelId: Long?): Int? = EntryCoverMetadata.getBaseColor(type, novelId)

    fun setBaseColor(novelId: Long?, @ColorInt color: Int) =
        EntryCoverMetadata.setBaseColor(type, novelId, color)

    fun remove(novelId: Long?) = EntryCoverMetadata.remove(type, novelId)

    fun savePrefs() = EntryCoverMetadata.savePrefs(type)
}
