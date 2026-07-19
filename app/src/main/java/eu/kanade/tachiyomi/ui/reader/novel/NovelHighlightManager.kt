package eu.kanade.tachiyomi.ui.reader.novel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NovelHighlightManager(context: Context) {

    private val appContext = context.applicationContext
    private val highlightsDir = File(appContext.getExternalFilesDir(null), "highlights")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    companion object {
        const val COLOR_YELLOW = "#FFFF8D"
        const val COLOR_GREEN = "#B9F6CA"
        const val COLOR_BLUE = "#B3E5FC"
        const val COLOR_PINK = "#F8BBD0"
        const val COLOR_PURPLE = "#E1BEE7"

        val DEFAULT_COLORS = listOf(COLOR_YELLOW, COLOR_GREEN, COLOR_BLUE, COLOR_PINK, COLOR_PURPLE)
    }

    init {
        highlightsDir.mkdirs()
    }

    suspend fun saveHighlight(
        novelKey: NovelKey,
        chapterNumber: Double,
        chapterTitle: String,
        selectedText: String,
        paragraphIndex: Int = 0,
        color: String = COLOR_YELLOW,
        note: String? = null,
    ) {
        withContext(Dispatchers.IO) {
            val data = loadData(novelKey)
            val existingChapter = data.chapters.find { it.chapterNumber == chapterNumber }
            val existingHighlights = existingChapter?.highlights ?: emptyList()

            val (mergedHighlights, _) = mergeIfOverlapping(
                existingHighlights,
                selectedText,
                color,
                note,
            )

            val chapterHighlights = if (existingChapter != null) {
                existingChapter.copy(highlights = mergedHighlights)
            } else {
                ChapterHighlights(
                    chapterNumber = chapterNumber,
                    chapterTitle = chapterTitle,
                    highlights = mergedHighlights,
                )
            }

            val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber } + chapterHighlights
            val updatedData = data.copy(
                novelTitle = novelKey.title,
                chapters = updatedChapters,
            )
            writeJson(novelKey, updatedData)
            exportToMd(novelKey, updatedData)
        }
    }

    suspend fun deleteHighlight(
        novelKey: NovelKey,
        chapterNumber: Double,
        highlightText: String,
        timestamp: Long,
    ) {
        withContext(Dispatchers.IO) {
            val data = loadData(novelKey)
            val chapter = data.chapters.find { it.chapterNumber == chapterNumber } ?: return@withContext
            val updatedHighlights = chapter.highlights.filterNot {
                it.text == highlightText && it.timestamp == timestamp
            }
            if (updatedHighlights.isEmpty()) {
                val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber }
                val updatedData = data.copy(chapters = updatedChapters)
                writeJson(novelKey, updatedData)
                exportToMd(novelKey, updatedData)
            } else {
                val updatedChapter = chapter.copy(highlights = updatedHighlights)
                val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber } + updatedChapter
                val updatedData = data.copy(chapters = updatedChapters)
                writeJson(novelKey, updatedData)
                exportToMd(novelKey, updatedData)
            }
        }
    }

    suspend fun updateHighlightNote(
        novelKey: NovelKey,
        chapterNumber: Double,
        highlightText: String,
        timestamp: Long,
        note: String?,
    ) {
        withContext(Dispatchers.IO) {
            val data = loadData(novelKey)
            val chapter = data.chapters.find { it.chapterNumber == chapterNumber } ?: return@withContext
            val updatedHighlights = chapter.highlights.map {
                if (it.text == highlightText && it.timestamp == timestamp) {
                    it.copy(note = note)
                } else it
            }
            val updatedChapter = chapter.copy(highlights = updatedHighlights)
            val updatedChapters = data.chapters.filter { it.chapterNumber != chapterNumber } + updatedChapter
            val updatedData = data.copy(chapters = updatedChapters)
            writeJson(novelKey, updatedData)
            exportToMd(novelKey, updatedData)
        }
    }

    suspend fun deleteAllHighlights(novelKey: NovelKey) {
        withContext(Dispatchers.IO) {
            val titleFile = File(highlightsDir, "${novelKey.titleFileName()}.json")
            val mdFile = File(highlightsDir, "${novelKey.titleFileName()}.md")
            titleFile.delete()
            mdFile.delete()
        }
    }

    fun getChapterHighlights(novelKey: NovelKey, chapterNumber: Double): List<HighlightEntry> {
        return loadData(novelKey).chapters.find { it.chapterNumber == chapterNumber }?.highlights ?: emptyList()
    }

    fun getAllHighlights(novelKey: NovelKey): NovelHighlightsData {
        return loadData(novelKey)
    }

    fun hasHighlights(novelKey: NovelKey): Boolean {
        return loadData(novelKey).chapters.any { it.highlights.isNotEmpty() }
    }

    fun getAllNovelsWithHighlights(): List<NovelHighlightsData> {
        if (!highlightsDir.exists() || !highlightsDir.isDirectory) return emptyList()
        val files = highlightsDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                val data = json.decodeFromString(NovelHighlightsData.serializer(), file.readText())
                if (data.chapters.any { it.highlights.isNotEmpty() }) data else null
            } catch (_: Exception) { null }
        }.sortedByDescending { novel ->
            novel.chapters.flatMap { it.highlights }.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    fun getTotalHighlightCount(novelKey: NovelKey): Int {
        return loadData(novelKey).chapters.sumOf { it.highlights.size }
    }

    private fun mergeIfOverlapping(
        existing: List<HighlightEntry>,
        newText: String,
        newColor: String,
        newNote: String?,
    ): Pair<List<HighlightEntry>, Boolean> {
        // Only merge if the new text actually contains or is contained by
        // an existing highlight (physical text overlap, not just shared words).
        // This ensures separate highlights remain separate unless one truly
        // overlaps the other.
        val overlapping = existing.find { existingHl ->
            existingHl.text.contains(newText) || newText.contains(existingHl.text)
        }

        return if (overlapping != null) {
            // Keep the longer text (the broader selection consumes the narrower).
            val mergedText = if (overlapping.text.length >= newText.length) overlapping.text else newText
            val merged = overlapping.copy(
                text = mergedText,
                color = newColor,
                note = newNote ?: overlapping.note,
                timestamp = System.currentTimeMillis(),
            )
            existing.filterNot { it === overlapping } + merged to true
        } else {
            existing + HighlightEntry(
                text = newText,
                color = newColor,
                note = newNote,
                timestamp = System.currentTimeMillis(),
            ) to false
        }
    }

    private fun loadData(novelKey: NovelKey): NovelHighlightsData {
        val titleOnlyFile = File(highlightsDir, "${novelKey.titleFileName()}.json")

        if (!titleOnlyFile.exists()) return NovelHighlightsData(
            novelTitle = novelKey.title,
            chapters = emptyList(),
        )
        return try {
            json.decodeFromString(NovelHighlightsData.serializer(), titleOnlyFile.readText())
        } catch (e: Exception) {
            NovelHighlightsData(
                novelTitle = novelKey.title,
                chapters = emptyList(),
            )
        }
    }

    private fun writeJson(novelKey: NovelKey, data: NovelHighlightsData) {
        val file = File(highlightsDir, "${novelKey.titleFileName()}.json")
        file.writeText(json.encodeToString(data))
    }

    private fun exportToMd(novelKey: NovelKey, data: NovelHighlightsData) {
        val file = File(highlightsDir, "${novelKey.titleFileName()}.md")
        val sb = StringBuilder()
        sb.appendLine("# ${data.novelTitle}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        val sortedChapters = data.chapters.sortedBy { it.chapterNumber }
        for (ch in sortedChapters) {
            if (ch.highlights.isEmpty()) continue
            sb.appendLine("### ${ch.chapterTitle.ifBlank { "Chapter ${ch.chapterNumber}" }}")
            for (hl in ch.highlights) {
                val colorBar = hl.color?.let { "![color]($it) " } ?: ""
                sb.appendLine("> $colorBar\"${hl.text}\" — ${dateFormat.format(Date(hl.timestamp))}")
                if (!hl.note.isNullOrBlank()) {
                    sb.appendLine("> *Note: ${hl.note}*")
                }
                sb.appendLine()
            }
        }
        file.writeText(sb.toString())
    }

    @Serializable
    data class NovelKey(
        val title: String,
        val novelId: Long? = null,
    ) {
        fun titleFileName(): String {
            return title.trim().replace(Regex("[^a-zA-Z0-9\\s-]"), "").replace(Regex("\\s+"), "_")
        }
    }

    @Serializable
    data class NovelHighlightsData(
        val novelTitle: String,
        val novelId: Long? = null,
        val chapters: List<ChapterHighlights> = emptyList(),
    )

    @Serializable
    data class ChapterHighlights(
        val chapterNumber: Double,
        val chapterTitle: String = "",
        val highlights: List<HighlightEntry> = emptyList(),
    )

    @Serializable
    data class HighlightEntry(
        val text: String,
        val paragraphIndex: Int = 0,
        val timestamp: Long,
        val color: String? = COLOR_YELLOW,
        val note: String? = null,
    )
}
