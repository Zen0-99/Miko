package miko.ui.player

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import kotlin.math.abs

/**
 * Android's `IndexedSegment` — a seekbar/playhead segment marker. `index`
 * carries Android's provenance markers through the merge: -2 = external
 * (AniSkip/extension timestamp), -1 = inserted empty gap chapter, >= 0 =
 * the player's own chapter row.
 */
data class SkipSegment(
    val name: String,
    val startSec: Float,
    val index: Int = 0,
    val type: ChapterType = ChapterType.Other,
)

/**
 * Android's `ChapterUtils.mergeChapters` — folds new [stamps] into the
 * existing segment list, preferring external (-2) markers at ~1s
 * collisions, inserting empty gap segments so the "current chapter" query
 * can always resolve, and leading with an empty 0s segment when nothing
 * covers the head.
 */
fun mergeSkipSegments(
    current: List<SkipSegment>,
    stamps: List<TimeStamp>,
    durationSec: Double? = null,
): List<SkipSegment> {
    val sortedStamps = stamps.sortedBy { it.start }
    val chapters = sortedStamps.mapIndexed { i, it ->
        val startTime = if (i == 0 && it.start < 1.0) 0.0 else it.start
        val startChapter = SkipSegment(
            index = -2,
            name = it.name,
            startSec = startTime.toFloat(),
            type = it.type,
        )
        val nextStart = sortedStamps.getOrNull(i + 1)?.start
        val isNotLastChapter = abs(it.end - (durationSec ?: -2.0)) > 1.0
        val isNotAdjacent = nextStart == null || (abs(it.end - nextStart) > 1.0)
        if (isNotLastChapter && isNotAdjacent) {
            val endChapter = SkipSegment(
                index = -1,
                name = "",
                startSec = it.end.toFloat(),
            )
            return@mapIndexed listOf(startChapter, endChapter)
        } else {
            listOf(startChapter)
        }
    }.flatten()
    val playerChapters = current.filter { playerChapter ->
        chapters.none { chapter ->
            abs(chapter.startSec - playerChapter.startSec) < 1.0 && chapter.index == -2
        }
    }.map {
        SkipSegment(it.name, it.startSec, index = it.index, type = it.type)
    }.sortedBy { it.startSec }.mapIndexed { i, it ->
        if (i == 0 && it.startSec < 1.0) {
            SkipSegment(it.name, 0.0f, index = it.index, type = it.type)
        } else {
            it
        }
    }
    val filteredChapters = chapters.filter { chapter ->
        playerChapters.none { playerChapter ->
            abs(chapter.startSec - playerChapter.startSec) < 1.0 && chapter.index != -2
        }
    }
    val startChapter = if ((playerChapters + filteredChapters).isNotEmpty() &&
        playerChapters.none { it.startSec == 0.0f } &&
        filteredChapters.none { it.startSec == 0.0f }
    ) {
        listOf(
            SkipSegment(
                index = -1,
                name = "",
                startSec = 0.0f,
            ),
        )
    } else {
        emptyList()
    }

    val combined = (startChapter + playerChapters + filteredChapters).sortedBy { it.startSec }

    // Remove any adjacent "empty" chapters
    return combined.filterIndexed { index, segment ->
        if (index == 0) {
            true
        } else {
            val previousSegment = combined[index - 1]
            !(segment.name.isEmpty() && previousSegment.name.isEmpty())
        }
    }
}
