package eu.kanade.tachiyomi.extension.novel.js

import kotlinx.serialization.Serializable

/**
 * Metadata for a JS novel plugin (LNReader-compatible format).
 *
 * JS plugins are JavaScript files loaded from plugin repositories.
 * They differ from APK extensions in that they're interpreted at runtime
 * by [NovelJsRuntime] rather than compiled into Android APKs.
 *
 * Source IDs for JS plugins are generated via [NovelPluginId.toSourceId].
 */
sealed class NovelPlugin {
    abstract val id: String
    abstract val name: String
    abstract val site: String
    abstract val lang: String
    abstract val versionCode: Int
    abstract val versionName: String
    abstract val url: String
    abstract val iconUrl: String?
    abstract val customJs: String?
    abstract val customCss: String?
    abstract val hasSettings: Boolean
    abstract val sha256: String
    abstract val repoUrl: String
    abstract val isNsfw: Boolean

    data class Available(
        override val id: String,
        override val name: String,
        override val site: String,
        override val lang: String,
        override val versionCode: Int,
        override val versionName: String,
        override val url: String,
        override val iconUrl: String?,
        override val customJs: String?,
        override val customCss: String?,
        override val hasSettings: Boolean,
        override val sha256: String,
        override val repoUrl: String,
        override val isNsfw: Boolean = false,
    ) : NovelPlugin()

    data class Installed(
        override val id: String,
        override val name: String,
        override val site: String,
        override val lang: String,
        override val versionCode: Int,
        override val versionName: String,
        override val url: String,
        override val iconUrl: String?,
        override val customJs: String?,
        override val customCss: String?,
        override val hasSettings: Boolean,
        override val sha256: String,
        override val repoUrl: String,
        override val isNsfw: Boolean = false,
        /** Path to the installed plugin JS file on disk. */
        val filePath: String,
        /** Path to the installed icon file on disk, if downloaded. */
        val iconPath: String? = null,
    ) : NovelPlugin()
}

/**
 * Entry in a plugin repository's index JSON.
 * Compatible with LNReader plugin repo format.
 */
@Serializable
data class NovelPluginRepoEntry(
    val id: String,
    val name: String,
    val site: String,
    val lang: String,
    val version: Int,
    val url: String,
    val iconUrl: String? = null,
    val customJsUrl: String? = null,
    val customCssUrl: String? = null,
    val hasSettings: Boolean = false,
    val sha256: String = "",
    val nsfw: Boolean = false,
)
