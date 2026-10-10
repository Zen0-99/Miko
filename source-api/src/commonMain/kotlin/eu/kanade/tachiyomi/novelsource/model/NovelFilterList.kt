package eu.kanade.tachiyomi.novelsource.model

import eu.kanade.tachiyomi.util.Stable

@Stable
data class NovelFilterList(val list: List<NovelFilter<*>>) : List<NovelFilter<*>> by list {

    constructor(vararg fs: NovelFilter<*>) : this(if (fs.isNotEmpty()) fs.asList() else emptyList())

    override fun equals(other: Any?): Boolean {
        return false
    }

    override fun hashCode(): Int {
        return list.hashCode()
    }
}
