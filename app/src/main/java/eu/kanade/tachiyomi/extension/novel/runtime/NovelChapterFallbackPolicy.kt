package eu.kanade.tachiyomi.extension.novel.runtime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls how [NovelPluginResultNormalizer] handles missing or duplicate
 * chapter data returned by JS plugins.
 *
 * These policies are typically loaded from per-plugin runtime overrides
 * so that problematic plugins can be patched without modifying the
 * plugin script itself.
 */
@Serializable
data class NovelChapterFallbackPolicy(
    val fillMissingChapterNames: Boolean = true,
    val dropDuplicateChapterPaths: Boolean = true,
    val chapterNamePrefix: String = "Chapter",
    @SerialName("stripFragmentFromChapterPath")
    val stripFragmentFromChapterPath: Boolean = true,
)
