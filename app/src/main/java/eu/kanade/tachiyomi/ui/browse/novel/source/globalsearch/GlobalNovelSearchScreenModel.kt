package eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch

import android.util.Log
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource

class GlobalNovelSearchScreenModel(
    initialQuery: String = "",
    initialExtensionFilter: String? = null,
) : NovelSearchScreenModel(
    State(
        searchQuery = initialQuery,
    ),
) {

    init {
        Log.d("NovelSearch", "[GlobalNovelSearchScreenModel] init - initialQuery='$initialQuery', initialExtensionFilter='$initialExtensionFilter'")
        extensionFilter = initialExtensionFilter
        if (initialQuery.isNotBlank() || !initialExtensionFilter.isNullOrBlank()) {
            if (extensionFilter != null) {
                Log.d("NovelSearch", "[GlobalNovelSearchScreenModel] init - extensionFilter set, calling setSourceFilter(All)")
                setSourceFilter(NovelSourceFilter.All)
            }
            Log.d("NovelSearch", "[GlobalNovelSearchScreenModel] init - triggering initial search()")
            search()
        } else {
            Log.d("NovelSearch", "[GlobalNovelSearchScreenModel] init - no initial query/filter, waiting for user input")
        }
    }

    override fun getEnabledSources(): List<NovelCatalogueSource> {
        val result = super.getEnabledSources()
            .filter { state.value.sourceFilter != NovelSourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
        Log.d("NovelSearch", "[GlobalNovelSearchScreenModel] getEnabledSources - after PinnedOnly filter: ${result.size} sources, sourceFilter=${state.value.sourceFilter}")
        return result
    }
}
