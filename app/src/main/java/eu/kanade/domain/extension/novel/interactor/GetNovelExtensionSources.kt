package eu.kanade.domain.extension.novel.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.novel.model.NovelExtension
import eu.kanade.tachiyomi.novelsource.NovelSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetNovelExtensionSources(
    private val preferences: SourcePreferences,
) {

    fun subscribe(extension: NovelExtension.Installed): Flow<List<NovelExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledNovelSources().changes().map { disabledSources ->
            fun NovelSource.isEnabled() = id.toString() !in disabledSources

            extension.sources
                .map { source ->
                    NovelExtensionSourceItem(
                        source = source,
                        enabled = source.isEnabled(),
                        labelAsName = isMultiSource && !isMultiLangSingleSource,
                    )
                }
        }
    }
}

data class NovelExtensionSourceItem(
    val source: NovelSource,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
