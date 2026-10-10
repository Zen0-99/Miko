package tachiyomi.data.extension.novel

import platform.Foundation.NSUserDefaults

/**
 * NSUserDefaults-backed plugin key-value store. Entries are stored under
 * `novel_plugin_storage_<pluginId>.<key>` so per-plugin enumeration and
 * clearing only need key-prefix filtering.
 */
class IosNovelPluginKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NovelPluginKeyValueStore {

    override fun get(pluginId: String, key: String): String? {
        return defaults.objectForKey(entryKey(pluginId, key)) as? String
    }

    override fun set(pluginId: String, key: String, value: String) {
        defaults.setObject(value, forKey = entryKey(pluginId, key))
    }

    override fun remove(pluginId: String, key: String) {
        defaults.removeObjectForKey(entryKey(pluginId, key))
    }

    override fun clear(pluginId: String) {
        val prefix = "${storePrefix(pluginId)}."
        allKeys()
            .filter { it.startsWith(prefix) }
            .forEach { defaults.removeObjectForKey(it) }
    }

    override fun clearAll() {
        allKeys()
            .filter { it.startsWith(PREFS_PREFIX) }
            .forEach { defaults.removeObjectForKey(it) }
    }

    override fun keys(pluginId: String): Set<String> {
        val prefix = "${storePrefix(pluginId)}."
        return allKeys()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()
    }

    private fun allKeys(): List<String> {
        return defaults.dictionaryRepresentation().keys.filterIsInstance<String>()
    }

    private fun storePrefix(pluginId: String): String = "$PREFS_PREFIX$pluginId"

    private fun entryKey(pluginId: String, key: String): String = "${storePrefix(pluginId)}.$key"

    private companion object {
        private const val PREFS_PREFIX = "novel_plugin_storage_"
    }
}
