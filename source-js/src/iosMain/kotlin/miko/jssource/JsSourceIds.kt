package miko.jssource

/**
 * Source IDs for non-novel JS plugins (manga/anime kind). Delegates to
 * [NovelPluginId.toSourceId] — the SHA-256-first-8-bytes scheme is
 * carrier-agnostic; cross-carrier uniqueness comes from `novel_plugins.id`
 * being globally unique per plugin row.
 */
object JsSourceIds {
    fun toSourceId(pluginId: String): Long = NovelPluginId.toSourceId(pluginId)
}
