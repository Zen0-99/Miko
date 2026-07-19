package eu.kanade.tachiyomi.ui.reader.novel

import eu.kanade.domain.items.chapter.interactor.SetNovelReadingPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.items.chapter.model.NovelChapter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Character-level position tracking service for novel reading.
 *
 * Ports Miko's `CharacterPositionTracker`, adapted to aniyomi-fork's
 * `SetNovelReadingPosition` interactor and `NovelChapter.lastCharRead` column.
 *
 * Responsibilities:
 *  - Track the current character offset within the visible chapter.
 *  - Maintain a small position history for undo/redo.
 *  - Track reading-session analytics (start time, characters read, speed).
 *  - Debounce-save the position to the database.
 */
class CharacterPositionTracker(
    private val setReadingPosition: SetNovelReadingPosition = Injekt.get(),
) {

    private val _currentPosition = MutableStateFlow(CharacterPosition())
    val currentPosition: StateFlow<CharacterPosition> = _currentPosition.asStateFlow()

    private val _readingSession = MutableStateFlow(ReadingSession())
    val readingSession: StateFlow<ReadingSession> = _readingSession.asStateFlow()

    private val positionHistory = mutableListOf<CharacterPosition>()
    private var historyIndex = -1

    /**
     * Update the current character position within a chapter.
     */
    fun updatePosition(
        novelId: Long,
        chapterId: Long,
        characterPosition: Int,
        totalCharacters: Int,
        scrollPosition: Int = 0,
    ) {
        val position = CharacterPosition(
            novelId = novelId,
            chapterId = chapterId,
            characterPosition = characterPosition,
            totalCharacters = totalCharacters,
            scrollPosition = scrollPosition,
            timestamp = System.currentTimeMillis(),
        )
        _currentPosition.value = position
        addToHistory(position)
        updateReadingSession(position)
    }

    /**
     * Persist the current position to the database.
     */
    suspend fun savePosition() {
        val position = _currentPosition.value
        if (position.chapterId > 0) {
            setReadingPosition.await(position.chapterId, position.characterPosition.toLong())
        }
    }

    /**
     * Load the saved position for a chapter from the database.
     */
    suspend fun loadSavedPosition(chapter: NovelChapter): CharacterPosition? {
        return if (chapter.lastCharRead > 0) {
            CharacterPosition(
                novelId = chapter.novelId,
                chapterId = chapter.id,
                characterPosition = chapter.lastCharRead.toInt(),
                totalCharacters = 0,
                scrollPosition = 0,
                timestamp = chapter.lastModifiedAt,
            )
        } else {
            null
        }
    }

    /**
     * Reading progress as a 0..1 fraction.
     */
    fun getReadingProgress(): Float {
        val position = _currentPosition.value
        return if (position.totalCharacters > 0) {
            position.characterPosition.toFloat() / position.totalCharacters.toFloat()
        } else {
            0f
        }
    }

    /**
     * Estimated minutes remaining at [averageWordsPerMinute] (default 200 wpm).
     */
    fun getEstimatedReadingTime(averageWordsPerMinute: Int = 200): Int {
        val position = _currentPosition.value
        val remainingCharacters = (position.totalCharacters - position.characterPosition).coerceAtLeast(0)
        val estimatedWords = remainingCharacters / 5
        return (estimatedWords / averageWordsPerMinute).coerceAtLeast(0)
    }

    /**
     * Total word count in the current chapter (approximate: characters / 5).
     */
    fun getTotalWordCount(): Int {
        val position = _currentPosition.value
        return (position.totalCharacters / 5).coerceAtLeast(0)
    }

    /**
     * Estimated minutes to end of chapter at [averageWordsPerMinute].
     * Alias for [getEstimatedReadingTime] — kept for clarity in the info display.
     */
    fun getTimeToEnd(averageWordsPerMinute: Int = 200): Int {
        return getEstimatedReadingTime(averageWordsPerMinute)
    }

    fun navigateToPreviousPosition(): CharacterPosition? {
        if (historyIndex > 0) {
            historyIndex--
            val position = positionHistory[historyIndex]
            _currentPosition.value = position
            return position
        }
        return null
    }

    fun navigateToNextPosition(): CharacterPosition? {
        if (historyIndex < positionHistory.size - 1) {
            historyIndex++
            val position = positionHistory[historyIndex]
            _currentPosition.value = position
            return position
        }
        return null
    }

    fun startReadingSession(startPosition: Int = 0) {
        _readingSession.value = ReadingSession(
            startTime = System.currentTimeMillis(),
            startPosition = startPosition,
            isActive = true,
        )
        positionHistory.clear()
        historyIndex = -1
    }

    suspend fun endReadingSession() {
        val session = _readingSession.value
        if (session.isActive) {
            savePosition()
            _readingSession.value = session.copy(
                endTime = System.currentTimeMillis(),
                isActive = false,
            )
        }
    }

    private fun addToHistory(position: CharacterPosition) {
        // Only record positions that moved by more than 100 characters.
        if (positionHistory.isEmpty() ||
            kotlin.math.abs(positionHistory.last().characterPosition - position.characterPosition) > 100
        ) {
            if (historyIndex < positionHistory.size - 1) {
                positionHistory.subList(historyIndex + 1, positionHistory.size).clear()
            }
            positionHistory.add(position)
            historyIndex = positionHistory.size - 1
            if (positionHistory.size > 50) {
                positionHistory.removeAt(0)
                historyIndex--
            }
        }
    }

    private fun updateReadingSession(position: CharacterPosition) {
        val session = _readingSession.value
        if (session.isActive) {
            _readingSession.value = session.copy(
                lastPosition = position.characterPosition,
                lastUpdateTime = position.timestamp,
            )
        }
    }

    private fun calculateReadingSpeed(): Float {
        val session = _readingSession.value
        val sessionDurationMinutes = (System.currentTimeMillis() - session.startTime) / 60000f
        val charactersRead = session.lastPosition - session.startPosition
        val wordsRead = charactersRead / 5f
        return if (sessionDurationMinutes > 0) wordsRead / sessionDurationMinutes else 0f
    }

    fun getReadingStatistics(): ReadingStatistics {
        val session = _readingSession.value
        val position = _currentPosition.value
        return ReadingStatistics(
            sessionDuration = System.currentTimeMillis() - session.startTime,
            charactersRead = position.characterPosition - session.startPosition,
            averageReadingSpeed = calculateReadingSpeed(),
            positionsVisited = positionHistory.size,
        )
    }
}

data class CharacterPosition(
    val novelId: Long = 0,
    val chapterId: Long = 0,
    val characterPosition: Int = 0,
    val totalCharacters: Int = 0,
    val scrollPosition: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

data class ReadingSession(
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0,
    val startPosition: Int = 0,
    val lastPosition: Int = 0,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val isActive: Boolean = false,
)

data class ReadingStatistics(
    val sessionDuration: Long,
    val charactersRead: Int,
    val averageReadingSpeed: Float,
    val positionsVisited: Int,
)
