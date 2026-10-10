package tachiyomi.data.extension.novel

interface NovelPluginKeyValueStore {
    fun get(pluginId: String, key: String): String?
    fun set(pluginId: String, key: String, value: String)
    fun remove(pluginId: String, key: String)
    fun clear(pluginId: String)
    fun clearAll()
    fun keys(pluginId: String): Set<String>
}
