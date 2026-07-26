package eu.kanade.tachiyomi.ui.reader.novel

/**
 * Packs character position, RecyclerView item index, and pixel offset into a
 * single Long for storage in the `last_char_read` database column.
 *
 * This allows exact scroll restoration (no visual jump, no drift) by saving
 * the LinearLayoutManager's first visible item position and pixel offset,
 * alongside the character position for progress-percentage display.
 *
 * Encoding (decimal, marker-based for backward compatibility):
 * - Values below [SCROLL_MARKER] are treated as legacy character positions.
 * - Values >= [SCROLL_MARKER] are decoded as:
 *     payload = value - SCROLL_MARKER
 *     charPos     = payload / CHAR_BASE       (up to 999,999)
 *     itemIndex   = (payload % CHAR_BASE) / OFFSET_BASE  (up to 9,999)
 *     pixelOffset = payload % OFFSET_BASE     (up to 9,999)
 */
object NovelScrollPositionCodec {

    private const val SCROLL_MARKER = 100_000_000_000L  // 100 billion
    private const val OFFSET_BASE = 10_000L             // pixel offset + item index range
    private const val CHAR_BASE = 100_000_000L          // 10_000 * 10_000

    /**
     * Encode character position, item index, and pixel offset into a single Long.
     */
    fun encode(charPosition: Int, itemIndex: Int, pixelOffset: Int): Long {
        val safeChar = charPosition.coerceIn(0, 999_999).toLong()
        val safeIndex = itemIndex.coerceIn(0, 9_999).toLong()
        val safeOffset = pixelOffset.coerceIn(0, 9_999).toLong()
        return SCROLL_MARKER + safeChar * CHAR_BASE + safeIndex * OFFSET_BASE + safeOffset
    }

    /**
     * Decode a Long into character position, item index, and pixel offset.
     * Returns null for legacy values (plain character positions < SCROLL_MARKER).
     */
    fun decode(value: Long): DecodedPosition? {
        if (value < SCROLL_MARKER) return null
        val payload = value - SCROLL_MARKER
        val charPos = (payload / CHAR_BASE).toInt().coerceAtLeast(0)
        val remainder = payload % CHAR_BASE
        val itemIndex = (remainder / OFFSET_BASE).toInt().coerceAtLeast(0)
        val pixelOffset = (remainder % OFFSET_BASE).toInt().coerceAtLeast(0)
        return DecodedPosition(
            characterPosition = charPos,
            itemIndex = itemIndex,
            pixelOffset = pixelOffset,
        )
    }

    /**
     * Extract just the character position from a packed or legacy value.
     * Used by chapter-list progress calculations that only need the % read.
     */
    fun decodeCharacterPosition(value: Long): Long {
        val decoded = decode(value)
        return if (decoded != null) {
            decoded.characterPosition.toLong()
        } else {
            value  // Legacy: the value IS the character position
        }
    }

    /**
     * Check whether a value represents a "started" chapter (position > 0).
     * Works for both legacy and packed formats.
     */
    fun isStarted(value: Long): Boolean {
        val decoded = decode(value)
        return if (decoded != null) {
            decoded.characterPosition > 0 || decoded.itemIndex > 0
        } else {
            value > 0
        }
    }

    data class DecodedPosition(
        val characterPosition: Int,
        val itemIndex: Int,
        val pixelOffset: Int,
    )
}
