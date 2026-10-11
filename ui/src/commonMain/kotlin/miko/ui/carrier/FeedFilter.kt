package miko.ui.carrier

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.source.model.Filter

/**
 * Uniform view over the three carrier filter hierarchies (NovelFilter /
 * Filter / AnimeFilter) for the source feed's filter sheet. All accessors
 * read and write the underlying filter's `state`, so edits persist on the
 * same objects the source bridges serialize into plugin calls.
 */
class FeedFilter private constructor(
    val name: String,
    val kind: Kind,
    private val delegate: Any,
    val children: List<FeedFilter> = emptyList(),
) {
    enum class Kind { Header, Separator, Text, CheckBox, TriState, Select, Sort, Group }

    var text: String
        get() = when (val d = delegate) {
            is NovelFilter.Text -> d.state
            is Filter.Text -> d.state
            is AnimeFilter.Text -> d.state
            else -> ""
        }
        set(value) {
            when (val d = delegate) {
                is NovelFilter.Text -> d.state = value
                is Filter.Text -> d.state = value
                is AnimeFilter.Text -> d.state = value
            }
        }

    var checked: Boolean
        get() = when (val d = delegate) {
            is NovelFilter.Switch -> d.state
            is NovelFilter.CheckBox -> d.state
            is Filter.CheckBox -> d.state
            is AnimeFilter.CheckBox -> d.state
            else -> false
        }
        set(value) {
            when (val d = delegate) {
                is NovelFilter.Switch -> d.state = value
                is NovelFilter.CheckBox -> d.state = value
                is Filter.CheckBox -> d.state = value
                is AnimeFilter.CheckBox -> d.state = value
            }
        }

    var triState: Int
        get() = when (val d = delegate) {
            is NovelFilter.TriState -> d.state
            is Filter.TriState -> d.state
            is AnimeFilter.TriState -> d.state
            else -> 0
        }
        set(value) {
            when (val d = delegate) {
                is NovelFilter.TriState -> d.state = value
                is Filter.TriState -> d.state = value
                is AnimeFilter.TriState -> d.state = value
            }
        }

    /** Selected index for Select/Picker rows; -1 renders as unset. */
    var selectedIndex: Int
        get() = when (val d = delegate) {
            is NovelFilter.Select<*> -> d.state
            is Filter.Select<*> -> d.state
            is AnimeFilter.Select<*> -> d.state
            else -> -1
        }
        set(value) {
            when (val d = delegate) {
                is NovelFilter.Select<*> -> d.state = value
                is Filter.Select<*> -> d.state = value
                is AnimeFilter.Select<*> -> d.state = value
            }
        }

    /** Option labels for Select/Picker and Sort rows. */
    val options: List<String>
        get() = when (val d = delegate) {
            is NovelFilter.Select<*> -> d.values.map { it.toString() }
            is NovelFilter.Sort -> d.values.toList()
            is Filter.Select<*> -> d.values.map { it.toString() }
            is Filter.Sort -> d.values.toList()
            is AnimeFilter.Select<*> -> d.values.map { it.toString() }
            is AnimeFilter.Sort -> d.values.toList()
            else -> emptyList()
        }

    /** Sort rows: selected index, or -1 when nothing is picked. */
    val sortIndex: Int
        get() = when (val d = delegate) {
            is NovelFilter.Sort -> d.state?.index ?: -1
            is Filter.Sort -> d.state?.index ?: -1
            is AnimeFilter.Sort -> d.state?.index ?: -1
            else -> -1
        }

    val sortAscending: Boolean
        get() = when (val d = delegate) {
            is NovelFilter.Sort -> d.state?.ascending ?: true
            is Filter.Sort -> d.state?.ascending ?: true
            is AnimeFilter.Sort -> d.state?.ascending ?: true
            else -> true
        }

    fun setSort(index: Int, ascending: Boolean) {
        when (val d = delegate) {
            is NovelFilter.Sort -> d.state = NovelFilter.Sort.Selection(index, ascending)
            is Filter.Sort -> d.state = Filter.Sort.Selection(index, ascending)
            is AnimeFilter.Sort -> d.state = AnimeFilter.Sort.Selection(index, ascending)
        }
    }

    companion object {
        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2

        fun wrap(filter: Any?): FeedFilter? = when (filter) {
            null -> null
            // Order matters: Picker ⊂ Select, XCheckBox ⊂ TriState.
            is NovelFilter.Header -> FeedFilter(filter.name, Kind.Header, filter)
            is NovelFilter.Separator -> FeedFilter(filter.name, Kind.Separator, filter)
            is NovelFilter.Sort -> FeedFilter(filter.name, Kind.Sort, filter)
            is NovelFilter.Group<*> -> FeedFilter(
                filter.name,
                Kind.Group,
                filter,
                filter.state.mapNotNull(::wrap),
            )
            is NovelFilter.Select<*> -> FeedFilter(filter.name, Kind.Select, filter)
            is NovelFilter.Text -> FeedFilter(filter.name, Kind.Text, filter)
            is NovelFilter.Switch -> FeedFilter(filter.name, Kind.CheckBox, filter)
            is NovelFilter.CheckBox -> FeedFilter(filter.name, Kind.CheckBox, filter)
            is NovelFilter.TriState -> FeedFilter(filter.name, Kind.TriState, filter)
            is Filter.Header -> FeedFilter(filter.name, Kind.Header, filter)
            is Filter.Separator -> FeedFilter(filter.name, Kind.Separator, filter)
            is Filter.Sort -> FeedFilter(filter.name, Kind.Sort, filter)
            is Filter.Group<*> -> FeedFilter(
                filter.name,
                Kind.Group,
                filter,
                filter.state.mapNotNull(::wrap),
            )
            is Filter.Select<*> -> FeedFilter(filter.name, Kind.Select, filter)
            is Filter.Text -> FeedFilter(filter.name, Kind.Text, filter)
            is Filter.CheckBox -> FeedFilter(filter.name, Kind.CheckBox, filter)
            is Filter.TriState -> FeedFilter(filter.name, Kind.TriState, filter)
            is AnimeFilter.Header -> FeedFilter(filter.name, Kind.Header, filter)
            is AnimeFilter.Separator -> FeedFilter(filter.name, Kind.Separator, filter)
            is AnimeFilter.Sort -> FeedFilter(filter.name, Kind.Sort, filter)
            is AnimeFilter.Group<*> -> FeedFilter(
                filter.name,
                Kind.Group,
                filter,
                filter.state.mapNotNull(::wrap),
            )
            is AnimeFilter.Select<*> -> FeedFilter(filter.name, Kind.Select, filter)
            is AnimeFilter.Text -> FeedFilter(filter.name, Kind.Text, filter)
            is AnimeFilter.CheckBox -> FeedFilter(filter.name, Kind.CheckBox, filter)
            is AnimeFilter.TriState -> FeedFilter(filter.name, Kind.TriState, filter)
            else -> null
        }

        fun wrapAll(filters: List<Any?>): List<FeedFilter> = filters.mapNotNull(::wrap)
    }
}
