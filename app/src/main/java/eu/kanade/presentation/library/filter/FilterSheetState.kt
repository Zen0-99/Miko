package eu.kanade.presentation.library.filter

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR

/**
 * Visibility states for the persistent filter sheet.
 */
enum class FilterSheetVisibility {
    HIDDEN,
    COLLAPSED,
    EXPANDED,
}

/**
 * Identifies a library filter type for chip ordering.
 * Each char maps to one position in the filter order preference string.
 */
enum class LibraryFilterId(val char: Char) {
    DOWNLOADED('d'),
    UNREAD('u'),
    STARTED('s'),
    BOOKMARKED('b'),
    COMPLETED('c'),
    TRACKED('t'),
    ;

    companion object {
        val DEFAULT_ORDER = entries.joinToString("") { it.char.toString() }

        fun fromChar(c: Char): LibraryFilterId? = entries.find { it.char == c }
    }
}

/**
 * Data for a single filter chip in the persistent filter sheet.
 */
data class FilterChipData(
    val id: LibraryFilterId,
    val labelRes: StringResource,
    val state: TriState,
    val enabled: Boolean = true,
    val onToggle: () -> Unit,
)

/**
 * Data for a filter section in the full filter sheet.
 * Each section has a title and 3 radio-style options (All / positive / negative).
 * May contain multiple tracker filters under one section.
 */
data class FilterSectionData(
    val id: LibraryFilterId,
    val titleRes: StringResource,
    val items: List<FilterOptionData>,
)

/**
 * A single radio-style filter option in the full filter sheet.
 * Matches old repo's FilterOption design: All / Yes / No.
 */
data class FilterOptionData(
    val label: String,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)
