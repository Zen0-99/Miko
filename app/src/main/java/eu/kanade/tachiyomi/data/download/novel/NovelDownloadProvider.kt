package eu.kanade.tachiyomi.data.download.novel

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.util.storage.DiskUtil
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.chapter.model.NovelChapter
import tachiyomi.domain.storage.service.StorageManager
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class NovelDownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager = Injekt.get(),
) {

    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    internal fun getNovelDir(novelTitle: String, source: NovelSource): UniFile {
        try {
            return downloadsDir!!
                .createDirectory(getSourceDirName(source))!!
                .createDirectory(getNovelDirName(novelTitle))!!
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Invalid download directory" }
            throw Exception(
                context.stringResource(
                    MR.strings.invalid_location,
                    downloadsDir?.displayablePath ?: "",
                ),
            )
        }
    }

    fun findSourceDir(source: NovelSource): UniFile? {
        return downloadsDir?.findFile(getSourceDirName(source))
    }

    fun findNovelDir(novelTitle: String, source: NovelSource): UniFile? {
        val sourceDir = findSourceDir(source)
        return sourceDir?.findFile(getNovelDirName(novelTitle))
    }

    fun findChapterDir(
        chapterName: String,
        chapterScanlator: String?,
        novelTitle: String,
        source: NovelSource,
    ): UniFile? {
        val novelDir = findNovelDir(novelTitle, source)
        return getValidChapterDirNames(chapterName, chapterScanlator).asSequence()
            .mapNotNull { novelDir?.findFile(it) }
            .firstOrNull()
    }

    fun findChapterDirs(
        chapters: List<NovelChapter>,
        novel: Novel,
        source: NovelSource,
    ): Pair<UniFile?, List<UniFile>> {
        val novelDir = findNovelDir(novel.title, source) ?: return null to emptyList()
        return novelDir to chapters.mapNotNull { chapter ->
            getValidChapterDirNames(chapter.name, chapter.scanlator).asSequence()
                .mapNotNull { novelDir.findFile(it) }
                .firstOrNull()
        }
    }

    fun getSourceDirName(source: NovelSource): String {
        return DiskUtil.buildValidFilename(source.toString())
    }

    fun getNovelDirName(novelTitle: String): String {
        return DiskUtil.buildValidFilename(novelTitle)
    }

    fun getChapterDirName(chapterName: String, chapterScanlator: String?): String {
        val newChapterName = sanitizeChapterName(chapterName)
        return DiskUtil.buildValidFilename(
            when {
                !chapterScanlator.isNullOrBlank() -> "${chapterScanlator}_$newChapterName"
                else -> newChapterName
            },
        ) + ".txt"
    }

    private fun sanitizeChapterName(chapterName: String): String {
        return chapterName.ifBlank {
            "Chapter"
        }
    }

    fun isChapterDirNameChanged(oldChapter: NovelChapter, newChapter: NovelChapter): Boolean {
        return oldChapter.name != newChapter.name ||
            oldChapter.scanlator?.takeIf { it.isNotBlank() } != newChapter.scanlator?.takeIf { it.isNotBlank() }
    }

    fun getValidChapterDirNames(chapterName: String, chapterScanlator: String?): List<String> {
        val chapterDirName = getChapterDirName(chapterName, chapterScanlator)
        return listOf(chapterDirName)
    }

    // -- chapter audio (plan 05-01) ----------------------------------------------

    /**
     * `.m4a` filename for a rendered chapter — the downloaded chapter's
     * sanitized base name with `.txt` swapped for `.m4a` (D-04).
     */
    fun getAudioFileName(chapterName: String, chapterScanlator: String?): String {
        return getChapterDirName(chapterName, chapterScanlator)
            .removeSuffix(".txt") + AUDIO_FILE_SUFFIX
    }

    /** Per-novel `audio/` subdirectory inside the user-picked downloads tree. */
    fun findAudioDir(novelTitle: String, source: NovelSource): UniFile? {
        return findNovelDir(novelTitle, source)?.findFile(AUDIO_DIR_NAME)
    }

    fun getOrCreateAudioDir(novelTitle: String, source: NovelSource): UniFile? {
        return findNovelDir(novelTitle, source)?.createDirectory(AUDIO_DIR_NAME)
    }

    fun findAudioFile(
        chapterName: String,
        chapterScanlator: String?,
        novelTitle: String,
        source: NovelSource,
    ): UniFile? {
        return findAudioDir(novelTitle, source)
            ?.findFile(getAudioFileName(chapterName, chapterScanlator))
    }

    fun deleteAudioFile(
        chapterName: String,
        chapterScanlator: String?,
        novelTitle: String,
        source: NovelSource,
    ): Boolean {
        return findAudioFile(chapterName, chapterScanlator, novelTitle, source)?.delete() ?: false
    }

    /** Names of rendered `.m4a` files under the novel's `audio/` dir. */
    suspend fun renderedAudioFileNames(novelTitle: String, source: NovelSource): Set<String> {
        return findAudioDir(novelTitle, source)?.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .mapNotNull { it.name }
            .filter { it.endsWith(AUDIO_FILE_SUFFIX) }
            .toSet()
    }

    private companion object {
        const val AUDIO_DIR_NAME = "audio"
        const val AUDIO_FILE_SUFFIX = ".m4a"
    }
}
